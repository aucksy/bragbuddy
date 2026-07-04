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

    /** Entries pinned "always include" — fed live to the summary generator (Phase 5). */
    fun observePinned(): Flow<List<EntryEntity>> = entryDao.observePinned()

    /** Durably store what the user said/typed, then categorize in the background. Returns the row id.
     *  [anchorProject] (folder-tap) fixes the project for this capture. */
    suspend fun capture(
        rawTranscript: String,
        source: EntrySource,
        occurredAt: Long? = null,
        anchorProject: String? = null,
        combineSingle: Boolean = false,
    ): Long {
        val id = entryDao.insert(
            EntryEntity(
                createdAt = System.currentTimeMillis(),
                occurredAt = occurredAt,
                source = source,
                status = EntryStatus.RAW,
                rawTranscript = rawTranscript.trim(),
                anchorProject = anchorProject?.takeIf { it.isNotBlank() },
            ),
        )
        appScope.launch { processor.process(id, combineSingle) }
        return id
    }

    /** Catch any entries left RAW by an interrupted run (called on launch). */
    fun processPending() {
        appScope.launch { processor.processPending() }
    }

    /** Re-run entries that previously failed (e.g. after the Groq key is added). */
    fun reprocessFailed() {
        appScope.launch { processor.reprocessFailed() }
    }

    /** Rebuild/repair the running rollup from processed entries (called on launch). */
    fun reconcileRollup() {
        appScope.launch { processor.reconcileRollup() }
    }

    /** Retry one failed entry on demand (from the Inbox). */
    fun retry(id: Long) {
        appScope.launch { processor.process(id) }
    }

    /** Resolve an Inbox entry to a project in one tap (Inbox quick-confirm). No AI re-call. */
    fun resolve(id: Long, project: String, goalArea: String) {
        appScope.launch { processor.resolve(id, project, goalArea) }
    }

    /** Move a filed entry to another project + goal area (Phase 4 entry detail). No AI re-call. */
    fun reassign(id: Long, project: String, goalArea: String) {
        appScope.launch { processor.reassign(id, project, goalArea) }
    }

    /** Toggle the ★ Standout flag on an entry (Phase 4 entry detail). Serialised in the processor. */
    fun setExtra(id: Long, value: Boolean) {
        appScope.launch { processor.setExtra(id, value) }
    }

    /** Toggle the pin-for-summary flag on an entry (Phase 4 entry detail). Serialised in the processor. */
    fun setPinned(id: Long, value: Boolean) {
        appScope.launch { processor.setPinned(id, value) }
    }

    /** Delete an entry outright (Home → Delete). Routed through the processor so the running rollup
     *  contribution is dropped too (under its lock). Siblings of a split transcript are independent. */
    fun delete(id: Long) {
        appScope.launch { processor.delete(id) }
    }

    /** Delete several entries at once (multi-select bulk delete). No-op on an empty selection. */
    fun deleteMany(ids: List<Long>) {
        if (ids.isEmpty()) return
        appScope.launch { processor.deleteMany(ids) }
    }

    /**
     * Replace an entry's text and re-file it from scratch (Home → Edit, or Redo's re-record). The
     * reset + re-categorize runs inside [EntryProcessor] under its lock, so it can't be clobbered by
     * an in-flight categorization of the same row and can't leave duplicate split rows.
     */
    fun replaceText(id: Long, text: String, combineSingle: Boolean = false) {
        appScope.launch { processor.replace(id, text, combineSingle) }
    }
}
