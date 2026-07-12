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

    /**
     * Queue an **offline voice note** (Phase 7): the clip at [audioPath] is saved but couldn't be
     * transcribed (no network / transport failure). Stores a PENDING_AUDIO row so nothing spoken is
     * ever lost; [OfflineRecovery] transcribes + files it when the network returns. Fire-and-forget
     * (safe from onCleared — no suspension on the caller's side). [onQueued] runs after the insert
     * commits, so an immediate recovery kick can actually see the row.
     */
    fun queueVoiceNote(audioPath: String, anchorProject: String? = null, onQueued: () -> Unit = {}) {
        appScope.launch {
            entryDao.insert(
                EntryEntity(
                    createdAt = System.currentTimeMillis(),
                    source = EntrySource.VOICE,
                    status = EntryStatus.PENDING_AUDIO,
                    rawTranscript = "",
                    anchorProject = anchorProject?.takeIf { it.isNotBlank() },
                    audioPath = audioPath,
                ),
            )
            onQueued()
        }
    }

    /**
     * Queue an **offline image scan** (M2): the downscaled JPEG at [imagePath] is saved but couldn't
     * be read by Groq vision (no network / transport failure). Stores a PENDING_IMAGE row so nothing
     * scanned is lost; [OfflineRecovery] reads + files it when the network returns. Fire-and-forget
     * (safe from onCleared). [onQueued] runs after the insert commits, so an immediate recovery kick
     * can actually see the row. Mirrors [queueVoiceNote].
     */
    fun queueImageNote(imagePath: String, anchorProject: String? = null, onQueued: () -> Unit = {}) {
        appScope.launch {
            entryDao.insert(
                EntryEntity(
                    createdAt = System.currentTimeMillis(),
                    source = EntrySource.IMAGE,
                    status = EntryStatus.PENDING_IMAGE,
                    rawTranscript = "",
                    anchorProject = anchorProject?.takeIf { it.isNotBlank() },
                    imagePath = imagePath,
                ),
            )
            onQueued()
        }
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

    /** How many filed records carry this category name (goal-area label or behaviour tag) — decides
     *  whether to offer the category rename-remap prompt (Phase B2). */
    suspend fun countCategoryReferences(name: String): Int = processor.countCategoryReferences(name)

    /** Relabel every record from a renamed category, old → new (Phase B2 · deterministic, no AI). */
    fun renameCategoryEntries(old: String, new: String) {
        appScope.launch { processor.renameCategoryEverywhere(old, new) }
    }

    /** How many filed records are tagged to this project name under [area] — decides whether to offer
     *  the project rename-remap prompt (Phase B2b). Scoped by goal area (folders are unique by name+area). */
    suspend fun countProjectReferences(name: String, area: String): Int =
        processor.countProjectReferences(name, area)

    /** Re-tag every record of a renamed project ([old] under [oldArea]) → [target] under [targetArea]
     *  (Phase B2b · deterministic, no AI). Set [createTargetFolder] to create the destination folder
     *  first (option c · a new project). */
    fun remapProjectEntries(
        old: String,
        oldArea: String,
        target: String,
        targetArea: String,
        createTargetFolder: Boolean = false,
    ) {
        appScope.launch { processor.remapProjectEverywhere(old, oldArea, target, targetArea, createTargetFolder) }
    }

    /** Retry one failed entry on demand (from the Inbox). */
    fun retry(id: Long) {
        appScope.launch { processor.process(id) }
    }

    /** Resolve an Inbox entry to a project in one tap (Inbox quick-confirm). No AI re-call. */
    fun resolve(id: Long, project: String, goalArea: String) {
        appScope.launch { processor.resolve(id, project, goalArea) }
    }

    /** Recategorize a filed entry: set its placement category + project AND its behaviour evidence,
     *  no AI re-call (Phase 2 · fix-a-wrong-category). Supersedes the old reassign/"Move". */
    fun recategorize(id: Long, goalArea: String, project: String, demonstrates: List<String>) {
        appScope.launch { processor.recategorize(id, goalArea, project, demonstrates) }
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

    /**
     * Add a user-supplied impact/number to an already-filed win (Phase 4 · the Home "Add impact" list).
     * Delegates to the **non-destructive** [EntryProcessor.addImpact], which folds the number into the
     * bullet in COMBINE mode while keeping the win's placement, behaviours and ★/pin — and leaves it
     * untouched on an AI failure (a transient blip can't demote a good record). Fire-and-forget on the
     * app scope so the sheet can close immediately.
     */
    fun addImpact(entry: EntryEntity, impact: String) {
        val add = impact.trim()
        if (add.isEmpty()) return
        appScope.launch { processor.addImpact(entry.id, add) }
    }
}
