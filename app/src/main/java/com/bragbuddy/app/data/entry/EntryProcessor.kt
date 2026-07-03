package com.bragbuddy.app.data.entry

import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.CategorizeRequest
import com.bragbuddy.app.data.ai.CategorizedEntry
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectDao
import com.bragbuddy.app.data.prefs.SettingsStore
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
) {

    // Serialize all processing so two callers (capture's kick + a launch-time drain during a config
    // change) can never both claim the same row and double-insert its split siblings. Also friendlier
    // to the free-tier rate limit — entries process one at a time.
    private val mutex = Mutex()

    /**
     * Process a single entry by id. Idempotent and safe to call from several places: it re-reads the
     * row inside the lock and only processes work that is still awaiting it (RAW) or that previously
     * failed and can be retried (FAILED). A PROCESSED/INBOX row is skipped, so nothing is split twice.
     */
    suspend fun process(id: Long) {
        mutex.withLock {
            val entry = entryDao.getById(id) ?: return@withLock
            if (entry.status != EntryStatus.RAW && entry.status != EntryStatus.FAILED) return@withLock
            // Backstop: ANY throw (a malformed key char in a header, a DataStore read error, etc.)
            // must still leave the entry visible in the Inbox rather than crashing the fire-and-forget
            // scope and stranding the row as RAW (which would re-throw and crash-loop on next launch).
            runCatching { processEntry(entry) }
                .onFailure { runCatching { entryDao.update(entry.copy(status = EntryStatus.FAILED)) } }
        }
    }

    /**
     * Replace an entry's text and re-file it from scratch (Home → Edit, or Redo's re-record). Runs
     * under the same lock as processing, so it can't interleave with an in-flight categorization of
     * the same row (which would otherwise clobber the edit). Clears any sibling rows a prior split
     * produced first, so re-filing can't leave duplicates. No-op if the row is gone.
     */
    suspend fun replace(id: Long, text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        mutex.withLock {
            val e = entryDao.getById(id) ?: return@withLock
            entryDao.deleteSiblings(e.createdAt, id)
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
            runCatching { processEntry(reset) }
                .onFailure { runCatching { entryDao.update(reset.copy(status = EntryStatus.FAILED)) } }
        }
    }

    /** Drain every entry still awaiting processing (called on launch after an interrupted run). */
    suspend fun processPending() {
        entryDao.listByStatus(EntryStatus.RAW).forEach { process(it.id) }
    }

    /** Re-run everything that previously failed (e.g. right after the user adds the Groq key). */
    suspend fun reprocessFailed() {
        entryDao.observeIn(listOf(EntryStatus.FAILED)).first().forEach { process(it.id) }
    }

    private suspend fun processEntry(entry: EntryEntity) {
        val role = settings.settings.first().jobRole
        val framework = frameworkStore.framework.first().toPromptBlock()
        val activeProjects = projectDao.observeActive().first()
        val projects = activeProjects.map { p ->
            buildString {
                append("- ").append(p.name).append(" [").append(p.goalArea).append("]")
                p.description?.takeIf { it.isNotBlank() }?.let { append(" — ").append(it) }
            }
        }
        // Folder-tap anchor: fixes the project for this whole capture (and its split siblings).
        val anchor = entry.anchorProject?.takeIf { it.isNotBlank() }
        val anchorGoalArea = anchor?.let { name -> activeProjects.firstOrNull { it.name.equals(name, ignoreCase = true) }?.goalArea }

        val request = CategorizeRequest(
            transcript = entry.rawTranscript,
            today = LocalDate.now().toString(),
            framework = framework,
            projects = projects,
            role = role,
            projectAnchor = anchor,
        )

        aiProvider.categorize(request).fold(
            onSuccess = { result ->
                if (result.entries.isEmpty()) {
                    // No usable work contribution — keep it, but hold it in the Inbox rather than
                    // silently dropping (never lose an entry).
                    entryDao.update(entry.copy(status = EntryStatus.INBOX, confidence = 0.0))
                } else {
                    // First result updates the captured row; extras become sibling rows.
                    entryDao.update(entry.applyCategorized(result.entries.first(), anchor, anchorGoalArea))
                    result.entries.drop(1).forEach { extra ->
                        entryDao.insert(
                            entry.copy(id = 0, bullet = null).applyCategorized(extra, anchor, anchorGoalArea),
                        )
                    }
                }
            },
            onFailure = {
                // AI unreachable or unparseable → keep the transcript, route to Inbox for a later retry.
                entryDao.update(entry.copy(status = EntryStatus.FAILED))
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
