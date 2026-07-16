package com.bragbuddy.app.ui.summary

import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.local.NO_PROJECT_LABEL
import com.bragbuddy.app.data.local.isNamedProject

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
