package com.bragbuddy.app.data.entry

import androidx.room.withTransaction
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.CategorizeRequest
import com.bragbuddy.app.data.ai.CategorizedEntry
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.BragBuddyDatabase
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
     */
    suspend fun replace(id: Long, text: String, combineSingle: Boolean = false) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        mutex.withLock {
            val e = entryDao.getById(id) ?: return@withLock
            // A manually-set ★ Standout is the user's call and must survive an edit (mirrors isPinned,
            // which is already preserved). Re-apply it after the AI re-file, which would otherwise reset it.
            val keepExtra = e.isExtra
            val reset = e.copy(
                rawTranscript = clean,
                status = EntryStatus.RAW,
                occurredAt = null,
                bullet = null,
                project = null,
                goalCategory = null,
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
            val updated = e.copy(
                status = EntryStatus.PROCESSED,
                // A FAILED / empty-result row has no cleaned bullet; fall back to its raw transcript so
                // a resolved entry still appears in the generated summary (the rollup skips bullet-less
                // rows). Rows that already have a bullet keep it.
                bullet = e.bullet?.takeIf { it.isNotBlank() } ?: e.rawTranscript.trim().ifBlank { null },
                project = if (isOutside) OUTSIDE_PROJECT else clean,
                goalCategory = area,
                // A named project becomes the deterministic anchor (edit re-files here); Outside
                // keeps whatever anchor it had (usually none).
                anchorProject = if (isOutside) e.anchorProject else clean,
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
     * still-processing RAW row and an offline PENDING_AUDIO row (let those finish first). A named project
     * becomes the [anchorProject] so a later edit re-files here. A blank [goalArea] falls back to the
     * entry's current one (then "Inbox") — the sheet always sends a real category, so that's a defensive
     * floor only; keeping a real goal area is what lets the entry's evidence reach the summary rollup.
     */
    suspend fun recategorize(
        id: Long,
        goalArea: String,
        project: String,
        demonstrates: List<String>,
    ) {
        mutex.withLock {
            val e = entryDao.getById(id) ?: return@withLock
            if (e.status == EntryStatus.RAW || e.status == EntryStatus.PENDING_AUDIO) return@withLock
            val clean = project.trim()
            val isOutside = clean.isBlank() || clean.equals(OUTSIDE_PROJECT, ignoreCase = true)
            val area = goalArea.trim().ifBlank { e.goalCategory?.takeIf { it.isNotBlank() } ?: INBOX_LABEL }
            val cleanDemos = demonstrates.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            val updated = e.copy(
                status = EntryStatus.PROCESSED,
                // A bullet-less row (recategorized straight from FAILED) falls back to its transcript so
                // it isn't silently dropped from the summary rollup.
                bullet = e.bullet?.takeIf { it.isNotBlank() } ?: e.rawTranscript.trim().ifBlank { null },
                project = if (isOutside) OUTSIDE_PROJECT else clean,
                goalCategory = area,
                anchorProject = if (isOutside) e.anchorProject else clean,
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
     * A queued offline voice note's clip is deleted with its row (no orphaned audio).
     */
    suspend fun delete(id: Long) = mutex.withLock {
        deleteAudioOf(entryDao.getById(id))
        rollupStore.remove(id)
        entryDao.deleteById(id)
    }

    /** Bulk delete (multi-select). Drops each rollup contribution, then removes the rows. */
    suspend fun deleteMany(ids: List<Long>) = mutex.withLock {
        ids.forEach { id ->
            deleteAudioOf(entryDao.getById(id))
            rollupStore.remove(id)
        }
        entryDao.deleteByIds(ids)
    }

    /** Remove a queued voice note's on-device clip when its row goes away. Best-effort. */
    private fun deleteAudioOf(entry: EntryEntity?) {
        entry?.audioPath?.takeIf { it.isNotBlank() }?.let { runCatching { java.io.File(it).delete() } }
    }

    /** Null out a drained voice clip's dangling path reference (offline-recovery cleanup) **under the
     *  processing mutex**, so it can't race — and be reinstated by — a concurrent full-row re-file of
     *  the same row. The file itself is deleted by the caller; this only clears the DB reference. */
    suspend fun clearDrainedClipPath(path: String) = mutex.withLock { entryDao.clearAudioPath(path) }

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
            // Skip the row rewrite only when nothing actually changes (same name AND same goal area).
            if (!(o.equals(t, ignoreCase = true) && oa.equals(ta, ignoreCase = true))) {
                entryDao.remapProjectScoped(o, oa, t, ta)
                entryDao.remapAnchorScoped(o, oa, t)
            }
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

    /** Everything the categorizer call needs for one entry, plus the resolved anchor. */
    private data class Prep(val request: CategorizeRequest, val anchor: String?, val anchorGoalArea: String?)

    private suspend fun prepare(entry: EntryEntity, combineSingle: Boolean = false): Prep {
        val role = settings.settings.first().jobRole
        val fw = frameworkStore.framework.first()
        val activeProjects = projectDao.observeActive().first()
        // Placement universe = sub-folders under GOAL_AREA categories only (what an entry's "project"
        // can be). Behaviour/growth sub-folders are context, not a placement slot.
        val goalNames = fw.pillars.filter { it.kind == PillarKind.GOAL_AREA }.map { it.name }
        val placement = activeProjects.filter { p -> goalNames.any { it.equals(p.goalArea, ignoreCase = true) } }
        val projects = placement.map { p ->
            buildString {
                append("- ").append(p.name).append(" [").append(p.goalArea).append("]")
                p.description?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
            }
        }
        // The categorizer framework block = category NAMES + each category's sub-folder names, but NOT
        // the category detail blurbs (Phase B2b: a category detail feeds the SUMMARY only, so editing it
        // no longer affects daily filing). Full project details ride below in {{PROJECTS}}.
        val framework = FrameworkPrompt.categorizerBlock(fw, activeProjects)
        // Folder-tap anchor: fixes the project for this whole capture (and its split siblings).
        val anchor = entry.anchorProject?.takeIf { it.isNotBlank() }
        val anchorGoalArea = anchor?.let { name -> activeProjects.firstOrNull { it.name.equals(name, ignoreCase = true) }?.goalArea }
        return Prep(
            CategorizeRequest(
                transcript = entry.rawTranscript,
                today = LocalDate.now().toString(),
                framework = framework,
                projects = projects,
                role = role,
                projectAnchor = anchor,
                combineSingle = combineSingle,
            ),
            anchor,
            anchorGoalArea,
        )
    }

    /** Capture path: one transcript may describe several things — split into the row + sibling rows. */
    private suspend fun processEntry(entry: EntryEntity) {
        val p = prepare(entry)
        aiProvider.categorize(p.request).fold(
            onSuccess = { result ->
                if (result.entries.isEmpty()) {
                    // No usable work contribution — keep it, but hold it in the Inbox rather than
                    // silently dropping (never lose an entry).
                    entryDao.update(entry.copy(status = EntryStatus.INBOX, confidence = 0.0))
                } else {
                    // First result updates the captured row; extras become sibling rows.
                    val firstRow = entry.applyCategorized(result.entries.first(), p.anchor, p.anchorGoalArea)
                    val siblings = result.entries.drop(1).map { extra ->
                        entry.copy(id = 0, bullet = null).applyCategorized(extra, p.anchor, p.anchorGoalArea)
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
            onSuccess = { result ->
                val first = result.entries.firstOrNull()
                val updated = if (first == null) entry.copy(status = EntryStatus.INBOX, confidence = 0.0)
                else entry.applyCategorized(first, p.anchor, p.anchorGoalArea)
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
     * Map one categorizer result onto this row. When the capture is anchored to a folder, the
     * project (and its goal area) are fixed deterministically — the folder tap wins even if the
     * model drifts — and the entry is PROCESSED (the project is certain, so no Inbox for placement
     * uncertainty). Behaviours / impact / isExtra always come from the AI.
     */
    private fun EntryEntity.applyCategorized(
        c: CategorizedEntry,
        anchor: String?,
        anchorGoalArea: String?,
    ): EntryEntity = copy(
        status = statusFor(c, anchored = anchor != null),
        occurredAt = c.dateMentioned.toEpochMillisOrNull() ?: occurredAt,
        bullet = c.bullet.ifBlank { null },
        project = anchor ?: c.project,
        goalCategory = if (anchor != null) (anchorGoalArea ?: c.goalCategory) else c.goalCategory,
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
