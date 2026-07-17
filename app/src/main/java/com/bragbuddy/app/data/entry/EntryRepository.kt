package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
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
     *  [anchorProject] (folder-tap) fixes the project for this capture, and [anchorDeliverable] fixes
     *  the deliverable within it (a deliverable-tap) — the AI guesses neither. */
    suspend fun capture(
        rawTranscript: String,
        source: EntrySource,
        occurredAt: Long? = null,
        anchorProject: String? = null,
        anchorDeliverable: String? = null,
        anchorGoalArea: String? = null,
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
                anchorDeliverable = anchorDeliverable?.takeIf { it.isNotBlank() },
                // The tapped-through CATEGORY. Pinned so the two name-only anchors above can actually be
                // resolved — see EntryEntity.anchorGoalArea and CaptureActivity.EXTRA_GOAL_AREA.
                anchorGoalArea = anchorGoalArea?.takeIf { it.isNotBlank() },
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
    fun queueVoiceNote(
        audioPath: String,
        anchorProject: String? = null,
        anchorDeliverable: String? = null,
        anchorGoalArea: String? = null,
        onQueued: () -> Unit = {},
    ) {
        appScope.launch {
            entryDao.insert(
                EntryEntity(
                    createdAt = System.currentTimeMillis(),
                    source = EntrySource.VOICE,
                    status = EntryStatus.PENDING_AUDIO,
                    rawTranscript = "",
                    anchorProject = anchorProject?.takeIf { it.isNotBlank() },
                    // The deliverable anchor must survive the offline queue exactly as the project one
                    // does — the tap-in is the user's placement decision, and it would be silently lost
                    // if a capture into a deliverable happened to be made with no network.
                    anchorDeliverable = anchorDeliverable?.takeIf { it.isNotBlank() },
                    anchorGoalArea = anchorGoalArea?.takeIf { it.isNotBlank() },
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
    fun queueImageNote(
        imagePath: String,
        anchorProject: String? = null,
        anchorDeliverable: String? = null,
        anchorGoalArea: String? = null,
        onQueued: () -> Unit = {},
    ) {
        appScope.launch {
            entryDao.insert(
                EntryEntity(
                    createdAt = System.currentTimeMillis(),
                    source = EntrySource.IMAGE,
                    status = EntryStatus.PENDING_IMAGE,
                    rawTranscript = "",
                    anchorProject = anchorProject?.takeIf { it.isNotBlank() },
                    anchorDeliverable = anchorDeliverable?.takeIf { it.isNotBlank() },
                    anchorGoalArea = anchorGoalArea?.takeIf { it.isNotBlank() },
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

    /** Clear the manual category anchor of every entry pinned to a now-DELETED category (v0.31.0), so a
     *  later edit re-files it into a live category instead of freezing it in the deleted one. */
    fun clearCategoryAnchor(area: String) {
        appScope.launch { processor.clearCategoryAnchor(area) }
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
        isCarry: Boolean = false,
    ) {
        appScope.launch {
            processor.remapProjectEverywhere(old, oldArea, target, targetArea, createTargetFolder, isCarry)
        }
    }

    /** Retry one failed entry on demand (from the Inbox). */
    fun retry(id: Long) {
        appScope.launch { processor.process(id) }
    }

    /** Resolve an Inbox entry to a project in one tap (Inbox quick-confirm). No AI re-call. */
    fun resolve(id: Long, project: String, goalArea: String) {
        appScope.launch { processor.resolve(id, project, goalArea) }
    }

    /** Recategorize a filed entry: set its placement category, project **and deliverable** AND its
     *  behaviour evidence, no AI re-call (Phase 2 · fix-a-wrong-category; third axis added v0.33.0).
     *  Supersedes the old reassign/"Move". [deliverable] null = "not part of one"; set
     *  [createDeliverable] to move the win into a brand-new deliverable named in the sheet (created in
     *  the processor, under its lock — race-free and un-orphanable). */
    fun recategorize(
        id: Long,
        goalArea: String,
        project: String,
        deliverable: String?,
        demonstrates: List<String>,
        createDeliverable: Boolean = false,
    ) {
        appScope.launch {
            processor.recategorize(id, goalArea, project, deliverable, demonstrates, createDeliverable)
        }
    }

    /**
     * Recategorize and **await completion** — the same processor call as [recategorize], just not
     * fire-and-forget. The Summary-tab retag (v0.31.0) needs to know the record has actually settled
     * before it re-derives the rollup signature; a fire-and-forget write would race the re-stamp and
     * leave the corrected summary looking stale (or, worse, stamp a signature for a rollup that hadn't
     * been written yet).
     *
     * ⚠️ The work runs on the **application** scope and the caller merely awaits it. Awaiting and
     * *owning* are different things, and conflating them was a real defect: this ran on the caller's
     * scope, and the caller is `SummaryViewModel.retagAchievement`'s `viewModelScope.launch` — around a
     * LOOP over a merged card's entries. `recategorize` blocks on the processing mutex, which
     * `process()` holds across a live Groq round-trip, so a retag requested while any capture is filing
     * waits seconds. Leaving the screen in that window cancelled the loop mid-way: entry 1 moved,
     * entries 2 and 3 didn't, `wroteBack` never re-stamped the summary — a half-moved record, no error,
     * no signal. Same lesson as [renameDeliverable]: a durable mutation must outlive the screen that
     * asked for it. `async`/`await` keeps the caller's need (know when it settled) without handing it
     * the power to cancel a half-done write.
     */
    suspend fun recategorizeNow(
        id: Long,
        goalArea: String,
        project: String,
        deliverable: String?,
        demonstrates: List<String>,
        createDeliverable: Boolean = false,
    ) {
        appScope.async {
            processor.recategorize(id, goalArea, project, deliverable, demonstrates, createDeliverable)
        }.await()
    }

    // ---------------- deliverables · the third axis (v0.33.0) ----------------

    /** How many filed records are tagged to this deliverable under ([project], [area]) — decides whether
     *  a delete needs to warn. Scoped by both parents (a deliverable's name isn't an identity alone). */
    suspend fun countDeliverableReferences(name: String, project: String, area: String): Int =
        processor.countDeliverableReferences(name, project, area)

    /**
     * Rename a deliverable **and** re-tag every record of it, in one transaction (deterministic, no AI).
     *
     * Fire-and-forget on the **application** scope, not the caller's. [EntryProcessor.renameDeliverable]
     * blocks on the processing mutex, which `process()` holds across a live Groq round-trip — so a rename
     * requested while any capture is filing can wait seconds. On a ViewModel scope that wait is
     * cancellable: navigating back (or the sheet closing) killed the coroutine and the rename vanished
     * with no error, after the UI had already said it saved. Every other durable mutation here runs on
     * the app scope for exactly this reason.
     */
    fun renameDeliverable(id: Long, newName: String, description: String?) {
        appScope.launch { processor.renameDeliverable(id, newName, description) }
    }

    /** Clear the deliverable tag + anchor of every entry of a DELETED deliverable. The entries stay —
     *  only the grouping goes, so they fall back to listing plainly under their project. */
    fun clearDeliverable(name: String, project: String, area: String) {
        appScope.launch { processor.clearDeliverable(name, project, area) }
    }

    /** Clear the deliverable axis of every entry of a DELETED project (whose deliverables cascade away
     *  in `ProjectRepository`). Call alongside `ProjectRepository.delete`. */
    fun clearProjectDeliverables(project: String, area: String) {
        appScope.launch { processor.clearProjectDeliverables(project, area) }
    }

    /** Clear the deliverable axis of every entry under a DELETED category. Call alongside
     *  [clearCategoryAnchor], which the same sites already invoke. */
    fun clearCategoryDeliverables(area: String) {
        appScope.launch { processor.clearCategoryDeliverables(area) }
    }

    /** One entry by id, or null. */
    suspend fun getById(id: Long): EntryEntity? = entryDao.getById(id)

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
     *
     * [isRedo] must be true ONLY for a genuine re-record (the user starting the capture over), never
     * for an edit or an append — it decides whether the entry's original words are preserved or reset.
     * See [EntryProcessor.replace].
     */
    fun replaceText(id: Long, text: String, combineSingle: Boolean = false, isRedo: Boolean = false) {
        appScope.launch { processor.replace(id, text, combineSingle, isRedo) }
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
