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

        val goalAreas = orderedKeys.map { key ->
            val group = byArea.getValue(key)
            val notable = group.filter { !it.routine }
                .sortedWith(compareByDescending<RollupItem> { it.impact }.thenByDescending { it.timeMillis })
            val highlights = notable.take(highlightCap).map {
                AggHighlight(it.bullet, it.project, it.metric, it.impact, it.isExtra, it.demonstrates)
            }
            val routine = group.filter { it.routine }
                .groupBy { (it.routineType ?: "Other").trim().ifBlank { "Other" } }
                .map { (type, rows) ->
                    AggRoutine(type, rows.size, rows.mapNotNull { it.metric?.takeIf { m -> m.isNotBlank() } }.distinct())
                }
                .sortedByDescending { it.count }
            val metrics = group.filter { it.routine }
                .mapNotNull { it.metric?.takeIf { m -> m.isNotBlank() } }
                .distinct().take(METRIC_CAP)
            AggGoalArea(areaDisplay.getValue(key), highlights, routine, metrics)
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
            appendLine("GOAL AREA: ${area.name}")
            if (area.highlights.isNotEmpty()) {
                appendLine("  Highlights (ranked by impact):")
                area.highlights.forEach { h ->
                    append("  - [impact ").append(fmt(h.impact)).append("] ").append(h.bullet)
                    h.project?.let { append(" (project: ").append(it).append(")") }
                    h.metric?.let { append(" (metric: ").append(it).append(")") }
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
}
