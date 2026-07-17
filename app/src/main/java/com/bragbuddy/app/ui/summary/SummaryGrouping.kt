package com.bragbuddy.app.ui.summary

import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.local.NO_PROJECT_LABEL
import com.bragbuddy.app.data.local.isNamedProject
import com.bragbuddy.app.data.rollup.AggDeliverable

/**
 * Groups a goal area's summary achievements into **project folders** (Summary phase · item 5),
 * mirroring the Home tab's project grouping ([com.bragbuddy.app.ui.home.goalProjectGroups]). Pure +
 * unit-tested so the render and the folder-scoped reorder agree exactly on which slot is which.
 */

/** Label for the loose bucket holding achievements with no named project. Aliases the single
 *  definition in the data layer so Summary and Home can never drift apart on the wording. */
const val SUMMARY_OUTSIDE_LABEL = NO_PROJECT_LABEL

/** An achievement plus its index in the goal area's FLAT achievements list — so a folder-scoped
 *  reorder can address the original slot in [com.bragbuddy.app.data.ai.SummaryGoalArea.achievements]. */
data class IndexedAchievement(val flatIndex: Int, val achievement: SummaryAchievement)

/** A project folder within a goal area: display name + its achievements (each with its flat index). */
data class SummaryFolder(val name: String, val isOutside: Boolean, val items: List<IndexedAchievement>)

/** A deliverable group inside a project folder (v0.34.0): its name + the achievements telling its
 *  story. [name] is null for the folder's loose work — achievements belonging to no deliverable. */
data class SummaryDeliverableGroup(val name: String?, val items: List<IndexedAchievement>)

/**
 * Sub-group one project folder's achievements by **deliverable** (v0.34.0) — the third level of the
 * record (Category → Project → Deliverable), mirroring Home.
 *
 * Named deliverables keep first-appearance order; the folder's loose work (no deliverable) sinks to the
 * end as a single `name = null` group. Returns **null** when there is no structure worth drawing —
 * fewer than two groups — so a folder whose work is all one deliverable, or all loose, renders exactly
 * as it did before instead of gaining a header that says nothing. Same rule, and same reason, as
 * [groupAchievementsByProject].
 *
 * Grouping is by name alone, which is safe ONLY here: these achievements have already been bucketed
 * into one project by [groupAchievementsByProject], and this runs within a single goal area — so
 * `(name, project, goalArea)`, a deliverable's real identity, is fully pinned by the caller's position.
 */
fun groupFolderByDeliverable(items: List<IndexedAchievement>): List<SummaryDeliverableGroup>? {
    if (items.isEmpty()) return null
    val buckets = LinkedHashMap<String, MutableList<IndexedAchievement>>()
    val display = LinkedHashMap<String, String?>()
    items.forEach { ia ->
        val name = ia.achievement.deliverable?.trim()?.takeIf { it.isNotBlank() }
        // Normalized like every other identity comparison in the app (trim + lowercase + single-space).
        // This value is whatever the MODEL echoed, not a resolved tag, so "market  rollout" and
        // "Market rollout" genuinely do arrive as the same thread and must land in one group.
        val key = name?.lowercase()?.replace(WS, " ") ?: LOOSE_KEY
        display.getOrPut(key) { name }
        buckets.getOrPut(key) { mutableListOf() }.add(ia)
    }
    val ordered = buckets.entries
        .map { (key, group) -> SummaryDeliverableGroup(display.getValue(key), group) }
        .sortedBy { it.name == null }
    return if (ordered.size >= 2) ordered else null
}

/** Bucket key for "no deliverable". A real name can never collide with it: a blank one is treated as
 *  loose above, so every named key is non-empty by construction. */
private const val LOOSE_KEY = ""

private val WS = Regex("\\s+")

private fun norm(s: String?): String = (s ?: "").trim().lowercase().replace(WS, " ")

/**
 * Snap each achievement's echoed `deliverable` onto a deliverable the rollup actually named, dropping
 * anything else (v0.34.0).
 *
 * ⭐ Every other deliverable name in the app is resolved against a real identity —
 * [com.bragbuddy.app.data.entry.DeliverableGuess] does it for the categorizer's guess — and PART B's
 * echo was the one that wasn't. The prompt says "never invent a deliverable the rollup didn't name",
 * but a model paraphrasing "Market rollout" into "Market rollout phase" is ordinary LLM behaviour, not
 * a fault, and the consequence isn't cosmetic: an unresolved name mints a sub-header for a deliverable
 * that doesn't exist and prints `[Payments ▸ Market rollout phase]` into the document the user hands
 * their manager. The authoritative set is already in hand — it is what [aggDeliverables] was built
 * from — so trusting a name here would be a choice, not a necessity.
 *
 * Matched on the FULL identity within this area: name + the achievement's own project. A name alone is
 * not an identity, and two projects can each own a "Phase 1".
 */
fun resolveAchievementDeliverables(
    achievements: List<SummaryAchievement>,
    aggDeliverables: List<AggDeliverable>,
): List<SummaryAchievement> {
    if (achievements.none { !it.deliverable.isNullOrBlank() }) return achievements
    return achievements.map { a ->
        val guess = a.deliverable?.trim()?.takeIf { it.isNotBlank() } ?: return@map a
        val hit = aggDeliverables.firstOrNull {
            norm(it.name) == norm(guess) && norm(it.project) == norm(a.project)
        }
        // Adopt the STORED casing on a hit; drop the tag entirely on a miss — a wrong tag in the
        // exported document is worse than no tag, and the win itself is never touched either way.
        if (hit != null) a.copy(deliverable = hit.name) else a.copy(deliverable = null)
    }
}

/**
 * Group [achievements] into project folders. Named-project achievements cluster by project
 * (case-insensitive, first-appearance display name and order — the model already ranks them by
 * impact within the area); achievements with no named project ([isNamedProject]) collapse into a
 * single trailing [SUMMARY_OUTSIDE_LABEL] bucket. Each achievement keeps its flat index so the
 * caller can reorder within a folder by swapping the right slots.
 *
 * Returns **null** when there is no useful structure to show — fewer than two folders — so the caller
 * renders the area flat (as before) instead of wrapping a single-project or all-loose area in one
 * pointless folder.
 */
fun groupAchievementsByProject(achievements: List<SummaryAchievement>): List<SummaryFolder>? {
    if (achievements.isEmpty()) return null
    val buckets = LinkedHashMap<String, MutableList<IndexedAchievement>>()
    val display = LinkedHashMap<String, String>()
    achievements.forEachIndexed { i, a ->
        val proj = a.project?.trim().orEmpty()
        val isOutside = !proj.isNamedProject()
        val key = if (isOutside) SUMMARY_OUTSIDE_LABEL else proj.lowercase()
        display.getOrPut(key) { if (isOutside) SUMMARY_OUTSIDE_LABEL else proj }
        buckets.getOrPut(key) { mutableListOf() }.add(IndexedAchievement(i, a))
    }
    // Named folders keep first-appearance order (stable sort); the Outside bucket sinks to the end.
    val ordered = buckets.entries
        .map { (key, items) -> SummaryFolder(display.getValue(key), isOutside = key == SUMMARY_OUTSIDE_LABEL, items = items) }
        .sortedBy { it.isOutside }
    return if (ordered.size >= 2) ordered else null
}
