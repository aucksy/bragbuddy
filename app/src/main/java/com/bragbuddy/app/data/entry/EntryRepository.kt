package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place entries are written and read. Capture is **fire-and-forget**: [capture] stores the
 * raw transcript immediately (status RAW) and returns — nothing blocks the user. AI categorization
 * (Phase 2) will later read RAW entries and fill in the derived fields.
 */
@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
) {
    fun observeAll(): Flow<List<EntryEntity>> = entryDao.observeAll()
    fun observeCount(): Flow<Int> = entryDao.observeCount()

    /** Durably store what the user said/typed. Returns the new row id. Never called with blank text. */
    suspend fun capture(rawTranscript: String, source: EntrySource, occurredAt: Long? = null): Long =
        entryDao.insert(
            EntryEntity(
                createdAt = System.currentTimeMillis(),
                occurredAt = occurredAt,
                source = source,
                status = EntryStatus.RAW,
                rawTranscript = rawTranscript.trim(),
            ),
        )
}
