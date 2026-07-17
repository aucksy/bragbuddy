package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.local.INBOX_PLACEMENT
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT

/**
 * One deliverable the categorizer is ALLOWED to pick (v0.34.0) — its full identity, never just a name.
 *
 * The universe is built in `EntryProcessor.prepare` from exactly the deliverables the prompt LISTS, so
 * "what the model was shown" and "what the model may pick" are the same set by construction. Two
 * different questions get two different checks, deliberately:
 *  - **the model's guess** → resolved against this universe: **Active** deliverables under a
 *    **placement** project, because that is all the prompt offered it.
 *  - **the user's pin** (`anchorDeliverable`) → checked against the DAO (`deliverableExists`), which
 *    filters on neither. A tap-in pinned while the deliverable was Active must still bind if the user
 *    marks it Done before an offline capture drains — the user's decision doesn't expire.
 */
data class DeliverableRef(val name: String, val project: String, val goalArea: String)

/**
 * Resolves the categorizer's `deliverable` guess against the deliverables that really exist.
 *
 * ⭐ **A name is not an identity.** A deliverable is unique by `(name, project, goalArea)` — "Phase 1"
 * exists under Payments *and* under a different category's same-named Payments, and they are genuinely
 * different work. So a guess is only ever resolved with all three, and the project/area used MUST be the
 * ones the row is actually **filed** under (`anchor ?: c.project`), not the ones the model itself
 * guessed: an anchored capture's project is decided by the user's tap, and validating "Market rollout"
 * against the model's own wrong project would happily durably file it under a project it isn't part of.
 *
 * Pure, so the whole rule is unit-tested away from the pipeline.
 */
object DeliverableGuess {

    private val WS = Regex("\\s+")

    private fun norm(s: String?): String = (s ?: "").trim().lowercase().replace(WS, " ")

    /**
     * The canonical name of [guess] if it really is a deliverable of ([project], [goalArea]) in
     * [universe] — otherwise **null**, which is the normal, safe answer.
     *
     * Null covers every "don't guess" case at once: the model omitted the field, invented a name, named
     * a real deliverable of some OTHER project, or the row isn't filed under a real project at all
     * ([OUTSIDE_PROJECT] / [INBOX_PLACEMENT] are placements, not projects — nothing nests under them).
     * Matching is case- and whitespace-insensitive and returns the STORED casing, so a model that
     * echoes "market rollout" still lands on the user's "Market rollout" rather than creating a
     * near-twin tag that every editor (all of which scope by exact name) would then be blind to.
     */
    fun resolve(
        guess: String?,
        project: String?,
        goalArea: String?,
        universe: List<DeliverableRef>,
    ): String? {
        val g = norm(guess).ifEmpty { return null }
        val p = norm(project).ifEmpty { return null }
        val a = norm(goalArea).ifEmpty { return null }
        if (p == norm(OUTSIDE_PROJECT) || p == norm(INBOX_PLACEMENT)) return null
        return universe.firstOrNull { norm(it.name) == g && norm(it.project) == p && norm(it.goalArea) == a }?.name
    }
}
