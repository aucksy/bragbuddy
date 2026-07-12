package com.bragbuddy.app.data.project

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
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val dao: ProjectDao,
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

    /** Rename / re-describe / re-home a project. */
    suspend fun update(id: Long, name: String, goalArea: String, description: String?) {
        val existing = dao.getById(id) ?: return
        val clean = name.trim().ifBlank { return }
        dao.update(
            existing.copy(
                name = clean,
                goalArea = goalArea.trim().ifBlank { existing.goalArea },
                description = description?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Read one folder by id (e.g. to recover its old name / description before a rename-remap). */
    suspend fun getById(id: Long): ProjectEntity? = dao.getById(id)

    suspend fun goalAreaOf(name: String): String? = dao.getByName(name)?.goalArea

    /** Read one folder by its exact name (e.g. the capture anchor → its detail feeds the impact coach). */
    suspend fun byName(name: String): ProjectEntity? = dao.getByName(name.trim())

    /** Re-home every sub-folder when its category (pillar) is renamed, so folders don't orphan. */
    suspend fun renameCategory(oldName: String, newName: String) {
        val a = oldName.trim()
        val b = newName.trim()
        if (a.isEmpty() || b.isEmpty() || a == b) return
        dao.reassignCategory(a, b)
    }

    /** Remove every sub-folder under a category when the category itself is deleted. */
    suspend fun deleteByCategory(name: String) {
        val n = name.trim().ifBlank { return }
        dao.deleteByCategory(n)
    }
}
