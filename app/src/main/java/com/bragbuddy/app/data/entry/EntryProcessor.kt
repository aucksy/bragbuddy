package com.bragbuddy.app.data.entry

import androidx.room.withTransaction
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.CategorizeRequest
import com.bragbuddy.app.data.ai.CategorizedEntry
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.BragBuddyDatabase
import com.bragbuddy.app.data.local.DeliverableDao
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT
import com.bragbuddy.app.data.local.ProjectDao
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.rollup.RollupStore
import com.bragbuddy.app.data.rollup.toRollupItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs the **daily categorizer** (BragBuddy-System-Prompt PART A) over a captured RAW entry and
 * files the result. Runs off the capture path (fire-and-forget), so capture is never blocked.
 *
 * FIRM INVARIANTS honored here:
 *  - **Never lose an entry.** The raw transcript is already stored; on any AI/parse failure the row
 *    keeps its transcript and is routed to the Inbox (status FAILED). On success the transcript is
 *    still preserved alongside the cleaned bullet.
 *  - **Confidence < ~0.6 → Inbox**, and an "Inbox" placement from the model → Inbox, regardless of
 *    what else came back (Build Brief § "the Inbox is how the app asks — without interrupting").
 *  - One transcript may describe several things: the model splits them; the original row takes the
 *    first, and the rest are inserted as sibling rows carrying the same transcript for provenance.
 */
@Singleton
class EntryProcessor @Inject constructor(
    private val entryDao: EntryDao,
    private val projectDao: ProjectDao,
    private val deliverableDao: DeliverableDao,
    private val frameworkStore: FrameworkStore,
    private val aiProvider: AiProvider,
    private val settings: SettingsStore,
    private val rollupStore: RollupStore,
    private val db: BragBuddyDatabase,
) {

    // Serialize all processing so two callers (capture's kick + a launch-time drain during a config
    // change) can never both claim the same row and double-insert its split siblings. Also friendlier
    // to the free-tier rate limit — entries process one at a time. Every running-rollup write also
    // happens under this lock, so a targeted put/remove can't race a concurrent re-file (Phase 5).
    private val mutex = Mutex()

    /**
     * Keep the running rollup ([RollupStore]) in step with one row's current state — a filed
     * contribution is put (upsert by id), anything else is removed. Called at every mutation point
     * below, always under [mutex]. Best-effort: a rollup write must never break the entry write
     * (the launch [reconcileRollup] repairs any gap), so it's wrapped in runCatching.
     */
    private suspend fun syncRollup(entry: EntryEntity) {
        runCatching {
            val item = entry.toRollupItem()
            if (item != null) rollupStore.put(item) else rollupStore.remove(entry.id)
        }
    }

    /**
     * Process a single entry by id. Idempotent and safe to call from several places: it re-reads the
     * row inside the lock and only processes work that is still awaiting it (RAW) or that previously
     * failed and can be retried (FAILED). A PROCESSED/INBOX row is skipped, so nothing is split twice.
     */
    suspend fun process(id: Long, combineSingle: Boolean = false) {
        mutex.withLock {
            val entry = entryDao.getById(id) ?: return@withLock
            if (entry.status != EntryStatus.RAW && entry.status != EntryStatus.FAILED) return@withLock
            // Backstop: ANY throw (a malformed key char in a header, a DataStore read error, etc.)
            // must still leave the entry visible in the Inbox rather than crashing the fire-and-forget
            // scope and stranding the row as RAW (which would re-throw and crash-loop on next launch).
            // Combine mode (add-a-number flow) files as ONE merged entry — never split.
            runCatching { if (combineSingle) refileSingle(entry, combineSingle = true) else processEntry(entry) }
                .onFailure { runCatching { entryDao.update(entry.copy(status = EntryStatus.FAILED)) } }
        }
    }

    /**
     * Replace ONE entry's text and re-file it (Home → Edit, or Redo's re-record). Deliberately does
     * NOT split or touch sibling rows: an edit fixes a single entry, so re-filing it must never
     * delete/duplicate the other items from the same original capture. Runs under the processing lock
     * so it can't be clobbered by an in-flight categorization of the same row. No-op if the row is gone.
     *
     * [isRedo] distinguishes the two callers of this one path, which mean opposite things for the
     * user's original words (v0.32.0):
     *  - an **edit** (false) mutates the existing capture → snapshot [EntryEntity.originalTranscript]
     *    once, so what the user actually said survives; the editor is seeded from the AI's *bullet*, so
     *    without this the edit would silently overwrite their words with the AI's.
     *  - a **redo** (true) is starting over → the fresh recording BECOMES the original, so the snapshot
     *    resets to null and the scrapped attempt is let go (owner's call, 2026-07-17).
     */
    suspend fun replace(id: Long, text: String, combineSingle: Boolean = false, isRedo: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        mutex.withLock {
            val e = entryDao.getById(id) ?: return@withLock
            // A manually-set ★ Standout is the user's call and must survive an edit (mirrors isPinned,
            // which is already preserved). Re-apply it after the AI re-file, which would otherwise reset it.
            val keepExtra = e.isExtra
            // Capture the original words ONCE, from the value about to be overwritten, and never again.
            val keepOriginal = OriginalTranscript.next(
                current = e.rawTranscript, existing = e.originalTranscript, incoming = clean, isRedo = isRedo,
            )
            // NOTE: `anchorProject` / `anchorGoalArea` are deliberately NOT reset below. A manual
            // placement is the user's call and must survive an edit for exactly the same reason ★ does —
            // prepare() re-reads them and applyCategorized() re-applies them, so the AI's fresh guess is
            // overridden rather than allowed to revert the correction. Behaviours (`demonstrates`) DO
            // re-derive: they are a property of the TEXT, which just changed. Placement is structural.
            val reset = e.copy(
                rawTranscript = clean,
                originalTranscript = keepOriginal,
                status = EntryStatus.RAW,
                occurredAt = null,
                bullet = null,
                project = null,
                goalCategory = null,
                // Cleared with the other two placement axes, not kept: refileSingle re-derives it from
                // `anchorDeliverable` (which, like the other anchors, deliberately survives the reset).
                // Without this a row whose re-file FAILS keeps a stale tag it can no longer justify —
                // and if its deliverable was deleted meanwhile, the anchor re-validation drops it and
                // this row would otherwise still claim the old one.
                deliverable = null,
                demonstrates = emptyList(),
                isExtra = false,
                impact = null,
                routine = false,
                routineType = null,
                metric = null,
                confidence = null,
                suggestedProjects = emptyList(),
            )
            entryDao.update(reset)
            // The row is RAW again (no placement) until re-filed — drop its stale rollup contribution;
            // refileSingle re-adds the fresh one.
            rollupStore.remove(reset.id)
            runCatching {
                refileSingle(reset, combineSingle)
                if (keepExtra) {
                    entryDao.setExtra(reset.id, true)
                    // Re-apply the ★ into the rollup highlight (refileSingle synced with the AI's flag).
                    entryDao.getById(reset.id)?.let { syncRollup(it) }
                }
            }.onFailure {
                runCatching {
                    entryDao.update(reset.copy(status = EntryStatus.FAILED))
                    rollupStore.remove(reset.id)
                }
            }
        }
    }

    /**
     * Add a user-supplied impact/number to an already-filed win (Phase 4 · the Home "Add impact" list).
     * **Non-destructive** — unlike [replace], the row is NOT reset to RAW: it stays PROCESSED with its
     * placement, behaviours and ★/pin intact, and only the **bullet** is re-written to fold in the number
     * (COMBINE mode) with the **metric** captured. On ANY AI failure / empty result the win is left
     * EXACTLY as it was — so a transient blip can never demote a good record to a bullet-less Inbox row.
     * The number always comes from the user ([impactText]); the AI only merges it, inventing nothing.
     * Only enriches a PROCESSED row; runs under the processing [mutex].
     */
    suspend fun addImpact(id: Long, impactText: String) {
        val add = impactText.trim()
        if (add.isEmpty()) return
        mutex.withLock {
            val e = entryDao.getById(id) ?: return@withLock
            if (e.status != EntryStatus.PROCESSED) return@withLock
            val combined = "${e.rawTranscript.trim()} $add".trim()
            runCatching {
                // Anchor to the current project so the merged bullet has the right context; we keep the
                // existing placement/behaviours regardless (only bullet + metric + impact change).
                val anchor = e.project?.takeIf { it.isNotBlank() && !it.equals(INBOX_LABEL, ignoreCase = true) }
                val p = prepare(e.copy(rawTranscript = combined, anchorProject = anchor ?: e.anchorProject), combineSingle = true)
                val c = aiProvider.categorize(p.request).getOrNull()?.entries?.firstOrNull()
                val newBullet = c?.bullet?.ifBlank { null }
                if (c != null && newBullet != null) {
                    val updated = e.copy(
                        rawTranscript = combined,
                        // An append LOSES nothing (`combined` is the transcript plus the user's number),
                        // so the rule below deliberately snapshots NOTHING here — keeping the full text
                        // readable as the user's own words. It still runs, rather than being skipped, so
                        // an already-snapshotted original is carried through untouched (v0.32.0).
                        originalTranscript = OriginalTranscript.next(
                            current = e.rawTranscript, existing = e.originalTranscript,
                            incoming = combined, isRedo = false,
                        ),
                        bullet = newBullet,
                        metric = c.metric?.takeIf { it.isNotBlank() } ?: e.metric,
                        impact = c.impact,
                    )
                    entryDao.update(updated)
                    syncRollup(updated)
                }
                // else: couldn't produce a clean merged bullet → leave the win untouched (retryable).
            }
        }
    }

    /**
     * Resolve an Inbox entry in one tap (Inbox → quick-confirm). Assigns the [project] the user
     * picked and files it — no AI re-call: the cleaned bullet, behaviours and impact the model
     * already produced are kept; only the placement (project + goal area) is set and the row goes
     * PROCESSED. Runs under the lock and only touches an unresolved row (INBOX / FAILED), so it can't
     * race an in-flight categorization. Assigning a real project also records it as the [anchorProject]
     * so a later edit re-files back into the same folder.
     *
     * [project] blank or [OUTSIDE_PROJECT] means "no named project" (kept under [goalArea] only).
     */
    suspend fun resolve(id: Long, project: String, goalArea: String) {
        mutex.withLock {
            val e = entryDao.getById(id) ?: return@withLock
            if (e.status != EntryStatus.INBOX && e.status != EntryStatus.FAILED) return@withLock
            val clean = project.trim()
            val isOutside = clean.isBlank() || clean.equals(OUTSIDE_PROJECT, ignoreCase = true)
            val area = goalArea.trim().ifBlank { e.goalCategory?.takeIf { it.isNotBlank() } ?: INBOX_LABEL }
            // The Inbox's quick-resolve has no deliverable picker, so the tag has to be checked rather
            // than asked about: an entry CAN reach the Inbox still carrying one (a tap-in capture is
            // anchored — including its deliverable — but still goes FAILED if the AI was unreachable, and
            // FAILED rows are resolvable). Keep it only where it genuinely exists under the project the
            // user just chose; resolving somewhere else leaves it pointing at nothing.
            val keptDeliverable = e.deliverable?.takeIf { !isOutside && deliverableExists(it, clean, area) }
            val updated = e.copy(
                status = EntryStatus.PROCESSED,
                // A FAILED / empty-result row has no cleaned bullet; fall back to its raw transcript so
                // a resolved entry still appears in the generated summary (the rollup skips bullet-less
                // rows). Rows that already have a bullet keep it.
                bullet = e.bullet?.takeIf { it.isNotBlank() } ?: e.rawTranscript.trim().ifBlank { null },
                project = if (isOutside) OUTSIDE_PROJECT else clean,
                goalCategory = area,
                deliverable = keptDeliverable,
                // Deliberately NOT `= keptDeliverable`: the resolve is a decision about the project and
                // category (which is why both of those anchor below), but the sheet never asked about the
                // deliverable — so an existing pin is carried and a nonexistent one is never invented.
                // v0.34.0's AI can set `deliverable` with no pin, and silently promoting that to a
                // user-made decision is the same mistake `remapAnchorScoped` guards against.
                anchorDeliverable = e.anchorDeliverable?.takeIf { keptDeliverable != null },
                // The user's resolve is a DECISION and must survive a later edit/redo (which resets the
                // row to RAW and re-runs the categorizer). Anchor BOTH axes — including the Outside
                // sentinel, which previously anchored nothing and so let the AI silently re-guess a
                // placement the user had already corrected (v0.31.0).
                anchorProject = if (isOutside) OUTSIDE_PROJECT else clean,
                anchorGoalArea = area,
                confidence = 1.0,
                suggestedProjects = emptyList(),
            )
            entryDao.update(updated)
            syncRollup(updated)
        }
    }

    /**
     * **Recategorize** a filed entry with NO AI (Phase 2 · fix-a-wrong-category) — the entry-detail
     * "Recategorize" action, which supersedes the old "Move" (that changed only the placement). Sets, in
     * one atomic update under the [mutex]:
     *  - its **placement category** ([goalArea] — a GOAL_AREA / DEVELOPMENT pillar) and its **project**
     *    within that category ([project]: a folder name, or blank / [OUTSIDE_PROJECT] = "no project"), and
     *  - its **behaviour evidence** ([demonstrates] — the checked behaviour pillars; *replaces* the old
     *    tags with these canonical names).
     *
     * No AI re-call: the cleaned bullet / impact / metric the model produced are kept; only the placement
     * + evidence change and the row stays PROCESSED. Works on a PROCESSED / INBOX / FAILED row; skips a
     * still-processing RAW row and an offline PENDING_AUDIO/PENDING_IMAGE row (let those finish first). A named project
     * becomes the [anchorProject] so a later edit re-files here. A blank [goalArea] falls back to the
     * entry's current one (then "Inbox") — the sheet always sends a real category, so that's a defensive
     * floor only; keeping a real goal area is what lets the entry's evidence reach the summary rollup.
     *
     * [deliverable] is the third axis (v0.33.0): the deliverable within [project], or null for "not part
     * of one" — a normal state, not an absence, so there is no sentinel to pass. It is **validated, not
     * trusted**: a deliverable only exists inside a specific (project, goalArea), so one that isn't
     * really there is dropped rather than written as a tag pointing at nothing. This is the owner's
     * "move a win to any level, anywhere" path — every surface that can move an entry routes here.
     *
     * [createDeliverable] moves a win into a **brand-new** deliverable, named right in the move sheet
     * (the owner's "existing or new"). The create happens HERE, under the [mutex], for the same two
     * reasons [remapProjectEverywhere]'s `createTargetFolder` does: it can't be orphaned by the sheet
     * dismissing, and — the real point — it removes the race. Creating from the UI and then filing would
     * be two hops, and an Apply that beat the insert would hit the validation above and **silently drop
     * the tag**, leaving the user looking at a win that ignored the deliverable they just made.
     */
    suspend fun recategorize(
        id: Long,
        goalArea: String,
        project: String,
        deliverable: String?,
        demonstrates: List<String>,
        createDeliverable: Boolean = false,
    ) {
        mutex.withLock {
            val e = entryDao.getById(id) ?: return@withLock
            if (e.status == EntryStatus.RAW || e.status == EntryStatus.PENDING_AUDIO ||
                e.status == EntryStatus.PENDING_IMAGE) return@withLock
            val clean = project.trim()
            val isOutside = clean.isBlank() || clean.equals(OUTSIDE_PROJECT, ignoreCase = true)
            val area = goalArea.trim().ifBlank { e.goalCategory?.takeIf { it.isNotBlank() } ?: INBOX_LABEL }
            val cleanDemos = demonstrates.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            // Resolve the deliverable against the DESTINATION — never trust the name that came in. A
            // deliverable exists only inside one (project, goalArea), so "no project" can't have one, and
            // a stale pick (deleted since, or belonging to the project the user just moved away from)
            // resolves to none.
            val cleanDeliverable: String? = run {
                val wanted = deliverable?.trim()?.takeIf { it.isNotBlank() } ?: return@run null
                if (isOutside || area.isBlank()) return@run null
                // Match case-INSENSITIVELY and adopt the stored row's own spelling. The unique index is
                // case-sensitive while every lookup here is not, so a "+ New" named "Alpha" beside an
                // existing "alpha" would insert a TWIN the UI can't tell apart — Home would render two
                // headers, one of them permanently empty (found in the v0.33.0 accuracy assessment; the
                // dedicated create dialogs block this, the move sheets' inline "+ New" didn't).
                deliverableDao.getByIdentity(wanted, clean, area)?.let { return@run it.name }
                if (!createDeliverable) return@run null
                runCatching {
                    deliverableDao.insert(
                        DeliverableEntity(
                            name = wanted, project = clean, goalArea = area,
                            createdAt = System.currentTimeMillis(),
                        ),
                    )
                }
                // Re-read rather than assume: the insert IGNOREs on conflict, so only a row that really
                // landed may be tagged — otherwise the entry would point at nothing.
                deliverableDao.getByIdentity(wanted, clean, area)?.name
            }
            val updated = e.copy(
                status = EntryStatus.PROCESSED,
                // A bullet-less row (recategorized straight from FAILED) falls back to its transcript so
                // it isn't silently dropped from the summary rollup.
                bullet = e.bullet?.takeIf { it.isNotBlank() } ?: e.rawTranscript.trim().ifBlank { null },
                project = if (isOutside) OUTSIDE_PROJECT else clean,
                goalCategory = area,
                deliverable = cleanDeliverable,
                // Anchor ALL THREE axes so this correction survives a later edit/redo — see resolve()
                // above. The deliverable anchors unconditionally here (unlike in resolve) because this
                // sheet DID ask: whatever the user picked, including "none", is their decision, and
                // v0.34.0's AI must not re-guess over it.
                anchorProject = if (isOutside) OUTSIDE_PROJECT else clean,
                anchorGoalArea = area,
                anchorDeliverable = cleanDeliverable,
                demonstrates = cleanDemos,
                confidence = 1.0,
                suggestedProjects = emptyList(),
            )
            entryDao.update(updated)
            syncRollup(updated)
        }
    }

    /**
     * Toggle the ★ Standout / pin flags **under the processing lock** so a targeted column write can't
     * be clobbered by a concurrent full-row re-file (reassign/resolve/replace read-modify-write the
     * whole row; serialising here means the flag and the placement never race to a lost update).
     */
    suspend fun setExtra(id: Long, value: Boolean) = mutex.withLock {
        entryDao.setExtra(id, value)
        // The ★ flag rides in the rollup highlight, so refresh this row's contribution.
        entryDao.getById(id)?.let { syncRollup(it) }
    }

    // Pin is NOT part of the rollup — pinned items are fed to the summary LIVE (a bounded query), so
    // a pin toggle needs no rollup write; the summary screen's input signature picks it up directly.
    suspend fun setPinned(id: Long, value: Boolean) = mutex.withLock { entryDao.setPinned(id, value) }

    /**
     * Delete an entry and drop its rollup contribution (routed here from the repository so the running
     * rollup can't be left stale by a raw DAO delete). Under the lock so it can't race a re-file.
     * A queued offline voice note's clip / image scan's file is deleted with its row (no orphans).
     */
    suspend fun delete(id: Long) = mutex.withLock {
        deleteQueuedFilesOf(entryDao.getById(id))
        rollupStore.remove(id)
        entryDao.deleteById(id)
    }

    /** Bulk delete (multi-select). Drops each rollup contribution, then removes the rows. */
    suspend fun deleteMany(ids: List<Long>) = mutex.withLock {
        ids.forEach { id ->
            deleteQueuedFilesOf(entryDao.getById(id))
            rollupStore.remove(id)
        }
        entryDao.deleteByIds(ids)
    }

    /** Remove a queued voice note's clip and/or image scan's file when its row goes away. Best-effort. */
    private fun deleteQueuedFilesOf(entry: EntryEntity?) {
        entry?.audioPath?.takeIf { it.isNotBlank() }?.let { runCatching { java.io.File(it).delete() } }
        entry?.imagePath?.takeIf { it.isNotBlank() }?.let { runCatching { java.io.File(it).delete() } }
    }

    /** Null out a drained voice clip's dangling path reference (offline-recovery cleanup) **under the
     *  processing mutex**, so it can't race — and be reinstated by — a concurrent full-row re-file of
     *  the same row. The file itself is deleted by the caller; this only clears the DB reference. */
    suspend fun clearDrainedClipPath(path: String) = mutex.withLock { entryDao.clearAudioPath(path) }

    /** Image-queue twin of [clearDrainedClipPath] — clears a drained scan's dangling image reference
     *  under the processing mutex. */
    suspend fun clearDrainedImagePath(path: String) = mutex.withLock { entryDao.clearImagePath(path) }

    /**
     * Rebuild the running rollup from the current PROCESSED entries — the launch-time self-heal
     * (also seeds it for the v0.13 upgrade, whose existing entries predate the rollup). This reads the
     * bounded entry log ONCE, off the summary path, to build/repair the running state — the summary
     * itself still only ever reads the rollup, never the log. Under the lock so no incremental write
     * interleaves.
     */
    suspend fun reconcileRollup() = mutex.withLock { runCatching { reconcileLocked() } }

    /** The reconcile body WITHOUT taking the lock — for callers that already hold it (Mutex isn't
     *  reentrant, so [reconcileRollup] calling this directly would deadlock a lock-holder). */
    private suspend fun reconcileLocked() {
        val processed = entryDao.listByStatus(EntryStatus.PROCESSED)
        rollupStore.replaceAll(processed.mapNotNull { it.toRollupItem() })
    }

    /**
     * Run a wholesale restore [replace] (delete+reinsert the log/folders + rewrite the stores) UNDER
     * the processing lock, then rebuild the rollup — so no in-flight categorization can interleave with
     * the replace (which would leak a pre-restore capture into the restored data), and the derived
     * rollup is rebuilt from exactly the restored entries. [replace] should be atomic itself (a Room
     * transaction) so a mid-restore failure can't leave the log half-wiped.
     */
    suspend fun runRestore(replace: suspend () -> Unit) = mutex.withLock {
        replace()
        runCatching { reconcileLocked() }
    }

    /**
     * Commit the outcome of an offline voice note's recovery attempt with a **compare-and-swap
     * guard under the processing mutex**: the row must still exist, still be PENDING_AUDIO, and
     * still reference the same clip. Returns false when the row was deleted or replaced in the
     * meantime (a Drive restore re-inserts pending rows with fresh ids; a stale-id full-row update
     * here could otherwise overwrite a restored entry, or store the transcript nowhere) — the
     * caller must then keep the audio file so the surviving row can be drained on the next pass.
     */
    suspend fun commitPendingAudio(
        id: Long,
        expectedAudioPath: String?,
        transform: (EntryEntity) -> EntryEntity,
    ): Boolean = mutex.withLock {
        val e = entryDao.getById(id) ?: return@withLock false
        if (e.status != EntryStatus.PENDING_AUDIO || e.audioPath != expectedAudioPath) return@withLock false
        entryDao.update(transform(e))
        true
    }

    /**
     * Image-queue twin of [commitPendingAudio]: commit the outcome of an offline image scan's recovery
     * attempt with the same compare-and-swap guard — the row must still exist, still be PENDING_IMAGE,
     * and still reference the same image file. Returns false when it was deleted/replaced meanwhile (a
     * Drive restore re-inserts pending rows with fresh ids) so the caller keeps the file for the next
     * pass.
     */
    suspend fun commitPendingImage(
        id: Long,
        expectedImagePath: String?,
        transform: (EntryEntity) -> EntryEntity,
    ): Boolean = mutex.withLock {
        val e = entryDao.getById(id) ?: return@withLock false
        if (e.status != EntryStatus.PENDING_IMAGE || e.imagePath != expectedImagePath) return@withLock false
        entryDao.update(transform(e))
        true
    }

    /**
     * How many filed records still carry a category name (Phase B2 · category rename-remap) — either
     * as their goal-area label or as a behaviour tag. Read-only, so no lock is taken; it just decides
     * whether to offer the relabel prompt (and shows the count). Case-insensitive.
     */
    suspend fun countCategoryReferences(name: String): Int {
        val n = name.trim()
        if (n.isEmpty()) return 0
        return entryDao.getAllOnce().count { e ->
            e.goalCategory?.equals(n, ignoreCase = true) == true ||
                e.demonstrates.any { it.equals(n, ignoreCase = true) }
        }
    }

    /**
     * Deterministically relabel every record from a renamed category ([old] → [new]) — NO AI, instant,
     * reversible (rename back). Updates the goal-area label ([EntryDao.updateGoalCategory]) AND rewrites
     * behaviour tags in the `demonstrates` JSON list (SQL can't touch a list column), all under the
     * processing [mutex] so it can't race a categorization, then rebuilds the rollup so the summary
     * follows the new name. Folders already cascade separately (v0.9.0 `renameCategory`).
     */
    suspend fun renameCategoryEverywhere(old: String, new: String) = mutex.withLock {
        val o = old.trim()
        val nw = new.trim()
        if (o.isEmpty() || nw.isEmpty() || o.equals(nw, ignoreCase = true)) return@withLock
        runCatching {
            // Goal-area label (SQL, case-insensitive on the old name).
            entryDao.updateGoalCategory(o, nw)
            // The MANUAL category anchor must follow the rename too (v0.31.0) — otherwise the entry's
            // next edit re-files it under the OLD category name, resurrecting a category the user
            // renamed and orphaning the record from the framework.
            entryDao.updateAnchorGoalArea(o, nw)
            // Behaviour tags — re-read fresh rows (goalCategory already updated) and rewrite any
            // `demonstrates` list containing the old name; dedupe in case the new name was already present.
            entryDao.getAllOnce().forEach { e ->
                if (e.demonstrates.any { it.equals(o, ignoreCase = true) }) {
                    val rewritten = e.demonstrates.map { if (it.equals(o, ignoreCase = true)) nw else it }.distinct()
                    entryDao.update(e.copy(demonstrates = rewritten))
                }
            }
            reconcileLocked()
        }
        Unit
    }

    /**
     * A deleted category must not stay pinned in any entry's manual category anchor (v0.31.0) —
     * otherwise a later edit re-files the entry under a category that no longer exists. Clears the
     * anchor under the [mutex] so it can't race a categorization, then rebuilds the rollup.
     */
    suspend fun clearCategoryAnchor(area: String) = mutex.withLock {
        val a = area.trim()
        if (a.isEmpty()) return@withLock
        runCatching {
            entryDao.clearAnchorGoalArea(a)
            reconcileLocked()
        }
        Unit
    }

    /**
     * How many filed records are tagged to a project [name] **under goal area [area]** — Phase B2b ·
     * project rename-remap. Read-only, so no lock is taken; it just decides whether to offer the
     * 3-option prompt (and shows the count). Scoped by goal area because a folder is unique by
     * (name, goalArea) — a same-named folder under another goal area must not be counted.
     */
    suspend fun countProjectReferences(name: String, area: String): Int {
        val n = name.trim()
        if (n.isEmpty()) return 0
        return entryDao.countProjectReferences(n, area.trim())
    }

    /**
     * Deterministically re-tag every record of the renamed project ([old] under [oldArea]) to [target]
     * under [targetArea] — NO AI, instant (Phase B2b · project rename-remap 3-option flow). Scoped by
     * the OLD goal area so a same-named folder under a different goal area is never touched. Sets the
     * goal-area label to [targetArea] so records follow the folder whether the goal area stays the same
     * (carry — [targetArea] == [oldArea]), the folder was recategorised in the same edit, or the records
     * were reassigned to a project in another goal area. Also follows the rename in `anchorProject` so a
     * later edit re-files into the new folder. When [createTargetFolder] is true the destination folder
     * is created first (option **c** · a brand-new project) — done here, under the [mutex] on the durable
     * app scope, so it can't be orphaned by the caller navigating away. All under the [mutex] so it can't
     * race a categorization, then the rollup is rebuilt so the summary follows.
     */
    suspend fun remapProjectEverywhere(
        old: String,
        oldArea: String,
        target: String,
        targetArea: String,
        createTargetFolder: Boolean = false,
        isCarry: Boolean = false,
    ) = mutex.withLock {
        val o = old.trim()
        val oa = oldArea.trim()
        val t = target.trim()
        val ta = targetArea.trim()
        if (o.isEmpty() || t.isEmpty()) return@withLock
        runCatching {
            // Create the destination folder first (option c) — IGNORE means an existing same-name folder
            // under this goal area is reused, and the records simply join it.
            if (createTargetFolder && ta.isNotEmpty()) {
                projectDao.insert(ProjectEntity(name = t, goalArea = ta, createdAt = System.currentTimeMillis()))
            }
            // The deliverable tags survive ONLY a carry. `ProjectRepository.update` moves this folder's
            // deliverables along with it, so on a carry the records arrive to find them already there.
            // On a reassign / create-new the records go to somebody ELSE's folder and the deliverables
            // stayed behind with the renamed one — so the tags must go.
            //
            // [isCarry] is passed rather than inferred. This previously asked the deliverables table
            // whether a same-named deliverable existed at the destination — but a deliverable's name is
            // not its identity, and names like "Phase 1" repeat across projects, so reassigning into a
            // folder that happened to own its own "Phase 1" silently adopted those records into an
            // unrelated deliverable AND anchored them there. The callers know exactly which of the three
            // options the user chose; the table can only guess.
            //
            // OUTSIDE the name-change guard below, deliberately: "the names happen to match" is not the
            // same question as "did the deliverables come along". Renaming Alpha→Beta then creating a
            // NEW folder called "Alpha" and moving the records there needs no row rewrite (the labels
            // already read "Alpha") — but Alpha's deliverables left with Beta, so every tag left behind
            // points at nothing (found in the v0.33.1 assessment).
            if (!isCarry) entryDao.clearDeliverablesOfProject(o, oa)
            // Skip the row rewrite only when nothing actually changes (same name AND same goal area).
            if (!(o.equals(t, ignoreCase = true) && oa.equals(ta, ignoreCase = true))) {
                // ORDER MATTERS. Both queries below read `project`/`goalCategory`, which
                // remapProjectScoped rewrites; running either after it means its WHERE never matches.
                // That exact mistake cost a round in v0.31.0, so it is spelled out at each query too.
                entryDao.remapAnchorScoped(o, oa, t, ta)
                entryDao.remapProjectScoped(o, oa, t, ta)
            }
            reconcileLocked()
        }
        Unit
    }

    // ---------------- deliverables · the third axis (v0.33.0) ----------------

    /**
     * How many filed records are tagged to deliverable [name] under ([project], [area]) — read-only, so
     * no lock (it only decides whether to warn before a delete). Scoped by BOTH parents, because a
     * deliverable's name alone is not an identity.
     */
    suspend fun countDeliverableReferences(name: String, project: String, area: String): Int {
        val n = name.trim()
        if (n.isEmpty()) return 0
        return entryDao.countDeliverableReferences(n, project.trim(), area.trim())
    }

    /**
     * Rename a deliverable **and re-tag every record of it, atomically** — NO AI, deterministic.
     * Returns true when the rename landed; false when it was refused (blank, unchanged, or the name is
     * already taken under the same project) or the row is gone.
     *
     * **Both halves live here, in ONE transaction, deliberately.** They were briefly split — rename the
     * row in the repository, then remap the entries fire-and-forget — and that was visibly wrong:
     * rendering canonicalises each entry against the *live* deliverable names ([deliverableGroups]), so
     * the instant the row was renamed every entry still tagged with the old name matched nothing and
     * fell out into the project's loose list. The user saw the deliverable go empty and its wins scatter.
     * And the window was not milliseconds: the remap blocks on this very [mutex], which `process()`
     * holds across a live Groq round-trip — so renaming while any capture was filing left the record
     * looking gutted for the whole network call, then snapped back. It self-healed; it looked like data
     * loss, which for a record whose only job is to be trustworthy is just as bad.
     *
     * Collisions are refused up front rather than left to `UPDATE OR IGNORE`, which reports nothing —
     * and the row is re-read afterwards, so the remap can only ever follow a rename that actually landed
     * (the v0.33.0 review's intent-vs-effect lesson).
     */
    suspend fun renameDeliverable(id: Long, newName: String, description: String?): Boolean = mutex.withLock {
        val clean = newName.trim()
        if (clean.isEmpty()) return@withLock false
        val existing = deliverableDao.getById(id) ?: return@withLock false
        // Case-insensitive, matching the name dialogs' own "unchanged" test: a case-only rename is a
        // no-op here rather than a half-applied one (`getByIdentity` would find this very row anyway).
        if (clean.equals(existing.name, ignoreCase = true)) return@withLock false
        if (deliverableDao.getByIdentity(clean, existing.project, existing.goalArea) != null) return@withLock false

        var renamed = false
        runCatching {
            db.withTransaction {
                deliverableDao.update(
                    existing.copy(name = clean, description = description?.trim()?.takeIf { it.isNotBlank() }),
                )
                val after = deliverableDao.getById(id)
                if (after != null && !after.name.equals(existing.name, ignoreCase = true)) {
                    // One statement rewrites the filed label AND the anchor. Unlike the project remap
                    // there's no ordering hazard: neither `project` nor `goalCategory` moves here.
                    entryDao.remapDeliverableScoped(existing.name, existing.project, existing.goalArea, after.name)
                    renamed = true
                }
            }
            if (renamed) reconcileLocked() // the rollup is a derived store, so it syncs after the commit
        }
        renamed
    }

    /**
     * A **deleted deliverable** must not stay tagged on its entries. The entries are the record and are
     * never touched — only the grouping goes, so they fall back to listing plainly under their project.
     * Both columns clear: leaving the filed label would render a group header for a deliverable that no
     * longer exists (a deliverable has no "Uncategorized" catch-all to fall into, unlike a category), and
     * leaving the anchor would re-pin the entry to a ghost on its next edit — the v0.31.0 lesson.
     */
    suspend fun clearDeliverable(name: String, project: String, area: String) = mutex.withLock {
        val n = name.trim()
        if (n.isEmpty()) return@withLock
        runCatching {
            entryDao.clearDeliverable(n, project.trim(), area.trim())
            reconcileLocked()
        }
        Unit
    }

    /** Clear the deliverable axis of every entry of a **deleted project** (its deliverables cascade away
     *  in `ProjectRepository`; this drops the entries' now-dangling tags). */
    suspend fun clearProjectDeliverables(project: String, area: String) = mutex.withLock {
        val p = project.trim()
        if (p.isEmpty()) return@withLock
        runCatching {
            entryDao.clearDeliverablesOfProject(p, area.trim())
            reconcileLocked()
        }
        Unit
    }

    /** Clear the deliverable axis of every entry under a **deleted category** (mirrors
     *  [clearCategoryAnchor], which the same call site already invokes). */
    suspend fun clearCategoryDeliverables(area: String) = mutex.withLock {
        val a = area.trim()
        if (a.isEmpty()) return@withLock
        runCatching {
            entryDao.clearDeliverablesOfCategory(a)
            reconcileLocked()
        }
        Unit
    }

    /** Drain every entry still awaiting processing (called on launch after an interrupted run). */
    suspend fun processPending() {
        entryDao.listByStatus(EntryStatus.RAW).forEach { process(it.id) }
    }

    /** Re-run everything that previously failed (e.g. right after the user adds the Groq key). */
    suspend fun reprocessFailed() {
        entryDao.observeIn(listOf(EntryStatus.FAILED)).first().forEach { process(it.id) }
    }

    /** Everything the categorizer call needs for one entry, plus the resolved anchor and the canonical
     *  universe the output validator ([CategorizedNormalizer]) snaps against. */
    private data class Prep(
        val request: CategorizeRequest,
        val anchor: String?,
        val anchorGoalArea: String?,
        val anchorDeliverable: String?,
        val placementNames: List<String>,
        val pillars: List<Pillar>,
    )

    /** Does this exact deliverable exist under this exact ([project], [area])? A deliverable's name is
     *  not an identity on its own, so every "is this tag real?" test must ask with all three. */
    private suspend fun deliverableExists(name: String?, project: String?, area: String?): Boolean {
        val n = name?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val p = project?.trim()?.takeIf { it.isNotBlank() } ?: return false
        val a = area?.trim()?.takeIf { it.isNotBlank() } ?: return false
        return runCatching { deliverableDao.getByIdentity(n, p, a) }.getOrNull() != null
    }

    private suspend fun prepare(entry: EntryEntity, combineSingle: Boolean = false): Prep {
        val role = settings.settings.first().jobRole
        val fw = frameworkStore.framework.first()
        val activeProjects = projectDao.observeActive().first()
        // Placement universe = sub-folders under any NON-BEHAVIOUR category (what an entry's "project"
        // can be) — goal areas AND development areas. Behaviour sub-folders stay context, not a
        // placement slot: they're tagged via `demonstrates`, not filed into.
        //
        // v0.31.0: this deliberately matches [Recategorize.placementCategories] (`!= BEHAVIOUR`), which
        // the manual Recategorize sheet has always used. Previously the AI got GOAL_AREA folders only
        // while still being allowed to FILE into a development area — so every entry it filed under
        // e.g. "Learning & Growth" was structurally forced to "Outside-project", because it was never
        // offered a single folder there. The AI and the manual UI now agree on one placement universe.
        val goalNames = fw.pillars.filter { it.kind != PillarKind.BEHAVIOUR }.map { it.name }
        val placement = activeProjects.filter { p -> goalNames.any { it.equals(p.goalArea, ignoreCase = true) } }
        val projects = placement.map { p ->
            buildString {
                append("- ").append(p.name).append(" [").append(p.goalArea).append("]")
                // Cap the description (rides on EVERY call + the cached prefix) at 300 chars (AI-1).
                p.description?.takeIf { it.isNotBlank() }?.let { append(" — ").append(TextCaps.cap(it)) }
            }
        }
        // AI-1 · routine-label reuse: the user's existing routine labels (most-used first) so the model
        // reuses a fitting one instead of coining a near-duplicate variant.
        val routineTypes = runCatching { entryDao.distinctRoutineTypes() }.getOrDefault(emptyList())
        // The categorizer framework block = category NAMES (goal areas) + behaviour/development blurbs
        // (AI-1) + each category's sub-folder names. Full project details ride below in {{PROJECTS}}.
        val framework = FrameworkPrompt.categorizerBlock(fw, activeProjects)
        // Anchors: a folder tap at capture time, or a manual correction (Inbox resolve / Recategorize).
        // Both fix this capture's placement (and its split siblings') deterministically.
        val anchor = entry.anchorProject?.takeIf { it.isNotBlank() }
        // The Outside sentinel is the ABSENCE of a project, not a project — it must never be handed to
        // the model as an anchor to "use verbatim" (categorizer rule 4). It still binds deterministically
        // below, in applyCategorized; the model's project guess is simply discarded.
        val namedAnchor = anchor?.takeUnless { it.equals(OUTSIDE_PROJECT, ignoreCase = true) }
        // An explicit manual category anchor wins; otherwise derive the category from the anchored
        // folder (the long-standing folder-tap behaviour).
        val anchorGoalArea = entry.anchorGoalArea?.takeIf { it.isNotBlank() }
            ?: namedAnchor?.let { name -> activeProjects.firstOrNull { it.name.equals(name, ignoreCase = true) }?.goalArea }
        // The DELIVERABLE anchor (v0.33.0) — the only thing that can put an entry in a deliverable at
        // this version. It is deliberately NOT added to the CategorizeRequest: no prompt change ships in
        // this phase (hence no eval gate), so the model is never told deliverables exist and can never
        // guess one. It binds purely deterministically in applyCategorized. Teaching the categorizer to
        // pick one is v0.34.0's job — prompt-first, eval-gated, with the JS mirror in `eval/run.mjs`
        // updated in the same breath (v0.31.0's F4 drifted that mirror and shipped the fix unmeasured).
        //
        // Re-validated against the anchored parents on every re-file rather than trusted: an edit can
        // land long after the capture, and the deliverable may have been deleted or renamed since.
        val anchorDeliverable = entry.anchorDeliverable
            ?.takeIf { deliverableExists(it, namedAnchor, anchorGoalArea) }
        return Prep(
            CategorizeRequest(
                transcript = entry.rawTranscript,
                today = LocalDate.now().toString(),
                framework = framework,
                projects = projects,
                routineTypes = routineTypes,
                role = role,
                projectAnchor = namedAnchor,
                combineSingle = combineSingle,
            ),
            anchor,
            anchorGoalArea,
            anchorDeliverable,
            placement.map { it.name },
            fw.pillars,
        )
    }

    /** Capture path: one transcript may describe several things — split into the row + sibling rows. */
    private suspend fun processEntry(entry: EntryEntity) {
        val p = prepare(entry)
        aiProvider.categorize(p.request).fold(
            onSuccess = { raw ->
                // AI-1 · output validation: snap fuzzy placements/goals/behaviours to canonical, park
                // phantom projects in the Inbox, reject implausible dates — before the row write.
                val result = CategorizedNormalizer.normalize(raw, p.placementNames, p.pillars)
                if (result.entries.isEmpty()) {
                    // No usable work contribution — keep it, but hold it in the Inbox rather than
                    // silently dropping (never lose an entry).
                    entryDao.update(entry.copy(status = EntryStatus.INBOX, confidence = 0.0))
                } else {
                    // First result updates the captured row; extras become sibling rows.
                    val firstRow = entry.applyCategorized(
                        result.entries.first(), p.anchor, p.anchorGoalArea, p.anchorDeliverable,
                    )
                    val siblings = result.entries.drop(1).map { extra ->
                        entry.copy(id = 0, bullet = null)
                            .applyCategorized(extra, p.anchor, p.anchorGoalArea, p.anchorDeliverable)
                    }
                    // Atomic: the first-row update + every sibling insert commit together. Without this,
                    // a crash mid-split leaves row 1 PROCESSED (so processPending skips it forever) while
                    // the extra items the model split out are silently lost.
                    val siblingIds = db.withTransaction {
                        entryDao.update(firstRow)
                        siblings.map { entryDao.insert(it) }
                    }
                    // The rollup is a derived DataStore projection (not part of the Room transaction), so
                    // sync it only after the split is durably committed; a launch reconcile self-heals.
                    syncRollup(firstRow)
                    siblings.forEachIndexed { i, sibling -> syncRollup(sibling.copy(id = siblingIds[i])) }
                }
            },
            onFailure = {
                // AI unreachable or unparseable → keep the transcript, route to Inbox for a later retry.
                entryDao.update(entry.copy(status = EntryStatus.FAILED))
            },
        )
    }

    /** Edit/redo path: re-file exactly this one row (take the first result only) — never split, never
     *  insert siblings, never delete peers. Fixing one entry can't disturb the others. */
    private suspend fun refileSingle(entry: EntryEntity, combineSingle: Boolean = false) {
        val p = prepare(entry, combineSingle)
        aiProvider.categorize(p.request).fold(
            onSuccess = { raw ->
                // AI-1 · output validation (same as the capture path); refile files exactly this one row.
                val result = CategorizedNormalizer.normalize(raw, p.placementNames, p.pillars)
                val first = result.entries.firstOrNull()
                val updated = if (first == null) entry.copy(status = EntryStatus.INBOX, confidence = 0.0)
                else entry.applyCategorized(first, p.anchor, p.anchorGoalArea, p.anchorDeliverable)
                entryDao.update(updated)
                syncRollup(updated)
            },
            onFailure = {
                val failed = entry.copy(status = EntryStatus.FAILED)
                entryDao.update(failed)
                syncRollup(failed)
            },
        )
    }

    /**
     * Map one categorizer result onto this row. When the capture is anchored — by a folder tap OR by a
     * manual correction — the placement is fixed deterministically (the user's call wins even if the
     * model drifts) and the entry is PROCESSED (the placement is certain, so no Inbox for placement
     * uncertainty). Behaviours / impact / isExtra always come from the AI.
     *
     * [anchor] may be the [OUTSIDE_PROJECT] sentinel — a deliberate "no specific project" is a decision
     * and binds exactly like a named folder. [anchorGoalArea] binds INDEPENDENTLY of the project: it is
     * the whole point of the v0.31.0 column, since a manual category chosen alongside "no specific
     * project" has no folder to derive a category from.
     *
     * [anchorDeliverable] is the **only** source of a deliverable in v0.33.0 — the model is never told
     * they exist (no prompt change this phase), so there is nothing to fall back to and no guess to
     * override. `deliverable` is therefore assigned outright rather than `anchorDeliverable ?: c.<x>`:
     * an unanchored entry has none, which is the normal case. v0.34.0 adds the model's guess as the
     * fallback, at which point this becomes the same `anchor ?: guess` shape as the two axes above.
     */
    private fun EntryEntity.applyCategorized(
        c: CategorizedEntry,
        anchor: String?,
        anchorGoalArea: String?,
        anchorDeliverable: String?,
    ): EntryEntity = copy(
        status = statusFor(c, anchored = anchor != null || anchorGoalArea != null),
        occurredAt = c.dateMentioned.toEpochMillisOrNull() ?: occurredAt,
        bullet = c.bullet.ifBlank { null },
        project = anchor ?: c.project,
        goalCategory = anchorGoalArea ?: c.goalCategory,
        deliverable = anchorDeliverable,
        demonstrates = c.demonstrates,
        isExtra = c.isExtra,
        impact = c.impact,
        routine = c.routine,
        routineType = c.routineType?.takeIf { it.isNotBlank() },
        metric = c.metric?.takeIf { it.isNotBlank() },
        confidence = c.confidence,
        suggestedProjects = c.suggestedProjects,
    )

    private companion object {
        const val INBOX_LABEL = "Inbox"
        const val CONFIDENCE_FLOOR = 0.6

        /**
         * Force to Inbox when unplaceable or low-confidence; otherwise cleanly PROCESSED. An anchored
         * capture has a certain project, so it's never held in the Inbox for placement uncertainty.
         */
        fun statusFor(c: CategorizedEntry, anchored: Boolean): EntryStatus {
            if (anchored) return EntryStatus.PROCESSED
            val unplaceable = c.project.equals(INBOX_LABEL, ignoreCase = true) ||
                c.goalCategory.equals(INBOX_LABEL, ignoreCase = true)
            return if (unplaceable || c.confidence < CONFIDENCE_FLOOR) EntryStatus.INBOX else EntryStatus.PROCESSED
        }

        fun String?.toEpochMillisOrNull(): Long? =
            this?.takeIf { it.isNotBlank() }?.let { iso ->
                runCatching {
                    LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrNull()
            }
    }
}
