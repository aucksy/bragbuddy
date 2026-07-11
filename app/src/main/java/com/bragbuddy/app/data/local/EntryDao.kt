package com.bragbuddy.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    /** Inserting the raw transcript is the durable first step of capture; returns the new row id. */
    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Query("SELECT * FROM entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    @Query("SELECT * FROM entries WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: EntryStatus): Flow<List<EntryEntity>>

    /** The Inbox surface: low-confidence / unplaceable (INBOX) and AI-failure (FAILED) entries. */
    @Query("SELECT * FROM entries WHERE status IN (:statuses) ORDER BY createdAt DESC")
    fun observeIn(statuses: List<EntryStatus>): Flow<List<EntryEntity>>

    @Query("SELECT COUNT(*) FROM entries WHERE status IN (:statuses)")
    fun observeCountIn(statuses: List<EntryStatus>): Flow<Int>

    /** All entries still awaiting AI processing (drained on launch in case a run was interrupted). */
    @Query("SELECT * FROM entries WHERE status = :status ORDER BY createdAt ASC")
    suspend fun listByStatus(status: EntryStatus = EntryStatus.RAW): List<EntryEntity>

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    /** The user's existing routine-work labels, most-used first (AI-1 · {{ROUTINE_TYPES}}). Fed to the
     *  categorizer so the model reuses a fitting label instead of coining a near-duplicate variant. */
    @Query(
        "SELECT routineType FROM entries WHERE routineType IS NOT NULL AND routineType != '' " +
            "GROUP BY routineType ORDER BY COUNT(*) DESC LIMIT 20",
    )
    suspend fun distinctRoutineTypes(): List<String>

    /** Toggle the ★ Standout flag (Phase 4 entry detail). Targeted update — can't race a re-file. */
    @Query("UPDATE entries SET isExtra = :value WHERE id = :id")
    suspend fun setExtra(id: Long, value: Boolean)

    /** Toggle the pin-for-summary flag (Phase 4 entry detail; consumed by the Phase 5 summary). */
    @Query("UPDATE entries SET isPinned = :value WHERE id = :id")
    suspend fun setPinned(id: Long, value: Boolean)

    /** Relabel every entry filed under a renamed goal-area category (Phase B2 · category rename-remap).
     *  Case-insensitive match on the OLD name. NB: the param is `newName`, NOT `new` — Room generates
     *  Java from this DAO and `new` is a Java reserved word (it produced an invalid `EntryDao_Impl.java`).
     *  The `demonstrates` JSON list (behaviour tags) is a list column SQL can't touch — the processor
     *  rewrites those rows in Kotlin. */
    @Query("UPDATE entries SET goalCategory = :newName WHERE LOWER(goalCategory) = LOWER(:old)")
    suspend fun updateGoalCategory(old: String, newName: String)

    /** How many filed records are tagged to a project [name] **under goal area [area]** — decides
     *  whether to offer the project rename-remap prompt (Phase B2b). Scoped by goal area because a
     *  folder is unique by (name, goalArea): the same project name can live under two goal areas, and
     *  only the records of the renamed one should be counted. Case-insensitive. */
    @Query("SELECT COUNT(*) FROM entries WHERE LOWER(project) = LOWER(:name) AND LOWER(goalCategory) = LOWER(:area)")
    suspend fun countProjectReferences(name: String, area: String): Int

    /** Re-tag every record of the renamed project ([old] under goal area [oldArea]) to [newName] under
     *  [newArea] (Phase B2b · rename-remap). Scoped by the OLD goal area so a same-named folder under a
     *  different goal area is never touched; sets the goal-area label to [newArea] so records follow the
     *  folder even when its category changed (carry) or when reassigned to another goal area. NB: the
     *  params are `newName`/`newArea`, NOT `new` — Room generates Java and `new` is a reserved word. */
    @Query(
        "UPDATE entries SET project = :newName, goalCategory = :newArea " +
            "WHERE LOWER(project) = LOWER(:old) AND LOWER(goalCategory) = LOWER(:oldArea)",
    )
    suspend fun remapProjectScoped(old: String, oldArea: String, newName: String, newArea: String)

    /** Follow the rename in the deterministic anchor too, scoped to the same (old name, old goal area)
     *  so a later edit re-files back into the new folder (Phase B2b). Case-insensitive. */
    @Query(
        "UPDATE entries SET anchorProject = :newName " +
            "WHERE LOWER(anchorProject) = LOWER(:old) AND LOWER(goalCategory) = LOWER(:oldArea)",
    )
    suspend fun remapAnchorScoped(old: String, oldArea: String, newName: String)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM entries WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM entries")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int

    /** Count excluding one status — "is there real logged content?" checks that must not let a
     *  still-untranscribed offline voice note (PENDING_AUDIO) count as data worth backing up. */
    @Query("SELECT COUNT(*) FROM entries WHERE status != :excluded")
    suspend fun countExcluding(excluded: EntryStatus): Int

    /** Every non-null clip path referenced by ANY row. The offline-recovery orphan sweep treats these
     *  as "owned" so a clip that already drained (its row left PENDING_AUDIO but a crash skipped the
     *  file delete) is never re-adopted into a DUPLICATE entry. */
    @Query("SELECT audioPath FROM entries WHERE audioPath IS NOT NULL AND audioPath != ''")
    suspend fun allAudioPaths(): List<String>

    /** Rows that still reference a clip but have already LEFT the queue (status != PENDING_AUDIO): a
     *  drained note whose file survived a crash before its delete. Recovery deletes the file and clears
     *  the dangling reference. */
    @Query("SELECT * FROM entries WHERE audioPath IS NOT NULL AND audioPath != '' AND status != :pending")
    suspend fun settledWithAudio(pending: EntryStatus): List<EntryEntity>

    /** Null out a clip reference from the row(s) carrying it (the drained note + any split sibling that
     *  inherited the path) once its file is gone. */
    @Query("UPDATE entries SET audioPath = NULL WHERE audioPath = :path")
    suspend fun clearAudioPath(path: String)

    /** Entries the user pinned "always include" — fed live to the Phase 5 summary ({{PINNED}}). */
    @Query("SELECT * FROM entries WHERE isPinned = 1 ORDER BY createdAt DESC")
    fun observePinned(): Flow<List<EntryEntity>>

    /** The whole raw log, once (Phase 6 backup export). */
    @Query("SELECT * FROM entries ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<EntryEntity>

    /** Wipe the log (Phase 6 restore replaces it wholesale). */
    @Query("DELETE FROM entries")
    suspend fun deleteAll()

    /** Insert many rows preserving their ids (Phase 6 restore). */
    @Insert
    suspend fun insertAll(entries: List<EntryEntity>)
}
