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

    /** Toggle the ★ Standout flag (Phase 4 entry detail). Targeted update — can't race a re-file. */
    @Query("UPDATE entries SET isExtra = :value WHERE id = :id")
    suspend fun setExtra(id: Long, value: Boolean)

    /** Toggle the pin-for-summary flag (Phase 4 entry detail; consumed by the Phase 5 summary). */
    @Query("UPDATE entries SET isPinned = :value WHERE id = :id")
    suspend fun setPinned(id: Long, value: Boolean)

    /** Relabel every entry filed under a renamed goal-area category (Phase B2 · category rename-remap).
     *  Case-insensitive match on the OLD name (LOWER() rather than COLLATE on the bind parameter). The
     *  `demonstrates` JSON list (behaviour tags) is a list column SQL can't touch — the processor
     *  rewrites those rows in Kotlin. */
    @Query("UPDATE entries SET goalCategory = :new WHERE LOWER(goalCategory) = LOWER(:old)")
    suspend fun updateGoalCategory(old: String, new: String)

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
