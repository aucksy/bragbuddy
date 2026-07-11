package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.ai.CategorizeResult
import com.bragbuddy.app.data.ai.CategorizedEntry
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT
import java.time.LocalDate

/**
 * Deterministic output validation for the daily categorizer (AI-1 · 1c). Applied between a successful
 * [com.bragbuddy.app.data.ai.AiProvider.categorize] and the row write, it snaps the model's fuzzy
 * output onto the app's canonical universe so a near-miss ("Raven migration" vs "Raven Migration",
 * "code review" vs "code reviews") files correctly instead of surfacing under Uncategorized, and a
 * hallucinated project can never phantom-file. Pure — unit-tested away from the pipeline.
 *
 * The rules (all case/whitespace-insensitive):
 *  - **project** → snap to the exact placement-project casing. A value that is neither a known project
 *    nor "Outside-project"/"Inbox" is a **phantom** → forced to "Inbox", the model's guess prepended to
 *    "suggestedProjects" (so the Inbox chip still offers it), and confidence capped at 0.5 (the
 *    Inbox-floor routing then parks it — nothing lost, nothing phantom-filed).
 *  - **goalCategory** → snap to a goal-area / development pillar name; an unknown value is LEFT VERBATIM
 *    (the Uncategorized catch-all must keep its guarantee — a filed entry is never hidden).
 *  - **demonstrates** → snap each tag to a canonical BEHAVIOUR pillar name; drop the ones that match no
 *    behaviour (kills ghost/sub-behaviour inflation).
 *  - **dateMentioned** → reject an implausible date (> 370 days past, or on/after tomorrow); the row
 *    then keeps its capture time as occurredAt.
 *
 * Anchored captures are handled deterministically downstream (the folder tap wins in `applyCategorized`),
 * so this never touches the project of an anchored row's placement — the caller passes the raw result and
 * the anchor override applies after.
 */
object CategorizedNormalizer {

    private const val INBOX = "Inbox"
    private const val CONFIDENCE_PHANTOM_CAP = 0.5

    private fun norm(s: String?): String = (s ?: "").trim().lowercase().replace(Regex("\\s+"), " ")

    fun normalize(
        result: CategorizeResult,
        placementProjects: List<String>,
        pillars: List<Pillar>,
        today: LocalDate = LocalDate.now(),
    ): CategorizeResult {
        val projByNorm = placementProjects.associateBy { norm(it) }
        val goalNames = pillars
            .filter { it.kind == PillarKind.GOAL_AREA || it.kind == PillarKind.DEVELOPMENT }
            .associate { norm(it.name) to it.name }
        val behaviourNames = pillars
            .filter { it.kind == PillarKind.BEHAVIOUR }
            .associate { norm(it.name) to it.name }

        return CategorizeResult(result.entries.map { e -> normalizeEntry(e, projByNorm, goalNames, behaviourNames, today) })
    }

    private fun normalizeEntry(
        e: CategorizedEntry,
        projByNorm: Map<String, String>,
        goalNames: Map<String, String>,
        behaviourNames: Map<String, String>,
        today: LocalDate,
    ): CategorizedEntry {
        val projNorm = norm(e.project)
        // --- project ---
        var project = e.project
        var confidence = e.confidence
        var suggested = e.suggestedProjects
        when {
            projNorm == norm(INBOX) -> project = INBOX
            projNorm == norm(OUTSIDE_PROJECT) -> project = OUTSIDE_PROJECT
            projByNorm.containsKey(projNorm) -> project = projByNorm.getValue(projNorm) // snap to canonical casing
            else -> {
                // Phantom project the model invented → park in Inbox, keep the guess as a suggestion.
                val guess = e.project.trim()
                suggested = (listOfNotNull(guess.takeIf { it.isNotBlank() }) + e.suggestedProjects)
                    .distinctBy { norm(it) }
                project = INBOX
                confidence = minOf(confidence, CONFIDENCE_PHANTOM_CAP)
            }
        }

        // --- goalCategory ---
        val goalNorm = norm(e.goalCategory)
        val goalCategory = when {
            goalNorm == norm(INBOX) -> INBOX
            goalNames.containsKey(goalNorm) -> goalNames.getValue(goalNorm)
            else -> e.goalCategory // unknown stays verbatim (Uncategorized guarantee)
        }

        // --- demonstrates: snap to canonical behaviour names, drop unknowns ---
        val demonstrates = e.demonstrates
            .mapNotNull { behaviourNames[norm(it)] }
            .distinct()

        // --- dateMentioned: reject implausible dates ---
        val dateMentioned = e.dateMentioned?.takeIf { iso ->
            runCatching { LocalDate.parse(iso.trim()) }.getOrNull()?.let { d ->
                !d.isAfter(today) && !d.isBefore(today.minusDays(370))
            } ?: false
        }

        return e.copy(
            project = project,
            goalCategory = goalCategory,
            demonstrates = demonstrates,
            confidence = confidence,
            suggestedProjects = suggested,
            dateMentioned = dateMentioned,
        )
    }
}
