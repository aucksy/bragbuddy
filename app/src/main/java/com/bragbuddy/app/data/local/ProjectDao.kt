package com.bragbuddy.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(project: ProjectEntity): Long

    // IGNORE so a rename that would collide with a sibling (name, goalArea) is a silent no-op rather
    // than a crash; the UI validates uniqueness up front, this is the safety net.
    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(project: ProjectEntity)

    @Query("SELECT * FROM projects WHERE archived = 0 ORDER BY sortOrder, name")
    fun observeActive(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects ORDER BY sortOrder, name")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    /** All folders, once (Phase 6 backup export). */
    @Query("SELECT * FROM projects ORDER BY id ASC")
    suspend fun getAllOnce(): List<ProjectEntity>

    /** Wipe folders (Phase 6 restore replaces them wholesale). */
    @Query("DELETE FROM projects")
    suspend fun deleteAll()

    /** Insert many folders preserving ids (Phase 6 restore). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(projects: List<ProjectEntity>)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Move every sub-folder from one category (pillar) to another — used when a category is renamed.
     *  OR IGNORE: if a folder would collide with one already under the target category, skip it rather
     *  than crash (the UI blocks renaming onto an existing category name; this is the safety net).
     *
     *  Case-INSENSITIVE, like every other name match in this schema. A binary compare here silently
     *  diverged from [DeliverableDao.reassignCategory], which is case-insensitive: rename a category by
     *  case alone (the cascade is skipped as a no-op, leaving folders on the old casing), then rename it
     *  properly, and the folders no longer matched while their deliverables did — moving deliverables to
     *  a category holding no parent project, unreachable forever (v0.33.0 assessment). */
    @Query("UPDATE OR IGNORE projects SET goalArea = :newCategory WHERE LOWER(goalArea) = LOWER(:oldCategory)")
    suspend fun reassignCategory(oldCategory: String, newCategory: String)

    /** Delete every sub-folder under a category — used when the category itself is removed.
     *  Case-insensitive for the same reason as [reassignCategory]: it must delete exactly the set
     *  [DeliverableDao.deleteByCategory] deletes, or one table outlives the other. */
    @Query("DELETE FROM projects WHERE LOWER(goalArea) = LOWER(:category)")
    suspend fun deleteByCategory(category: String)
}
