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

    @Query("SELECT COUNT(*) FROM entries")
    fun observeCount(): Flow<Int>
}
