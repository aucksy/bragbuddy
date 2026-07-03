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

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Move every sub-folder from one category (pillar) to another — used when a category is renamed.
     *  OR IGNORE: if a folder would collide with one already under the target category, skip it rather
     *  than crash (the UI blocks renaming onto an existing category name; this is the safety net). */
    @Query("UPDATE OR IGNORE projects SET goalArea = :newCategory WHERE goalArea = :oldCategory")
    suspend fun reassignCategory(oldCategory: String, newCategory: String)

    /** Delete every sub-folder under a category — used when the category itself is removed. */
    @Query("DELETE FROM projects WHERE goalArea = :category")
    suspend fun deleteByCategory(category: String)
}
