package com.bragbuddy.app.data.project

import com.bragbuddy.app.data.local.DeliverableDao
import com.bragbuddy.app.data.local.ProjectDao
import com.bragbuddy.app.data.local.ProjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place projects (a.k.a. **folders / sub-folders**) are created and read. A folder's name
 * *is* its name — the same string that gives the AI context (and, for goal-area folders, the
 * "project" the categorizer files entries into). [ProjectEntity.goalArea] holds the **category
 * (pillar) name** the folder sits under — any category, not only goal areas — so the Framework editor
 * and Home both manage the same folders (they stay in sync because they read this one table).
 *
 * **Deliverables cascade from here** (v0.33.0). A deliverable is identified by
 * `(name, project, goalArea)` — i.e. by its parents' *names* — so every rename/re-home/delete of a
 * folder or a category must move or remove the deliverables underneath it, or they orphan. That cascade
 * lives here, at the single choke point every caller already goes through ([update], [delete],
 * [renameCategory], [deleteByCategory]), rather than at the call sites — deliberately:
 *  - a rename with **no filed records shows no remap sheet**, so a cascade hung off the remap flow would
 *    silently skip exactly the case where a project is renamed early, before anything is logged into it;
 *  - the framework editor and the Settings folder dialog are separate call sites that must not drift.
 * The **entries'** deliverable columns are cleared separately, through `EntryProcessor` (every entry
 * write goes under its mutex).
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val dao: ProjectDao,
    private val deliverables: DeliverableDao,
) {
    fun observeActive(): Flow<List<ProjectEntity>> = dao.observeActive()

    /**
     * Create a folder. No-op if the (unique) name already exists under this category — the insert
     * ignores on conflict, so tapping "add" twice can't duplicate. Returns the new row id, or a
     * NON-POSITIVE value (Room returns -1 on an IGNOREd conflict; 0 for a blank name) when nothing
     * was inserted — callers must treat `<= 0` as "not created".
     */
    suspend fun create(name: String, goalArea: String, description: String? = null): Long {
        val clean = name.trim()
        if (clean.isEmpty()) return 0
        return dao.insert(
            ProjectEntity(
                name = clean,
                goalArea = goalArea.trim().ifBlank { "Performance Goals" },
                description = description?.trim()?.takeIf { it.isNotBlank() },
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Rename / re-describe / re-home a project. Its **deliverables follow it** — they're identified by
     *  their parent's name, so without this a rename would strand every one of them (v0.33.0). */
    suspend fun update(id: Long, name: String, goalArea: String, description: String?) {
        val existing = dao.getById(id) ?: return
        val clean = name.trim().ifBlank { return }
        val area = goalArea.trim().ifBlank { existing.goalArea }
        dao.update(
            existing.copy(
                name = clean,
                goalArea = area,
                description = description?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
        // Cascade on the EFFECT, not the intent — re-read rather than trusting `clean`/`area`.
        // [ProjectDao.update] is `UPDATE OR IGNORE`, so a rename that collides with a sibling folder
        // under the same goal area is silently skipped, and no caller validates that up front. Gating on
        // the requested name would then move this project's deliverables to a project the user never
        // touched (found in review, v0.33.0): rename "Alpha" → "Beta" where Beta exists, the folder stays
        // "Alpha", but "Alpha"'s deliverables re-parent under Beta — leaving Alpha's entries tagged to a
        // deliverable that is no longer there, and Beta owning one nobody created.
        // Before this phase the IGNOREd update was a harmless no-op; it only desynchronises now that a
        // second table hangs off the name.
        val after = dao.getById(id) ?: return
        if (!after.name.equals(existing.name, ignoreCase = true) ||
            !after.goalArea.equals(existing.goalArea, ignoreCase = true)
        ) {
            deliverables.remapProject(existing.name, existing.goalArea, after.name, after.goalArea)
        }
    }

    /** Delete a project **and its deliverables** (a deliverable can't outlive the project it names).
     *  The *entries* are untouched, exactly as before — they keep their labels and surface under their
     *  category's "no specific project" bucket. Clearing their now-dangling deliverable tag is the
     *  caller's job, via `EntryRepository.clearProjectDeliverables`. */
    suspend fun delete(id: Long) {
        val existing = dao.getById(id)
        dao.deleteById(id)
        if (existing != null) deliverables.deleteByProject(existing.name, existing.goalArea)
    }

    /** Read one folder by id (e.g. to recover its old name / description before a rename-remap). */
    suspend fun getById(id: Long): ProjectEntity? = dao.getById(id)

    suspend fun goalAreaOf(name: String): String? = dao.getByName(name)?.goalArea

    /** Read one folder by its exact name (e.g. the capture anchor → its detail feeds the impact coach). */
    suspend fun byName(name: String): ProjectEntity? = dao.getByName(name.trim())

    /** Re-home every sub-folder when its category (pillar) is renamed, so folders don't orphan — and
     *  every deliverable underneath them, for the same reason (v0.33.0). */
    suspend fun renameCategory(oldName: String, newName: String) {
        val a = oldName.trim()
        val b = newName.trim()
        if (a.isEmpty() || b.isEmpty() || a == b) return
        dao.reassignCategory(a, b)
        deliverables.reassignCategory(a, b)
    }

    /** Remove every sub-folder — and every deliverable — under a category when the category itself is
     *  deleted. The entries stay (they're the record) and surface under "Uncategorized"; clearing their
     *  dangling deliverable tag is the caller's job, via `EntryRepository.clearCategoryDeliverables`. */
    suspend fun deleteByCategory(name: String) {
        val n = name.trim().ifBlank { return }
        dao.deleteByCategory(n)
        deliverables.deleteByCategory(n)
    }
}
