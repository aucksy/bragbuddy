package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.DeliverableEntity
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

    /**
     * The deliverables offerable for a given ([category], [folder]) — the picker's third level
     * (v0.33.0). Empty whenever there's no named project selected: a deliverable lives *inside* a
     * project, so "no specific project" has none to offer.
     *
     * **Done ones are still offered here**, deliberately. "Done drops out of the log-into list" is about
     * *capture* — where you're adding new work. This is a *correction*: a win from six months ago
     * genuinely belongs to the deliverable that shipped, and refusing to file it there would make the
     * record wrong to keep a rule that was never about corrections.
     */
    fun deliverablesFor(
        category: String?,
        folder: String?,
        deliverables: List<DeliverableEntity>,
    ): List<DeliverableEntity> {
        val cat = category?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val proj = folder?.trim()?.takeIf { it.isNotBlank() } ?: return emptyList()
        return deliverables
            .filter { it.project.equals(proj, ignoreCase = true) && it.goalArea.equals(cat, ignoreCase = true) }
            .sortedWith(compareBy({ it.done }, { it.name.lowercase() }))
    }

    /**
     * The deliverable to preselect within ([category], [folder]): the entry's current one when it still
     * names a real deliverable there, else null ("not part of one"). Scoped by both parents for the same
     * reason [defaultFolder] is scoped by category — a deliverable's name is not an identity on its own,
     * so switching the project must clear a deliverable that belonged to the old one rather than silently
     * matching a same-named deliverable somewhere else.
     */
    fun defaultDeliverable(
        entry: EntryEntity,
        category: String?,
        folder: String?,
        deliverables: List<DeliverableEntity>,
    ): String? {
        val current = entry.deliverable?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return deliverablesFor(category, folder, deliverables)
            .firstOrNull { it.name.equals(current, ignoreCase = true) }?.name
    }

    /** The minimal folder shape the pure helpers need (name + its goal area). */
    data class FolderRef(val name: String, val goalArea: String)
}
