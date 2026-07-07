package com.bragbuddy.app.ui.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.ImageExtractRequest
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.entry.OfflineRecovery
import com.bragbuddy.app.data.image.ImageInput
import com.bragbuddy.app.data.impact.ImpactCheck
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.net.ConnectivityMonitor
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.speech.AudioRecorder
import com.bragbuddy.app.data.speech.GroqTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class VoicePhase { IDLE, LISTENING, TRANSCRIBING, REVIEW, ERROR }

/** The optional "add a number" sub-flow shown on the review screen after a voice take. */
enum class NumberStage { NONE, TYPING, RECORDING, TRANSCRIBING }

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.SPEAK,
    val phase: VoicePhase = VoicePhase.IDLE,
    val elapsedSec: Int = 0,
    val typed: String = "",
    /** The transcript shown for review/edit after a voice capture, before the user taps Add. */
    val reviewText: String = "",
    val levels: List<Float> = emptyList(),
    val error: String? = null,
    /** True when voice was attempted with no Groq key set — the sheet points the user to Settings. */
    val needsKey: Boolean = false,
    /** A transport-failed take can be queued for later ("Save for later" in the error state). */
    val canSaveForLater: Boolean = false,
    /** The clip was queued offline — the surface shows a "saved for later" confirmation + dismisses. */
    val queuedOffline: Boolean = false,
    /** Set once settings (last mode) have loaded — the host waits for this before auto-starting voice. */
    val initialized: Boolean = false,
    /** When capturing into a folder, the project name this entry is anchored to (shown as a chip). */
    val anchorProject: String? = null,
    /** The scanned image (IMAGE mode) — kept for the review thumbnail; null in voice/text modes. */
    val imageThumb: Uri? = null,
    // ---- Review-stage number nudge (voice): record again just for the metric, or type it ----
    val numberStage: NumberStage = NumberStage.NONE,
    // ---- Post-save number nudge (TYPED capture only; voice handles it at review) ----
    val savedNudge: Boolean = false,
    val savedEntryId: Long = 0L,
    val addingNumber: Boolean = false,
    val numberDraft: String = "",
)

/**
 * Drives the capture sheet. **Fire-and-forget:** on submit it saves the raw transcript and signals
 * [saved] so the surface can toast + dismiss. Voice = **cloud Whisper (Groq)** only — record → upload
 * → text (on-device STT was removed; too inaccurate). Voice needs a Groq key; without one the sheet
 * points to Settings and typing (always an equal peer) still works.
 *
 * After a voice take, the review screen offers an optional **"add a number"** — record a short second
 * clip (or type) and it's appended to the transcript, then Add files the combined text so the AI
 * cleans it into one bullet. This never blocks and is always available (never gated by a guess).
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val entries: EntryRepository,
    private val settings: SettingsStore,
    private val groqTranscriber: GroqTranscriber,
    private val connectivity: ConnectivityMonitor,
    private val recovery: OfflineRecovery,
    private val aiProvider: AiProvider,
) : ViewModel() {

    private val recorder by lazy { AudioRecorder(appContext) }

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved

    private var timerJob: Job? = null
    private var ampJob: Job? = null
    private var imageJob: Job? = null
    private var submitting = false
    private var didSave = false
    private var lastAudio: File? = null
    private var numberAudio: File? = null
    private var savedText = "" // the just-saved text, so an added number appends to it (typed path)
    // True once the user has folded an impact/number follow-up into this take → file it as ONE merged
    // bullet (combine mode), not a normal split, so the AI rephrases both together and dedupes repeats.
    private var impactAdded = false

    /** When set (Home → Redo), Add replaces this existing entry instead of inserting a new one. */
    private var replaceId: Long? = null
    fun setReplaceId(id: Long) { if (id > 0L) replaceId = id }

    /** When set (folder tap), the capture is anchored to this project — no spoken prefix needed. */
    private var anchorProject: String? = null
    fun setAnchorProject(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        anchorProject = clean
        _state.update { it.copy(anchorProject = clean) }
    }

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            _state.update { it.copy(mode = s.lastCaptureMode, initialized = true) }
        }
    }

    fun setMode(mode: CaptureMode) {
        if (_state.value.mode == mode) return
        // Leaving image mode → drop any in-flight extraction so a late result can't flip the new UI.
        if (mode != CaptureMode.IMAGE) imageJob?.cancel()
        // Returning to SPEAK while a failed take is on hold must NOT reset to IDLE: the reset
        // would re-trigger auto-start, whose startVoice() drops lastAudio — silently destroying a
        // take the error screen still offers to retry / save for later. Keep the ERROR state (and
        // its copy) so the user lands back on the same choices.
        val keepError = mode == CaptureMode.SPEAK && _state.value.phase == VoicePhase.ERROR
        _state.update {
            it.copy(
                mode = mode,
                error = if (keepError) it.error else null,
                needsKey = if (keepError) it.needsKey else false,
            )
        }
        viewModelScope.launch { settings.setLastCaptureMode(mode) }
        when (mode) {
            CaptureMode.TYPE -> stopVoiceInternal()
            // Image opens on its own pick-a-source screen: stop any live voice recording and clear a
            // leftover voice phase (REVIEW/ERROR) so it can't render inside image mode. A deliberate
            // mode-switch abandons an unsaved voice take (the offline path already auto-queues; only
            // an online transport-failure the user navigates away from is dropped).
            CaptureMode.IMAGE -> {
                stopVoiceInternal()
                _state.update { it.copy(phase = VoicePhase.IDLE, reviewText = "", imageThumb = null) }
            }
            CaptureMode.SPEAK -> if (!keepError) _state.update { it.copy(phase = VoicePhase.IDLE) }
        }
    }

    // ---------------- Image scan (pick a photo → Groq vision → editable review) ----------------

    /**
     * A camera/gallery image was chosen. Downscale + base64 it, read it with Groq vision, and show
     * the extracted text for review — mirroring the voice flow (nothing is saved until [confirmAdd]).
     * Online-only: the source image persists (gallery/cache), so a failure just offers a retry — no
     * queue is needed. Guards: no key → the "add key / type instead" state; a double-pick mid-read is
     * ignored; the coroutine bails if the user has switched modes.
     */
    fun onImageChosen(uri: Uri) {
        if (_state.value.phase == VoicePhase.TRANSCRIBING) return // already reading one
        imageJob?.cancel()
        impactAdded = false // a fresh image starts fresh — never inherit a prior take's combine flag
        _state.update {
            it.copy(mode = CaptureMode.IMAGE, imageThumb = uri, phase = VoicePhase.TRANSCRIBING,
                error = null, needsKey = false, reviewText = "")
        }
        imageJob = viewModelScope.launch {
            val s = settings.settings.first()
            if (s.groqApiKey.isBlank()) {
                _state.update { it.copy(phase = VoicePhase.ERROR, needsKey = true, error = "Image scanning needs your Groq key.") }
                return@launch
            }
            val dataUrl = withContext(Dispatchers.IO) { ImageInput.toDataUrl(appContext, uri) }
            if (_state.value.mode != CaptureMode.IMAGE) return@launch // user switched away mid-read
            if (dataUrl == null) { imageFail("Couldn't read that image — try another, or type it"); return@launch }
            val result = aiProvider.extractFromImage(ImageExtractRequest(dataUrl, s.jobRole))
            if (_state.value.mode != CaptureMode.IMAGE) return@launch // switched away during the read
            result.fold(
                onSuccess = { r ->
                    val text = r.text.trim()
                    if (text.isBlank()) imageFail("I couldn't find any work in that image — try another, or type it")
                    else enterReview(text)
                },
                onFailure = {
                    val offline = !connectivity.isOnline.value
                    imageFail(if (offline) "You're offline — connect and try again, or type it"
                              else it.message ?: "Couldn't read the image — try again, or type it")
                },
            )
        }
    }

    private fun imageFail(message: String) {
        _state.update { it.copy(phase = VoicePhase.ERROR, error = message, needsKey = false) }
    }

    /** The image error state's "Try again" — re-read the same picked image, or (if none was picked,
     *  e.g. the camera couldn't open) return to the pick-a-source screen. */
    fun retryImage() {
        val uri = _state.value.imageThumb
        if (uri != null) onImageChosen(uri)
        else _state.update { it.copy(phase = VoicePhase.IDLE, error = null, needsKey = false) }
    }

    /** Surface an image-capture failure (e.g. no camera app) as the calm image error state. */
    fun onImageError(message: String) = imageFail(message)

    /** Called once mic permission is confirmed. Voice is cloud-only — it needs a Groq key; without one
     *  the sheet shows a "set it up" prompt and typing remains available. */
    fun startVoice() {
        val phase = _state.value.phase
        if (phase == VoicePhase.LISTENING || phase == VoicePhase.TRANSCRIBING) return
        submitting = false
        didSave = false
        lastAudio = null
        impactAdded = false
        viewModelScope.launch {
            if (settings.settings.first().groqApiKey.isBlank()) {
                _state.update {
                    it.copy(phase = VoicePhase.ERROR, needsKey = true, error = "Voice needs your transcription key.")
                }
                return@launch
            }
            _state.update {
                it.copy(phase = VoicePhase.LISTENING, needsKey = false, elapsedSec = 0, error = null, levels = emptyList())
            }
            startTimer()
            startRecording()
        }
    }

    // ---------------- Cloud (record → Groq Whisper) ----------------

    private fun startRecording() {
        runCatching { lastAudio = recorder.start() }.onFailure {
            fail("Couldn't start recording")
            return
        }
        startAmpLoop()
    }

    private fun startAmpLoop() {
        ampJob?.cancel()
        ampJob = viewModelScope.launch {
            while (true) {
                delay(90)
                val norm = (recorder.maxAmplitude() / 12000f).coerceIn(0f, 1f)
                _state.update { s -> s.copy(levels = (s.levels + norm).takeLast(MAX_LEVELS)) }
            }
        }
    }

    private fun stopAndTranscribe() {
        stopTimer()
        ampJob?.cancel(); ampJob = null
        val file = runCatching { recorder.stop() }.getOrNull()
        lastAudio = file
        if (file == null) { fail("Didn't catch that — try again or type it"); return }
        transcribe(file)
    }

    private fun transcribe(file: File) {
        _state.update { it.copy(phase = VoicePhase.TRANSCRIBING, error = null) }
        viewModelScope.launch {
            groqTranscriber.transcribe(file).fold(
                onSuccess = { text ->
                    if (text.isBlank()) {
                        fail("Didn't catch that — try again or type it") // keep the file so retry re-transcribes
                    } else {
                        file.delete(); lastAudio = null // have the text now; the audio isn't needed
                        enterReview(text)
                    }
                },
                onFailure = {
                    // Transport failure (offline / service unreachable) — the take itself is fine, so
                    // it must never be lost (Phase 7 offline queue). A fresh offline capture queues
                    // silently; a redo keeps the error state (its original entry is already safe).
                    val offline = !connectivity.isOnline.value
                    when {
                        replaceId == null && offline -> queueForLater(file)
                        offline -> fail("You're offline — your original entry is safe. Try again when you're connected.")
                        replaceId == null -> fail(
                            it.message ?: "Couldn't transcribe — try again or type it",
                            canSaveForLater = true,
                        )
                        else -> fail(it.message ?: "Couldn't transcribe — try again or type it")
                    }
                },
            )
        }
    }

    /**
     * Keep this take instead of losing it: move the clip out of the cache into the durable
     * voice-note queue and store a PENDING_AUDIO row. [OfflineRecovery] transcribes + files it the
     * moment the network (and key) allow. Used automatically for an offline capture, by the error
     * state's "Save for later", and as the dismiss backstop in [onCleared].
     */
    private fun queueForLater(file: File) {
        if (didSave) return
        val queued = runCatching {
            val dir = File(appContext.filesDir, OfflineRecovery.VOICE_QUEUE_DIR).apply { mkdirs() }
            val dest = File(dir, file.name)
            if (!file.renameTo(dest)) {
                file.copyTo(dest, overwrite = true)
                file.delete()
            }
            // renameTo keeps the RECORDING-time mtime — refresh it so the orphan sweep's grace
            // period measures from the move, not the take (else a slow retry could be adopted as
            // an "orphan" before the row insert lands, duplicating the note).
            dest.setLastModified(System.currentTimeMillis())
            dest
        }.getOrNull()
        if (queued == null) {
            // Couldn't move the clip (shouldn't happen — same volume). Keep the error state so
            // retry / type-instead still work; nothing has been deleted.
            fail("Couldn't save the clip — try again or type it", canSaveForLater = true)
            return
        }
        didSave = true
        lastAudio = null
        submitting = false
        // The kick is sequenced AFTER the row insert commits (both on the app scope) so an
        // immediately-online recovery pass can actually see the new row.
        entries.queueVoiceNote(queued.absolutePath, anchorProject) {
            if (connectivity.isOnline.value) recovery.kick()
        }
        _state.update { it.copy(queuedOffline = true, error = null, canSaveForLater = false) }
    }

    /** The error state's explicit "Save for later" — queue the failed take and finish. */
    fun saveForLater() {
        val audio = lastAudio ?: return
        if (audio.exists()) queueForLater(audio)
    }

    // ---------------- Shared ----------------

    /** Stop = finish the take → review (stop recording + transcribe). */
    fun stopAndSubmitVoice() {
        if (submitting || didSave) return
        submitting = true // guards a double-tap on Stop
        stopAndTranscribe()
    }

    // ---------------- Review before Add (voice) ----------------

    /** After a voice take, show the transcript editable; nothing is saved until [confirmAdd]. */
    private fun enterReview(text: String) {
        submitting = false
        _state.update { it.copy(phase = VoicePhase.REVIEW, reviewText = text, error = null, numberStage = NumberStage.NONE) }
    }

    fun onReviewTextChange(text: String) = _state.update { it.copy(reviewText = text) }

    /** Add = commit the (possibly edited) transcript, plus any number appended at review. */
    fun confirmAdd() {
        if (didSave) return
        // Fold in a number the user typed but didn't explicitly "Add to note" before tapping Add.
        if (_state.value.numberStage == NumberStage.TYPING && _state.value.numberDraft.isNotBlank()) {
            appendToReview(_state.value.numberDraft)
            impactAdded = true
            _state.update { it.copy(numberStage = NumberStage.NONE, numberDraft = "") }
        }
        val text = _state.value.reviewText.trim()
        if (text.isBlank()) return
        // Review is reached from voice or image; file with the matching source.
        val source = if (_state.value.mode == CaptureMode.IMAGE) EntrySource.IMAGE else EntrySource.VOICE
        // Combine mode only when a follow-up was actually added, so a plain multi-item note still splits.
        save(text, source, combineSingle = impactAdded) { lastAudio?.delete(); lastAudio = null }
    }

    /** Discard this take and start over — re-record (voice) or pick another image (image). */
    fun reRecord() {
        cancelNumber()
        if (_state.value.mode == CaptureMode.IMAGE) {
            _state.update { it.copy(phase = VoicePhase.IDLE, reviewText = "", imageThumb = null, error = null) }
        } else {
            _state.update { it.copy(reviewText = "", error = null) }
            startVoice()
        }
    }

    // ---------------- Review-stage "add a number" (voice or type) ----------------

    fun startTypeNumber() = _state.update { it.copy(numberStage = NumberStage.TYPING, numberDraft = "") }

    fun onNumberDraftChange(text: String) = _state.update { it.copy(numberDraft = text) }

    /** Append the typed metric to the transcript being reviewed, then collapse the number field. */
    fun confirmTypeNumber() {
        val added = _state.value.numberDraft.isNotBlank()
        appendToReview(_state.value.numberDraft)
        if (added) impactAdded = true
        _state.update { it.copy(numberStage = NumberStage.NONE, numberDraft = "") }
    }

    /** Record a short second clip just for the metric; on stop it's transcribed and appended. */
    fun startVoiceNumber() {
        viewModelScope.launch {
            if (settings.settings.first().groqApiKey.isBlank()) {
                // No key → fall back to typing the number rather than failing.
                _state.update { it.copy(numberStage = NumberStage.TYPING, numberDraft = "") }
                return@launch
            }
            runCatching { numberAudio = recorder.start() }.onFailure {
                _state.update { it.copy(numberStage = NumberStage.NONE) }
                return@launch
            }
            _state.update { it.copy(numberStage = NumberStage.RECORDING, elapsedSec = 0, levels = emptyList()) }
            startTimer()
            startAmpLoop()
        }
    }

    fun stopVoiceNumber() {
        // Double-tap guard (mirrors stopAndSubmitVoice's `submitting`): the TRANSCRIBING update
        // below is synchronous, so a second tap dispatched before recomposition bails here instead
        // of double-transcribing the same clip and appending the metric twice.
        if (_state.value.numberStage != NumberStage.RECORDING) return
        stopTimer()
        ampJob?.cancel(); ampJob = null
        val file = runCatching { recorder.stop() }.getOrNull()
        numberAudio = file
        if (file == null) { _state.update { it.copy(numberStage = NumberStage.NONE) }; return }
        _state.update { it.copy(numberStage = NumberStage.TRANSCRIBING) }
        viewModelScope.launch {
            groqTranscriber.transcribe(file).fold(
                onSuccess = { text ->
                    file.delete(); numberAudio = null
                    if (text.isNotBlank()) impactAdded = true
                    appendToReview(text)
                    _state.update { it.copy(numberStage = NumberStage.NONE) }
                },
                // Silently return to review (the note is intact) — the user can retry or just Add.
                onFailure = { file.delete(); numberAudio = null; _state.update { it.copy(numberStage = NumberStage.NONE) } },
            )
        }
    }

    /** Cancel the number sub-flow (also stops a number recording if one is running). */
    fun cancelNumber() {
        if (_state.value.numberStage == NumberStage.RECORDING) {
            stopTimer(); ampJob?.cancel(); ampJob = null
            runCatching { recorder.cancel() }
            numberAudio?.delete(); numberAudio = null
        }
        _state.update { it.copy(numberStage = NumberStage.NONE, numberDraft = "") }
    }

    private fun appendToReview(extra: String) {
        val add = extra.trim()
        if (add.isBlank()) return
        _state.update { s ->
            val base = s.reviewText.trim()
            s.copy(reviewText = if (base.isEmpty()) add else "$base $add")
        }
    }

    // ---------------- Typed ----------------

    fun onTypedChange(text: String) = _state.update { it.copy(typed = text) }

    fun submitTyped() {
        val text = _state.value.typed.trim()
        if (text.isBlank() || didSave) return
        save(text, EntrySource.TEXT)
    }

    fun retryVoice() {
        _state.update { it.copy(error = null, needsKey = false, canSaveForLater = false) }
        val audio = lastAudio
        if (audio != null && audio.exists()) transcribe(audio) else startVoice()
    }

    private fun save(text: String, source: EntrySource, combineSingle: Boolean = false, onDone: () -> Unit = {}) {
        if (didSave) return
        didSave = true
        viewModelScope.launch {
            val replace = replaceId
            val id = if (replace != null) { entries.replaceText(replace, text, combineSingle); replace }
                     else entries.capture(text, source, anchorProject = anchorProject, combineSingle = combineSingle)
            onDone()
            when {
                // Voice & image both offered the number nudge at review already → just dismiss.
                source != EntrySource.TEXT -> _saved.tryEmit(Unit)
                // Typed with a measurable value → dismiss instantly.
                ImpactCheck.hasMeasurable(text) -> _saved.tryEmit(Unit)
                // Typed with no number → the free, local post-save nudge (never blocks).
                else -> { savedText = text.trim(); _state.update { it.copy(savedNudge = true, savedEntryId = id) } }
            }
        }
    }

    // ---------------- Post-save nudge (TYPED capture only) ----------------

    fun startAddNumber() = _state.update { it.copy(addingNumber = true) }

    /** Append the typed metric to the already-saved entry and re-file it through the normal pipeline. */
    fun confirmNumber() {
        val metric = _state.value.numberDraft.trim()
        val id = _state.value.savedEntryId
        if (metric.isNotBlank() && id > 0L) {
            // Combine mode: the AI rewrites savedText + the metric into one clean bullet, not a splice.
            entries.replaceText(id, "$savedText $metric".trim(), combineSingle = true)
        }
    }

    private fun fail(message: String, canSaveForLater: Boolean = false) {
        submitting = false
        _state.update { it.copy(phase = VoicePhase.ERROR, error = message, canSaveForLater = canSaveForLater) }
    }

    private fun stopVoiceInternal() {
        stopTimer()
        ampJob?.cancel(); ampJob = null
        runCatching { recorder.cancel() }
        if (_state.value.phase == VoicePhase.LISTENING) _state.update { it.copy(phase = VoicePhase.IDLE) }
    }

    private fun startTimer() {
        stopTimer()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _state.update { it.copy(elapsedSec = it.elapsedSec + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        stopTimer()
        ampJob?.cancel()
        // Dismiss backstop (never lose a take): a take whose sheet is dismissed MID-TRANSCRIPTION
        // (the coroutine dies with the scope, so its onFailure never runs) or after a transport
        // failure is QUEUED, not deleted — the user never saw their words, so silent discard would
        // lose them. Checked BEFORE recorder.cancel(). An explicit Cancel during review, a blank
        // take, or a redo (original entry safe) still cleans up.
        val audio = lastAudio
        val phase = _state.value.phase
        val queueOnDismiss = !didSave && replaceId == null && audio != null && audio.exists() &&
            (phase == VoicePhase.TRANSCRIBING || (phase == VoicePhase.ERROR && _state.value.canSaveForLater))
        if (queueOnDismiss && audio != null) queueForLater(audio)
        runCatching { recorder.cancel() }
        // Clean up whatever wasn't queued — after a successful save/queue these are already null.
        lastAudio?.delete()
        numberAudio?.delete()
    }

    private companion object {
        const val MAX_LEVELS = 28
    }
}
