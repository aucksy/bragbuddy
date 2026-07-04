package com.bragbuddy.app.ui.capture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.impact.ImpactCheck
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.speech.AudioRecorder
import com.bragbuddy.app.data.speech.GroqTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    /** Set once settings (last mode) have loaded — the host waits for this before auto-starting voice. */
    val initialized: Boolean = false,
    /** When capturing into a folder, the project name this entry is anchored to (shown as a chip). */
    val anchorProject: String? = null,
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
) : ViewModel() {

    private val recorder by lazy { AudioRecorder(appContext) }

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved

    private var timerJob: Job? = null
    private var ampJob: Job? = null
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
        _state.update { it.copy(mode = mode, error = null, needsKey = false) }
        viewModelScope.launch { settings.setLastCaptureMode(mode) }
        if (mode == CaptureMode.TYPE) stopVoiceInternal() else _state.update { it.copy(phase = VoicePhase.IDLE) }
    }

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
                onFailure = { fail(it.message ?: "Couldn't transcribe — try again or type it") },
            )
        }
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
        // Combine mode only when a follow-up was actually added, so a plain multi-item note still splits.
        save(text, EntrySource.VOICE, combineSingle = impactAdded) { lastAudio?.delete(); lastAudio = null }
    }

    /** Discard this take and record again from scratch. */
    fun reRecord() {
        cancelNumber()
        _state.update { it.copy(reviewText = "", error = null) }
        startVoice()
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
        _state.update { it.copy(error = null, needsKey = false) }
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
                // Voice offered the number at review already → just dismiss.
                source == EntrySource.VOICE -> _saved.tryEmit(Unit)
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

    private fun fail(message: String) {
        submitting = false
        _state.update { it.copy(phase = VoicePhase.ERROR, error = message) }
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
        runCatching { recorder.cancel() }
        // Always clean up any recorded temp files — after a successful save they're already null.
        lastAudio?.delete()
        numberAudio?.delete()
    }

    private companion object {
        const val MAX_LEVELS = 28
    }
}
