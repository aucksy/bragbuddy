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
) {
    val isEmpty: Boolean get() = deleted.isEmpty() && edits.isEmpty() && restored.isEmpty()
}

@Serializable
data class BulletEdit(val from: String, val to: String)

@Serializable
data class RestoredNote(val text: String, val area: String)

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
fun applyOverrides(result: SummaryResult, overrides: SummaryOverrides): SummaryResult {
    if (overrides.isEmpty) return result

    val deleted = overrides.deleted.map { summaryKey(it) }.toSet()
    val edits = overrides.edits.associate { summaryKey(it.from) to it.to }

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

    // Restore: inject each note into its target area (create it if missing), de-duped by key.
    if (overrides.restored.isNotEmpty()) {
        val areas = goalAreas.toMutableList()
        overrides.restored.forEach { note ->
            val text = edit(note.text)
            if (dropped(text)) return@forEach
            val key = summaryKey(text)
            val idx = areas.indexOfFirst { it.name.equals(note.area, ignoreCase = true) }
            if (idx >= 0) {
                val already = areas[idx].achievements.any { summaryKey(it.bullet) == key }
                if (!already) areas[idx] = areas[idx].copy(
                    achievements = areas[idx].achievements + SummaryAchievement(bullet = text),
                )
            } else {
                areas.add(SummaryGoalArea(name = note.area, achievements = listOf(SummaryAchievement(bullet = text))))
            }
        }
        goalAreas = areas
    }

    // Safety net: collapse any achievements that normalize identically within an area (belt-and-braces
    // against a restore/edit interaction or a model that emits the same line twice — keeps the first).
    goalAreas = goalAreas.map { area ->
        area.copy(achievements = area.achievements.distinctBy { summaryKey(it.bullet) })
    }

    val behaviours = result.summary.behaviours.map { b ->
        b.copy(evidence = b.evidence.map { edit(it) }.filterNot { dropped(it) })
    }
    val development = result.summary.development.map { edit(it) }.filterNot { dropped(it) }

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
