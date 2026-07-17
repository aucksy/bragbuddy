package com.bragbuddy.app.data.rollup

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.INBOX_PLACEMENT
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT

/**
 * Pure, deterministic aggregation of the running rollup — the app-side half of the two-prompt design
 * (BragBuddy-System-Prompt A3 / PART B). Takes the bounded [RollupItem] set, keeps only the requested
 * period window, and produces:
 *  - per goal area: a ranked, capped **highlight shortlist** + **routine tallies** (type → count with
 *    cumulative metrics) + cumulative metrics,
 *  - per behaviour: concrete **evidence** bullets drawn from notable work,
 * then [serialize]s that into the `{{ROLLUP}}` block the model reads. No AI call, no log re-scan.
 *
 * Everything here is a pure function of already-loaded data, so it is unit-tested and the summary
 * cost stays bounded no matter how big the record grows.
 */
/**
 * Project one filed entry into a [RollupItem], or null if it isn't a summary-worthy placement
 * (not PROCESSED, no bullet, or still sitting in the Inbox placeholder). Deterministic — the single
 * point that decides what a "filed contribution" is, so incremental sync and the launch reconcile
 * agree exactly.
 */
fun EntryEntity.toRollupItem(): RollupItem? {
    if (status != EntryStatus.PROCESSED) return null
    val text = bullet?.trim().orEmpty()
    if (text.isEmpty()) return null
    val area = goalCategory?.trim().orEmpty()
    if (area.isEmpty() || area.equals(INBOX_PLACEMENT, ignoreCase = true)) return null
    val proj = project?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals(OUTSIDE_PROJECT, true) && !it.equals(INBOX_PLACEMENT, true) }
    return RollupItem(
        id = id,
        timeMillis = occurredAt ?: createdAt,
        goalArea = area,
        project = proj,
        // Dropped when the project is absent (v0.34.0): a deliverable is unique by (name, project,
        // goalArea), so a tag with no project names nothing this side could ever group or look up.
        deliverable = proj?.let { deliverable?.trim()?.takeIf { d -> d.isNotBlank() } },
        bullet = text,
        metric = metric?.takeIf { it.isNotBlank() },
        impact = impact ?: 0.0,
        routine = routine,
        routineType = routineType?.takeIf { it.isNotBlank() },
        isExtra = isExtra,
        demonstrates = demonstrates.filter { it.isNotBlank() },
    )
}

object RollupAggregator {

    /** Highlight candidates kept per goal area (bounds the model input; the summary picks ~top 5). */
    const val HIGHLIGHT_CAP = 15

    /** Evidence bullets kept per behaviour. */
    const val EVIDENCE_CAP = 6

    /** Cumulative metrics kept per goal area. */
    const val METRIC_CAP = 12

    /**
     * Deliverables listed per goal area (v0.34.0). Like every other cap here, this exists because the
     * rollup's whole contract is to stay BOUNDED however big the record grows — deliverables are
     * user-created and scale with the record, so an uncapped index would put ~80 lines of
     * `- "name" … — N entries` into every metered summary call and crowd out the highlights the model is
     * meant to write from. Ranked by size first, so what's dropped is the smallest threads.
     */
    const val DELIVERABLE_CAP = 12

    /**
     * Aggregate the rollup for a period window `[startMillis, endMillisExclusive)`. Goal areas are
     * ordered by the framework first (so the summary reads in the user's structure), then any extra
     * goal-area names present in the data (never drop a filed contribution). Behaviours follow the
     * framework's behaviour pillars.
     */
    fun aggregate(
        items: List<RollupItem>,
        startMillis: Long,
        endMillisExclusive: Long,
        framework: Framework,
        /** Live deliverable state (v0.34.0) — supplies `done`, which no [RollupItem] can carry without
         *  going stale. A tag with no matching fact still aggregates; it just reads as active. */
        deliverables: List<DeliverableFact> = emptyList(),
        highlightCap: Int = HIGHLIGHT_CAP,
    ): AggregatedRollup {
        val windowed = items.filter { it.timeMillis in startMillis until endMillisExclusive }
        if (windowed.isEmpty()) return AggregatedRollup(emptyList(), emptyList(), 0)

        // Group by goal area (case-insensitive key, first-seen display name).
        val byArea = LinkedHashMap<String, MutableList<RollupItem>>()
        val areaDisplay = LinkedHashMap<String, String>()
        for (it in windowed) {
            val key = it.goalArea.lowercase()
            areaDisplay.getOrPut(key) { it.goalArea }
            byArea.getOrPut(key) { mutableListOf() }.add(it)
        }

        // Framework goal-area order first, then any leftover data areas (kept, never lost).
        val fwGoalKeys = framework.pillars.filter { it.kind != PillarKind.BEHAVIOUR }.map { it.name.lowercase() }
        val orderedKeys = (fwGoalKeys.filter { byArea.containsKey(it) } + byArea.keys.filter { it !in fwGoalKeys }).distinct()
        // DEVELOPMENT-kind pillar names (case-insensitive): these areas serialize under a
        // "DEVELOPMENT AREA:" header (AI-2) so the summary routes them into development[].
        val devKeys = framework.pillars.filter { it.kind == PillarKind.DEVELOPMENT }.map { it.name.lowercase() }.toSet()

        // Live `done` state, keyed by the deliverable's FULL identity — a name alone is not one.
        val factByIdentity = deliverables.associateBy {
            Triple(norm(it.name), norm(it.project), norm(it.goalArea))
        }

        val goalAreas = orderedKeys.map { key ->
            val group = byArea.getValue(key)
            // De-dup (Phase 1): collapse exact/normalized-identical non-routine bullets in the same
            // project into ONE highlight with a count, BEFORE the cap so the count stays accurate even
            // when repeats exceed the highlight cap. Progressive notes on one piece of work normalize
            // differently, so an arc ("started X" → "shipped X, cut 18%") is NOT merged here — that
            // stays the model's call, which is exactly what the v0.34.0 deliverable tag anchors.
            val highlights = mergeNotable(group.filter { !it.routine })
                .sortedWith(compareByDescending<MergedNotable> { it.impact }.thenByDescending { it.timeMillis })
                .take(highlightCap)
                .map {
                    AggHighlight(
                        bullet = it.bullet, project = it.project, metric = it.metric, impact = it.impact,
                        isExtra = it.isExtra, demonstrates = it.demonstrates, deliverable = it.deliverable,
                        count = it.count, ids = it.ids,
                    )
                }
            // v0.34.0 · the area's deliverable index. Counts EVERY windowed entry of the thread — routine
            // ones and ones the highlight cap dropped — so the model can write "a 6-month, 47-entry
            // thread" honestly from the handful of bullets it can actually see. Grouped by (project,
            // deliverable): the same deliverable name under two projects is two different threads.
            val deliverableGroups = group
                .filter { !it.deliverable.isNullOrBlank() && !it.project.isNullOrBlank() }
                .groupBy { norm(it.project) to norm(it.deliverable) }
                .map { (_, rows) ->
                    val rep = rows.first()
                    val name = rep.deliverable!!.trim()
                    val proj = rep.project!!.trim()
                    AggDeliverable(
                        name = name,
                        project = proj,
                        done = factByIdentity[Triple(norm(name), norm(proj), norm(areaDisplay.getValue(key)))]?.done ?: false,
                        entryCount = rows.size,
                        firstMillis = rows.minOf { it.timeMillis },
                        lastMillis = rows.maxOf { it.timeMillis },
                    )
                }
                .sortedWith(compareByDescending<AggDeliverable> { it.entryCount }.thenBy { it.name })
                .take(DELIVERABLE_CAP)
            val routine = group.filter { it.routine }
                .groupBy { (it.routineType ?: "Other").trim().ifBlank { "Other" } }
                .map { (type, rows) ->
                    AggRoutine(type, rows.size, rows.mapNotNull { it.metric?.takeIf { m -> m.isNotBlank() } }.distinct())
                }
                .sortedByDescending { it.count }
            val metrics = group.filter { it.routine }
                .mapNotNull { it.metric?.takeIf { m -> m.isNotBlank() } }
                .distinct().take(METRIC_CAP)
            AggGoalArea(
                areaDisplay.getValue(key), highlights, routine, metrics,
                deliverables = deliverableGroups, isDevelopment = key in devKeys,
            )
        }

        // Behaviour evidence: concrete bullets from notable work, ranked by impact.
        val behaviourPillars = framework.pillars.filter { it.kind == PillarKind.BEHAVIOUR }
        val notableAll = windowed.filter { !it.routine }
            .sortedWith(compareByDescending<RollupItem> { it.impact }.thenByDescending { it.timeMillis })
        val behaviours = behaviourPillars.mapNotNull { p ->
            val evidence = notableAll
                .filter { row -> row.demonstrates.any { it.equals(p.name, ignoreCase = true) } }
                .map { it.bullet }.distinct().take(EVIDENCE_CAP)
            if (evidence.isEmpty()) null else AggBehaviour(p.name, evidence)
        }

        return AggregatedRollup(goalAreas, behaviours, windowed.size)
    }

    /** Serialise the aggregate into the `{{ROLLUP}}` prompt block — bounded, tight, model-friendly. */
    fun serialize(agg: AggregatedRollup): String = buildString {
        agg.goalAreas.forEach { area ->
            appendLine("${if (area.isDevelopment) "DEVELOPMENT AREA" else "GOAL AREA"}: ${area.name}")
            if (area.deliverables.isNotEmpty()) {
                appendLine("  Deliverables (the user's own grouping — each is ONE thread of work):")
                area.deliverables.forEach { d ->
                    append("  - \"").append(d.name).append("\" (project: ").append(d.project).append(") — ")
                    append(d.entryCount).append(if (d.entryCount == 1) " entry" else " entries")
                    spanLabel(d.firstMillis, d.lastMillis)?.let { append(" ").append(it) }
                    appendLine(if (d.done) ", DONE" else ", active")
                }
            }
            if (area.highlights.isNotEmpty()) {
                appendLine("  Highlights (ranked by impact):")
                area.highlights.forEach { h ->
                    append("  - [impact ").append(fmt(h.impact)).append("] ").append(h.bullet)
                    h.project?.let { append(" (project: ").append(it).append(")") }
                    h.deliverable?.let { append(" (deliverable: ").append(it).append(")") }
                    h.metric?.let { append(" (metric: ").append(it).append(")") }
                    if (h.count > 1) append(" (logged ").append(h.count).append("×)")
                    if (h.isExtra) append(" (standout)")
                    if (h.demonstrates.isNotEmpty()) append(" (evidences: ").append(h.demonstrates.joinToString(", ")).append(")")
                    appendLine()
                }
            }
            if (area.routine.isNotEmpty()) {
                appendLine("  Routine tallies:")
                area.routine.forEach { r ->
                    append("  - ").append(r.routineType).append(": ").append(r.count).append("×")
                    if (r.metrics.isNotEmpty()) append(" (metrics: ").append(r.metrics.joinToString("; ")).append(")")
                    appendLine()
                }
            }
            if (area.metrics.isNotEmpty()) appendLine("  Cumulative metrics: ${area.metrics.joinToString("; ")}")
            appendLine()
        }
        if (agg.behaviours.isNotEmpty()) {
            appendLine("BEHAVIOUR EVIDENCE (concrete bullets that demonstrated each):")
            agg.behaviours.forEach { b ->
                appendLine("- ${b.name}:")
                b.evidence.forEach { appendLine("  - $it") }
            }
        }
    }.trim()

    /**
     * A stable content signature over everything that should invalidate a cached summary (the rollup
     * window, pinned items, framework, role, period, length). Uses [String.hashCode], which the JVM
     * specifies deterministically — so "regenerate only when the rollup changed" is a cheap compare.
     */
    fun signature(vararg parts: String): String =
        parts.joinToString("").hashCode().toString()

    private fun fmt(v: Double): String {
        val clamped = v.coerceIn(0.0, 1.0)
        return ((clamped * 100).toInt() / 100.0).toString()
    }

    /**
     * "over 6 months" for a deliverable's date span, or null when it's too short to be worth saying.
     *
     * Deliberately pure arithmetic on the millisecond span — NOT a calendar conversion. Turning epochs
     * into dates would need a time zone, which would drag an environment dependency into a function the
     * unit tests rely on being deterministic, to say something no more true than this.
     */
    internal fun spanLabel(firstMillis: Long, lastMillis: Long): String? {
        val days = ((lastMillis - firstMillis).coerceAtLeast(0L) / 86_400_000L).toInt()
        return when {
            days < 7 -> null
            days < 14 -> "over 1 week"
            days < 60 -> "over ${days / 7} weeks"
            else -> "over ${days / 30} months"
        }
    }

    private val WS = Regex("\\s+")

    /** The identity-comparison normal form: trimmed, lowercased, single-spaced. */
    private fun norm(s: String?): String = (s ?: "").trim().lowercase().replace(WS, " ")
}

/** A de-duped highlight carrying the merge count (+ recency, for the secondary sort). */
private data class MergedNotable(
    val bullet: String,
    val project: String?,
    val metric: String?,
    val impact: Double,
    val isExtra: Boolean,
    val demonstrates: List<String>,
    val deliverable: String?,
    val count: Int,
    val timeMillis: Long,
    /** Every source entry id merged into this one — client-side only, never serialized to the model. */
    val ids: List<Long>,
)

/**
 * Collapse exact/normalized-identical bullets in the SAME project **and deliverable** into one
 * [MergedNotable] with a count. Deterministic (string-normalization only) so it never falsely merges
 * genuinely-distinct or progressive work — the semantic near-duplicate calls are left to the capped
 * summary model, which from v0.34.0 gets the deliverable tag to make them on. The
 * representative keeps the highest-impact row's phrasing; metrics/behaviour-evidence are unioned.
 */
private fun mergeNotable(items: List<RollupItem>): List<MergedNotable> {
    val groups = LinkedHashMap<String, MutableList<RollupItem>>()
    for (row in items) {
        // The deliverable joins the key (v0.34.0): the same bullet under two different deliverables is
        // two genuinely different threads of work, so merging them would fold one thread into the other
        // and drop a tag. Pre-v0.34.0 rows carry none, so their key — and their merging — is unchanged.
        //
        // The separator is an ESCAPED NUL, not a literal one. It stays collision-proof — a user cannot
        // type it into a project or deliverable name, so ("a", "b x") can never key-collide with
        // ("a b", "x") — while keeping this file TEXT: the raw byte that used to sit here made git
        // treat the whole file as binary, so it rendered no diff in review and grep skipped it.
        val key = (row.project?.trim()?.lowercase() ?: "") + "\u0000" +
            (row.deliverable?.trim()?.lowercase() ?: "") + "\u0000" + normalizeBullet(row.bullet)
        groups.getOrPut(key) { mutableListOf() }.add(row)
    }
    return groups.values.map { rows ->
        val rep = rows.maxByOrNull { it.impact } ?: rows.first()
        MergedNotable(
            bullet = rep.bullet,
            project = rep.project,
            metric = rep.metric?.takeIf { it.isNotBlank() }
                ?: rows.firstNotNullOfOrNull { it.metric?.takeIf { m -> m.isNotBlank() } },
            impact = rep.impact,
            isExtra = rows.any { it.isExtra },
            demonstrates = rows.flatMap { it.demonstrates }.distinct(),
            // Safe to take from the representative: the key means every row here shares its deliverable.
            deliverable = rep.deliverable,
            count = rows.size,
            timeMillis = rows.maxOf { it.timeMillis },
            // Representative-first so a single-target action (retag) hits the row whose phrasing the
            // user is actually looking at; the rest ride along (a merged card re-files every source).
            ids = (listOf(rep.id) + rows.map { it.id }).distinct(),
        )
    }
}

private val BULLET_WS = Regex("\\s+")
private val BULLET_TRIM = charArrayOf('.', '!', '?', ',', ';', ':', ' ', '—', '-').toSet()

/** Normalize a bullet for duplicate detection: lowercase, single-spaced, trailing punctuation stripped. */
private fun normalizeBullet(s: String): String =
    s.trim().lowercase().replace(BULLET_WS, " ").trim { it in BULLET_TRIM }
