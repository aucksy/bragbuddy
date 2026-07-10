package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.EntryEntity

/**
 * Pure helpers backing the **fix-a-wrong-category** flow (Phase 2 · Recategorize). No AI, no I/O — so
 * the "what should the picker preselect" logic is unit-tested and the entry-detail sheet stays thin.
 *
 * Two axes an entry can be filed on:
 *  - a **placement category** (a GOAL_AREA or DEVELOPMENT pillar) — the entry's `goalCategory`; a
 *    BEHAVIOUR pillar is *evidence*, never a placement slot.
 *  - **behaviour evidence** — the BEHAVIOUR pillars an entry demonstrates (its `demonstrates` tags).
 *
 * Recategorize always assigns a real placement category (never blank): the running rollup drops any
 * entry whose `goalCategory` is blank/"Inbox" ([com.bragbuddy.app.data.rollup.toRollupItem]), so an
 * entry must keep a goal area for its behaviour evidence to reach the generated summary.
 */
object Recategorize {

    /** The placement categories (GOAL_AREA + DEVELOPMENT) — everything an entry can be *filed under*. */
    fun placementCategories(framework: Framework): List<Pillar> =
        framework.pillars.filter { it.kind != PillarKind.BEHAVIOUR }

    /** The behaviour pillars — the multi-select *evidence* options. */
    fun behaviourCategories(framework: Framework): List<Pillar> =
        framework.pillars.filter { it.kind == PillarKind.BEHAVIOUR }

    /**
     * The placement category to preselect (its **canonical** pillar name): the entry's current goal area
     * when it still matches a placement category (case-insensitive), else the first placement category,
     * else null (a framework with no goal/growth pillars at all — a degenerate state; Apply is then
     * disabled in the sheet).
     */
    fun defaultCategory(entry: EntryEntity, framework: Framework): String? {
        val categories = placementCategories(framework)
        val current = entry.goalCategory?.trim()
        val match = current?.let { c -> categories.firstOrNull { it.name.equals(c, ignoreCase = true) } }
        return match?.name ?: categories.firstOrNull()?.name
    }

    /**
     * The project (folder) to preselect within [category]: the entry's current project when it names a
     * real folder under that exact category (case-insensitive), else null ("No specific project"). A
     * folder is unique by (name, goalArea), so the match is scoped by the category too — switching the
     * category clears a folder that belonged to the old one.
     */
    fun defaultFolder(entry: EntryEntity, category: String?, folders: List<FolderRef>): String? {
        val cat = category?.trim() ?: return null
        val proj = entry.project?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return folders.firstOrNull {
            it.name.equals(proj, ignoreCase = true) && it.goalArea.equals(cat, ignoreCase = true)
        }?.name
    }

    /**
     * The behaviour pillars to precheck: those the entry currently demonstrates (case-insensitive),
     * returned as the pillars' **canonical names** so applying rewrites `demonstrates` to exact,
     * current names (dropping any stale tag that no longer matches a behaviour pillar — it was already
     * invisible on Home, which matches evidence against current pillars).
     */
    fun defaultBehaviours(entry: EntryEntity, framework: Framework): Set<String> =
        behaviourCategories(framework)
            .filter { p -> entry.demonstrates.any { it.trim().equals(p.name, ignoreCase = true) } }
            .map { it.name }
            .toSet()

    /** The minimal folder shape the pure helpers need (name + its goal area). */
    data class FolderRef(val name: String, val goalArea: String)
}
