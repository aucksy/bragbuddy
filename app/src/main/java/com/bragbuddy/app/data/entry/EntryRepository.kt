package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place entries are written and read. Capture is **fire-and-forget**: [capture] stores the
 * raw transcript immediately (status RAW) and returns — nothing blocks the user. It then kicks the
 * [EntryProcessor] on the application scope so the AI categorization runs off the capture path; the
 * capture sheet can finish before it completes without cancelling it.
 */
@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val processor: EntryProcessor,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    fun observeAll(): Flow<List<EntryEntity>> = entryDao.observeAll()
    fun observeCount(): Flow<Int> = entryDao.observeCount()

    /** Inbox = low-confidence / unplaceable (INBOX) + AI-failure (FAILED) entries. */
    fun observeInbox(): Flow<List<EntryEntity>> =
        entryDao.observeIn(listOf(EntryStatus.INBOX, EntryStatus.FAILED))

    fun observeInboxCount(): Flow<Int> =
        entryDao.observeCountIn(listOf(EntryStatus.INBOX, EntryStatus.FAILED))

    /** Durably store what the user said/typed, then categorize in the background. Returns the row id. */
    suspend fun capture(rawTranscript: String, source: EntrySource, occurredAt: Long? = null): Long {
        val id = entryDao.insert(
            EntryEntity(
                createdAt = System.currentTimeMillis(),
                occurredAt = occurredAt,
                source = source,
                status = EntryStatus.RAW,
                rawTranscript = rawTranscript.trim(),
            ),
        )
        appScope.launch { processor.process(id) }
        return id
    }

    /** Catch any entries left RAW by an interrupted run (called on launch). */
    fun processPending() {
        appScope.launch { processor.processPending() }
    }

    /** Re-run entries that previously failed (e.g. after an OpenRouter key is added). */
    fun reprocessFailed() {
        appScope.launch { processor.reprocessFailed() }
    }

    /** Retry one failed entry on demand (from the Inbox). */
    fun retry(id: Long) {
        appScope.launch { processor.process(id) }
    }
}
