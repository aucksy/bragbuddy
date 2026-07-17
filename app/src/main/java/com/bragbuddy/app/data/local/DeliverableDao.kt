package com.bragbuddy.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Deliverables — the third level (Category → Project → **Deliverable** → entries).
 *
 * Mirrors [ProjectDao] deliberately, including its conflict strategy: IGNORE on insert/update so a
 * duplicate name (or a rename that would collide with a sibling) is a silent no-op rather than a crash.
 *
 * ⚠️ **A silent no-op is only safe if callers check the EFFECT.** `UPDATE OR IGNORE` reports nothing —
 * no exception, no row count — so code that assumes the requested name landed and then cascades on it
 * will corrupt data (this is exactly what a v0.33.0 review caught: entries remapped into a deliverable
 * the rename never reached). `DeliverableRepository.rename` therefore pre-checks the collision AND
 * re-reads the row; the name dialogs additionally block a duplicate before it gets here.
 *
 * **Every cascade query here is scoped by BOTH parents** (`project` AND `goalArea`). A deliverable's
 * identity is `(name, project, goalArea)` — scoping by project alone would touch a same-named project's
 * deliverables under a different category.
 */
@Dao
interface DeliverableDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(deliverable: DeliverableEntity): Long

    @Update(onConflict = OnConflictStrategy.IGNORE)
    suspend fun update(deliverable: DeliverableEntity)

    @Query("SELECT * FROM deliverables ORDER BY done, sortOrder, name")
    fun observeAll(): Flow<List<DeliverableEntity>>

    @Query("SELECT * FROM deliverables WHERE id = :id")
    suspend fun getById(id: Long): DeliverableEntity?

    /** One deliverable by its full identity — case-insensitive, like every other name lookup here. */
    @Query(
        "SELECT * FROM deliverables WHERE LOWER(name) = LOWER(:name) AND LOWER(project) = LOWER(:project) " +
            "AND LOWER(goalArea) = LOWER(:area) LIMIT 1",
    )
    suspend fun getByIdentity(name: String, project: String, area: String): DeliverableEntity?

    @Query("UPDATE deliverables SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM deliverables WHERE id = :id")
    suspend fun deleteById(id: Long)

    // ---------------- cascades ----------------

    /** Follow a **category rename**: re-home every deliverable under it (mirrors
     *  [ProjectDao.reassignCategory], which re-homes the projects in the same breath). OR IGNORE so a
     *  collision with an existing deliverable under the target category is skipped, not a crash. */
    @Query("UPDATE OR IGNORE deliverables SET goalArea = :newCategory WHERE LOWER(goalArea) = LOWER(:oldCategory)")
    suspend fun reassignCategory(oldCategory: String, newCategory: String)

    /** Follow a **category delete**: a deliverable can't outlive the category its project sat under
     *  (mirrors [ProjectDao.deleteByCategory], which removes those projects). */
    @Query("DELETE FROM deliverables WHERE LOWER(goalArea) = LOWER(:category)")
    suspend fun deleteByCategory(category: String)

    /** Follow a **project rename / re-home** (the v0.19.0 rename-remap): the deliverable's own name is
     *  unchanged — only its parent's identity moved — so entries' `deliverable` labels need no rewrite.
     *  OR IGNORE guards the case where the target project already owns a same-named deliverable. */
    @Query(
        "UPDATE OR IGNORE deliverables SET project = :newProject, goalArea = :newArea " +
            "WHERE LOWER(project) = LOWER(:oldProject) AND LOWER(goalArea) = LOWER(:oldArea)",
    )
    suspend fun remapProject(oldProject: String, oldArea: String, newProject: String, newArea: String)

    /** Follow a **project delete**: its deliverables go with it. The entries do NOT — they keep their
     *  labels and surface under the category, exactly as they already do when a project is deleted. */
    @Query("DELETE FROM deliverables WHERE LOWER(project) = LOWER(:project) AND LOWER(goalArea) = LOWER(:area)")
    suspend fun deleteByProject(project: String, area: String)

    // ---------------- backup (mirrors ProjectDao) ----------------

    /** All deliverables, once (backup export). */
    @Query("SELECT * FROM deliverables ORDER BY id ASC")
    suspend fun getAllOnce(): List<DeliverableEntity>

    /** Wipe (restore replaces them wholesale). */
    @Query("DELETE FROM deliverables")
    suspend fun deleteAll()

    /** Insert many preserving ids (restore). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(deliverables: List<DeliverableEntity>)
}
