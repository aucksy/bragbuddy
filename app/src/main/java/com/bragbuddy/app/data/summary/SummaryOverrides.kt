package com.bragbuddy.app.data.summary

import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.ai.SummaryResult
import kotlinx.serialization.Serializable

/**
 * User edits to a generated summary that must SURVIVE a Regenerate (Phase 1, feature #1/#5).
 *
 * A summary "pointer" is AI-generated text with no stable id — and a Regenerate rebuilds the whole
 * [SummaryResult] from the rollup — so a plain in-place edit would be silently reverted next time the
 * model runs. Instead we record the user's intent as text-keyed overrides and re-apply them
 * (deterministically, [applyOverrides]) both immediately AND on top of every fresh generation.
 *
 * Guarantees:
 *  - **Delete** is reliable: a suppressed line stays gone even if the model re-emits the same text.
 *  - **Restore** (from Set-aside) is reliable: the note is re-injected into its chosen area. It is also
 *    removed from the Set-aside panel best-effort across a Regenerate (matched by text — a reworded note
 *    could momentarily reappear there, never a data loss).
 *  - **Edit** is best-effort across a Regenerate: applied by matching the pre-edit text; if the model
 *    rephrases the line beyond recognition next time, the edit naturally lapses (the content is fresh).
 *    Within a viewing session the edit always shows.
 *
 * All stored as DataStore JSON on [CachedSummary] (defaulted → old cached blobs decode unchanged).
 */
@Serializable
data class SummaryOverrides(
    /** Normalized keys of pointers the user deleted — suppressed everywhere. */
    val deleted: List<String> = emptyList(),
    /** Pre-edit (normalized) → new text. */
    val edits: List<BulletEdit> = emptyList(),
    /** Set-aside notes the user restored back into the body. */
    val restored: List<RestoredNote> = emptyList(),
    /**
     * Summary-ONLY placement corrections (v0.31.0) — the graceful fallback for a retag whose line
     * couldn't be resolved back to a record row. The write-back path does NOT use this: it corrects the
     * record, so a Regenerate re-derives the right placement on its own and needs no override.
     *
     * Best-effort across a Regenerate, exactly like [edits]: keyed by the line's text at move time, so
     * a model that rewords the line beyond recognition lets the move lapse (never a data loss — the
     * record, if it was reachable, is already right).
     */
    val moved: List<PlacementOverride> = emptyList(),
) {
    val isEmpty: Boolean
        get() = deleted.isEmpty() && edits.isEmpty() && restored.isEmpty() && moved.isEmpty()
}

@Serializable
data class BulletEdit(val from: String, val to: String)

/** [project]/[deliverable] (v0.39.1, defaulted so old JSON decodes): the restored win's real home,
 *  carried so the re-injected line renders with its project tag instead of appearing homeless. */
@Serializable
data class RestoredNote(
    val text: String,
    val area: String,
    val project: String? = null,
    val deliverable: String? = null,
)

/** A summary-only re-placement of one line: which goal area it should sit under, and its project
 *  (null = no specific project). [key] is the normalized bullet text at the time of the move. */
@Serializable
data class PlacementOverride(val key: String, val area: String, val project: String?)

/**
 * Point any RESTORED note matching [bullet] at [toArea] — used when a restored line is retagged.
 *
 * A restored note is sticky by design: [applyOverrides] re-injects it into `note.area` on every apply.
 * So retagging a restored line without this leaves the note pinning it to the OLD area while the move
 * holds it in the new one — and the next local edit re-injects it, putting one line in two places
 * permanently. Returns the overrides unchanged when nothing matches.
 */
fun SummaryOverrides.retargetRestored(bullet: String, toArea: String): SummaryOverrides {
    val key = summaryKey(bullet)
    if (restored.none { summaryKey(it.text) == key }) return this
    return copy(restored = restored.map { if (summaryKey(it.text) == key) it.copy(area = toArea) else it })
}

/** Normalize a pointer's text into a stable match key: lowercase, single-spaced, trailing punctuation stripped. */
fun summaryKey(s: String): String =
    s.trim().lowercase().replace(WHITESPACE, " ").trim { it in TRIM_CHARS }

private val WHITESPACE = Regex("\\s+")
private val TRIM_CHARS = charArrayOf('.', '!', '?', ',', ';', ':', ' ', '—', '-').toSet()

/**
 * Apply the user's [overrides] to a [result] — pure and idempotent (re-applying an already-applied
 * override is a no-op). Used at edit time (over the current cached result) and at generation time
 * (over the fresh model result) so manual changes persist across a Regenerate.
 */
fun applyOverrides(
    result: SummaryResult,
    overrides: SummaryOverrides,
    /**
     * Lowercased names of the framework's DEVELOPMENT-kind areas. Development items live in
     * [SummaryBody.development] (plain strings), NOT in [SummaryBody.goalAreas] — so a restore or move
     * whose area is one of these must inject into / pull from that list, or it silently does nothing
     * (a move of a Learning & Growth line never finds it) or renders a DUPLICATE header (a move INTO
     * Learning & Growth creates a second goalArea named the same as the development section). Empty by
     * default: callers that don't move across the goal/development boundary (the unit tests, an edit or
     * delete) are unaffected.
     */
    developmentAreas: Set<String> = emptySet(),
): SummaryResult {
    if (overrides.isEmpty) return result

    val deleted = overrides.deleted.map { summaryKey(it) }.toSet()
    val edits = overrides.edits.associate { summaryKey(it.from) to it.to }
    fun isDevArea(name: String) = name.trim().lowercase() in developmentAreas

    // Chain edits (A→B→C) to a fixpoint so a re-edited line resolves fully — this also keeps a
    // restored-then-re-edited note matching its achievement, so it isn't re-injected as a duplicate.
    fun edit(text: String): String {
        var t = text
        var guard = 0
        while (guard++ < 16) {
            val next = edits[summaryKey(t)] ?: return t
            if (next == t) return t
            t = next
        }
        return t
    }
    fun dropped(text: String): Boolean = summaryKey(text) in deleted

    // Goal-area achievements + rolled-up lines: edit, then drop deleted.
    var goalAreas = result.summary.goalAreas.map { area ->
        val achievements = area.achievements
            .map { it.copy(bullet = edit(it.bullet)) }
            .filterNot { dropped(it.bullet) }
        // Rolled-up lines are edit/delete-keyed by their displayed text (bullet, or routineType when the
        // bullet is blank). Apply the edit onto the bullet so an edited routine line actually changes.
        val rolledUp = area.rolledUp
            .map { r ->
                val display = r.bullet.ifBlank { r.routineType }
                val edited = edit(display)
                if (edited != display) r.copy(bullet = edited) else r
            }
            .filterNot { dropped(it.bullet.ifBlank { it.routineType }) }
        area.copy(achievements = achievements, rolledUp = rolledUp)
    }

    // Development items (a plain string list) get the same edit/delete, and become a MUTABLE list so a
    // restore or move whose area is a DEVELOPMENT area can inject into it (see developmentAreas).
    val development = result.summary.development.map { edit(it) }.filterNot { dropped(it) }.toMutableList()

    // Inject [text] into whichever surface [area] denotes — the development list, or a goal area
    // (created if missing). [preExisting] is the set of bullets present BEFORE any restore ran: the
    // fuzzy de-dup only fires against those (the model's own output), never against a line injected
    // earlier in the same pass — otherwise "Restore all" of two genuinely-distinct-but-similar dropped
    // items would collapse them to one (the review's finding).
    fun inject(
        areasMut: MutableList<SummaryGoalArea>,
        area: String,
        text: String,
        preExisting: Set<String>,
        project: String? = null,
        deliverable: String? = null,
    ) {
        val key = summaryKey(text)
        if (isDevArea(area)) {
            val already = development.any { summaryKey(it) == key } ||
                preExisting.any { SummaryResolver.similar(it, text) }
            if (!already) development.add(text)
            return
        }
        val idx = areasMut.indexOfFirst { it.name.equals(area, ignoreCase = true) }
        if (idx >= 0) {
            val already = areasMut[idx].achievements.any { summaryKey(it.bullet) == key } ||
                preExisting.any { SummaryResolver.similar(it, text) }
            if (!already) areasMut[idx] = areasMut[idx].copy(
                achievements = areasMut[idx].achievements +
                    SummaryAchievement(bullet = text, project = project, deliverable = deliverable),
            )
        } else {
            areasMut.add(
                SummaryGoalArea(
                    name = area,
                    achievements = listOf(SummaryAchievement(bullet = text, project = project, deliverable = deliverable)),
                ),
            )
        }
    }

    // Restore: inject each note into its target area (create it if missing), de-duped.
    if (overrides.restored.isNotEmpty()) {
        val areas = goalAreas.toMutableList()
        // The fuzzy-dedup baseline is snapshotted ONCE, before any injection — the model's own output
        // for each surface. It must NOT be recomputed per note from the live (mutating) lists, or note
        // #2 would fuzzy-match note #1 injected moments earlier and "Restore all" would silently collapse
        // two genuinely-distinct wins. Keyed by lowercased area name; the development list has one entry.
        val devBaseline = development.toSet()
        val areaBaseline = areas.associate { it.name.lowercase() to it.achievements.map { a -> a.bullet }.toSet() }
        fun baselineFor(area: String): Set<String> =
            if (isDevArea(area)) devBaseline else areaBaseline[area.trim().lowercase()].orEmpty()
        overrides.restored.forEach { note ->
            val text = edit(note.text)
            if (dropped(text)) return@forEach
            inject(areas, note.area, text, baselineFor(note.area), note.project, note.deliverable)
        }
        goalAreas = areas
    }

    // Summary-only placement moves (the retag fallback). Applied AFTER edits/restores so a moved line
    // is matched on the text actually being displayed, and after restores so a restored-then-moved note
    // lands in the right area. Handles the development list on BOTH ends.
    if (overrides.moved.isNotEmpty()) {
        val areas = goalAreas.toMutableList()
        overrides.moved.forEach { mv ->
            val target = summaryKey(mv.key)
            // Already exactly where it should be → no-op. This is what keeps applyOverrides IDEMPOTENT,
            // which is load-bearing: it is re-applied over its OWN output on every subsequent local edit
            // (see mutateCached). Without it, a re-apply would pull the line out and re-append it, quietly
            // reordering the destination whenever the moved line wasn't already last.
            if (isDevArea(mv.area)) {
                // The development list holds plain strings (no project), so once the line is there the
                // move is done regardless of mv.project — checking the project too would re-run the pull
                // + re-inject on every apply and break idempotency.
                if (development.any { summaryKey(it) == target }) return@forEach
            } else {
                val here = areas.firstOrNull { it.name.equals(mv.area, ignoreCase = true) }
                    ?.achievements?.firstOrNull { summaryKey(it.bullet) == target }
                if (here != null && here.project == mv.project) return@forEach
            }

            // Pull the line out of whichever surface currently holds it (a goal area, or development).
            var movingBullet: String? = null
            for (i in areas.indices) {
                val hit = areas[i].achievements.firstOrNull { summaryKey(it.bullet) == target } ?: continue
                movingBullet = hit.bullet
                areas[i] = areas[i].copy(achievements = areas[i].achievements.filterNot { it === hit })
                break
            }
            if (movingBullet == null) {
                val devHit = development.firstOrNull { summaryKey(it) == target }
                if (devHit != null) { movingBullet = devHit; development.remove(devHit) }
            }
            // A line that no longer exists (deleted, or reworded past recognition) simply lapses.
            val text = movingBullet ?: return@forEach
            inject(areas, mv.area, text, emptySet())
            // A move sets the project on the destination achievement (goal areas only; development is
            // plain strings). Re-apply it after inject, which created it project-less.
            if (mv.project != null && !isDevArea(mv.area)) {
                val di = areas.indexOfFirst { it.name.equals(mv.area, ignoreCase = true) }
                if (di >= 0) areas[di] = areas[di].copy(
                    achievements = areas[di].achievements.map {
                        if (summaryKey(it.bullet) == target) it.copy(project = mv.project) else it
                    },
                )
            }
        }
        // A move can empty an area — drop it rather than render a header with nothing under it.
        goalAreas = areas.filterNot { it.achievements.isEmpty() && it.rolledUp.isEmpty() }
    }

    // Safety net: collapse any achievements that normalize identically within an area (belt-and-braces
    // against a restore/edit interaction or a model that emits the same line twice — keeps the first).
    goalAreas = goalAreas.map { area ->
        area.copy(achievements = area.achievements.distinctBy { summaryKey(it.bullet) })
    }

    val behaviours = result.summary.behaviours.map { b ->
        // Apply edits/deletes to the category's own evidence AND to each nested competency's evidence
        // (Summary phase · item 4). A competency whose evidence is entirely deleted is dropped; the
        // UI filters a category left with neither evidence nor competencies.
        b.copy(
            evidence = b.evidence.map { edit(it) }.filterNot { dropped(it) },
            competencies = b.competencies
                .map { comp -> comp.copy(evidence = comp.evidence.map { edit(it) }.filterNot { dropped(it) }) }
                .filter { it.evidence.isNotEmpty() },
        )
    }
    // A restored (or deleted) note must leave the Set-aside panel.
    val restoredKeys = overrides.restored.map { summaryKey(it.text) }.toSet()
    val setAside = result.setAside.filterNot {
        val k = summaryKey(it.what)
        k in restoredKeys || k in deleted
    }

    return result.copy(
        summary = result.summary.copy(
            goalAreas = goalAreas,
            behaviours = behaviours,
            development = development,
        ),
        setAside = setAside,
    )
}
