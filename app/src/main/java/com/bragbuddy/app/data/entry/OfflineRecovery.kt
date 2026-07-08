package com.bragbuddy.app.data.entry

import android.content.Context
import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.net.ConnectivityMonitor
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.speech.GroqTranscriber
import com.bragbuddy.app.data.speech.TranscriptionHttpException
import com.bragbuddy.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 7 · the offline queue's recovery engine. Runs whenever the network comes back (and once per
 * launch, and when the Groq key is added):
 *
 *  1. **Adopt orphaned clips.** A crash between moving a clip into the queue dir and its row insert
 *     would strand the file forever — the sweep inserts a PENDING_AUDIO row for any unreferenced,
 *     settled clip so nothing recorded can rot invisibly on disk.
 *  2. **Drain the voice-note queue.** Every [EntryStatus.PENDING_AUDIO] row is transcribed via the
 *     same cloud Whisper path as a live capture, flipped to RAW, and handed to the [EntryProcessor].
 *     Each outcome is committed through [EntryProcessor.commitPendingAudio] — a compare-and-swap
 *     under the processing mutex — so a concurrent Drive restore can never be clobbered by a
 *     stale-id write, and **the clip is deleted only after its text was durably committed**.
 *  3. **Retry FAILED entries** through the existing idempotent `process()` path (same as Inbox →
 *     Try again).
 *
 * Failure policy per clip: transient failures (offline, 408/429/5xx) leave it queued for the next
 * trigger; auth failures (401/403 — a bad key, fixable in Settings) also stay queued (the key-change
 * kick retries); other 4xx (unreadable clip, over the upload limit) can never succeed, so the row is
 * parked **visibly in the Inbox** with an honest note instead of silently retrying forever.
 */
@Singleton
class OfflineRecovery @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryDao: EntryDao,
    private val processor: EntryProcessor,
    private val transcriber: GroqTranscriber,
    private val settings: SettingsStore,
    private val connectivity: ConnectivityMonitor,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val mutex = Mutex()

    /** Start watching connectivity for the app's lifetime (called once from BragBuddyApp). The
     *  StateFlow's current value is collected immediately, so an online launch recovers right away. */
    fun start() {
        appScope.launch {
            connectivity.isOnline.collect { online -> if (online) runRecovery() }
        }
    }

    /** Fire one recovery attempt now (after queueing a clip while online, or when the key is added). */
    fun kick() {
        appScope.launch { runRecovery() }
    }

    private suspend fun runRecovery() {
        if (!connectivity.isOnline.value) return
        mutex.withLock {
            runCatching { adoptOrphanClips() }
            runCatching { drainPendingAudio() }
            // Delete the clips of notes that have durably left the queue (crash-safe: the drain keeps
            // the clip reference until this sweep removes it, so a crash between the two can't strand an
            // unreferenced file that the orphan sweep would re-adopt into a duplicate entry).
            runCatching { cleanupDrainedClips() }
            // Entries that failed while the model was unreachable get one automatic retry per
            // trigger — the same guarded, idempotent path as the Inbox's "Try again".
            runCatching { processor.reprocessFailed() }
        }
    }

    /**
     * Self-heal the crash window between "clip moved into the queue dir" and "row inserted": any
     * queue file no PENDING_AUDIO row references gets a row. Only files older than [ADOPT_AGE_MS]
     * are adopted, so an in-flight queueVoiceNote insert (file just moved, row still committing)
     * is never double-adopted.
     */
    private suspend fun adoptOrphanClips() {
        val dir = File(context.filesDir, VOICE_QUEUE_DIR)
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        if (files.isEmpty()) return
        // Reference clips owned by ANY row (not just PENDING_AUDIO): a note that already drained but
        // whose file survived a crash before deletion is still referenced by its (now settled) row, so
        // it must NOT be re-adopted into a duplicate — cleanupDrainedClips removes it instead.
        val referenced = entryDao.allAudioPaths().toSet()
        val cutoff = System.currentTimeMillis() - ADOPT_AGE_MS
        for (file in files) {
            if (file.absolutePath in referenced) continue
            if (file.lastModified() > cutoff) continue // possibly a queue insert still in flight
            // A zero-byte orphan holds no words — delete it instead of adopting a junk row
            // (adopting would loop: parked as unrecoverable, file left, re-adopted next pass).
            if (file.length() == 0L) {
                file.delete()
                continue
            }
            entryDao.insert(
                EntryEntity(
                    createdAt = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                    source = EntrySource.VOICE,
                    status = EntryStatus.PENDING_AUDIO,
                    rawTranscript = "",
                    audioPath = file.absolutePath,
                ),
            )
        }
    }

    private suspend fun drainPendingAudio() {
        val key = settings.settings.first().groqApiKey
        if (key.isBlank()) return // can't transcribe without the key — the queue keeps waiting
        val pending = entryDao.listByStatus(EntryStatus.PENDING_AUDIO)
        for (e in pending) {
            val file = e.audioPath?.takeIf { it.isNotBlank() }?.let { File(it) }
            if (file == null || !file.exists() || file.length() == 0L) {
                // The clip vanished (cleared storage edge) or is empty. Park it honestly in the
                // Inbox — as INBOX, not FAILED, so the auto-retry pass never feeds the placeholder
                // to the AI — and remove any empty husk so the orphan sweep can't re-adopt it.
                val committed = processor.commitPendingAudio(e.id, e.audioPath) {
                    it.copy(
                        status = EntryStatus.INBOX,
                        audioPath = null,
                        rawTranscript = it.rawTranscript.ifBlank { LOST_NOTE_TEXT },
                        confidence = 0.0,
                    )
                }
                if (committed) file?.delete()
                continue
            }
            transcriber.transcribe(file).fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        // Whisper heard nothing — retrying would give blank again; hold it visibly.
                        // Keep the clip reference; cleanupDrainedClips owns the file delete (crash-safe).
                        processor.commitPendingAudio(e.id, e.audioPath) {
                            it.copy(status = EntryStatus.INBOX, rawTranscript = EMPTY_NOTE_TEXT, confidence = 0.0)
                        }
                    } else {
                        // Commit the transcript, KEEPING the clip reference (never lose an entry). The
                        // clip is deleted later by cleanupDrainedClips once the row has durably left the
                        // queue — a crash before that used to leave an unreferenced file that the orphan
                        // sweep re-adopted into a DUPLICATE entry. A failed commit means a restore
                        // replaced the row — the surviving pending row drains it on the next pass.
                        val committed = processor.commitPendingAudio(e.id, e.audioPath) {
                            it.copy(status = EntryStatus.RAW, rawTranscript = text)
                        }
                        if (committed) processor.process(e.id)
                    }
                },
                onFailure = { error ->
                    val http = error as? TranscriptionHttpException
                    if (http != null && http.isPermanent && !http.isAuth) {
                        // Unreadable clip / over the upload limit — can never succeed. Park it
                        // visibly (INBOX) instead of re-uploading forever; cleanup deletes the clip.
                        processor.commitPendingAudio(e.id, e.audioPath) {
                            it.copy(status = EntryStatus.INBOX, rawTranscript = UNTRANSCRIBABLE_NOTE_TEXT, confidence = 0.0)
                        }
                    }
                    // Transient (offline, 408/429/5xx) or auth (fixable key) — stays queued; the
                    // next reconnect / launch / key-change kick tries again. Nothing is lost.
                },
            )
        }
    }

    /**
     * Delete the on-disk clips of notes that have durably left the queue. The drain commits a note's
     * text while KEEPING its clip reference, so the file (and its row's `audioPath`) survive together
     * until this sweep removes both — making the "committed → deleted" transition crash-safe: a kill in
     * the middle leaves a still-referenced file that the orphan sweep skips (no duplicate) and this
     * sweep reclaims on the next pass. Idempotent; one delete + one clear per unique path.
     */
    private suspend fun cleanupDrainedClips() {
        val paths = entryDao.settledWithAudio(EntryStatus.PENDING_AUDIO)
            .mapNotNull { it.audioPath?.takeIf { p -> p.isNotBlank() } }
            .toSet()
        for (path in paths) {
            runCatching { File(path).delete() }
            // Clear the DB reference under the PROCESSOR mutex (not this recovery mutex) so a concurrent
            // full-row re-file of the same row can't reinstate the now-dangling path.
            processor.clearDrainedClipPath(path)
        }
    }

    companion object {
        /** App-private dir (filesDir) holding queued offline voice clips until transcription. */
        const val VOICE_QUEUE_DIR = "voice_queue"

        /** Orphan-adoption grace period — must exceed any plausible queue-insert latency. */
        private const val ADOPT_AGE_MS = 2 * 60 * 1000L

        private const val LOST_NOTE_TEXT = "Voice note — the audio clip couldn't be recovered."
        private const val EMPTY_NOTE_TEXT = "Voice note — nothing was heard in the recording."
        private const val UNTRANSCRIBABLE_NOTE_TEXT =
            "Voice note — the clip couldn't be transcribed (unreadable or too long)."
    }
}
