package com.bragbuddy.app.data.project

import com.bragbuddy.app.data.local.ProjectDao
import com.bragbuddy.app.data.local.ProjectEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place projects (a.k.a. **folders**) are created and read. A folder's name *is* its project
 * name — the same string the categorizer places entries into. Kept low-friction: a project needs
 * only a name; its goal area defaults to the framework's first goal category (editable in Settings).
 */
@Singleton
class ProjectRepository @Inject constructor(
    private val dao: ProjectDao,
) {
    fun observeActive(): Flow<List<ProjectEntity>> = dao.observeActive()

    /**
     * Create a folder. No-op if the (unique) name already exists — the insert ignores on conflict,
     * so tapping "add" twice can't duplicate. Returns the new row id, or 0 if it already existed.
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

    suspend fun goalAreaOf(name: String): String? = dao.getByName(name)?.goalArea
}
