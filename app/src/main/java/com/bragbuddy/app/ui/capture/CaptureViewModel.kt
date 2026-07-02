package com.bragbuddy.app.ui.capture

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.speech.AudioRecorder
import com.bragbuddy.app.data.speech.GroqTranscriber
import com.bragbuddy.app.data.speech.SpeechToText
import com.bragbuddy.app.data.speech.SttEvent
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

enum class VoicePhase { IDLE, LISTENING, TRANSCRIBING, ERROR }

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.SPEAK,
    val phase: VoicePhase = VoicePhase.IDLE,
    val elapsedSec: Int = 0,
    val partial: String = "",
    val typed: String = "",
    val levels: List<Float> = emptyList(),
    val error: String? = null,
    /** True while the engine is cloud Whisper (affects the recorder/live-partials behaviour). */
    val cloud: Boolean = false,
)

/**
 * Drives the capture sheet. **Fire-and-forget:** on submit it saves the raw transcript and signals
 * [saved] so the surface can toast + dismiss. Two voice engines: **on-device** (Android STT, live
 * partials) or **cloud Whisper** (record → upload to Groq → text). Typing is always an equal peer.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val entries: EntryRepository,
    private val settings: SettingsStore,
    private val groqTranscriber: GroqTranscriber,
) : ViewModel() {

    private val stt by lazy { SpeechToText(appContext) }
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
    private var useCloud = false

    init {
        viewModelScope.launch {
            val s = settings.settings.first()
            useCloud = s.cloudTranscription
            _state.update { it.copy(mode = s.lastCaptureMode, cloud = useCloud) }
        }
    }

    fun setMode(mode: CaptureMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode, error = null) }
        viewModelScope.launch { settings.setLastCaptureMode(mode) }
        if (mode == CaptureMode.TYPE) stopVoiceInternal() else _state.update { it.copy(phase = VoicePhase.IDLE) }
    }

    /** Called once mic permission is confirmed. Reads the engine fresh (avoids a first-capture race
     *  with the async init), then branches to cloud recording or on-device STT. */
    fun startVoice() {
        val phase = _state.value.phase
        if (phase == VoicePhase.LISTENING || phase == VoicePhase.TRANSCRIBING) return
        submitting = false
        didSave = false
        lastAudio = null
        viewModelScope.launch {
            useCloud = settings.settings.first().cloudTranscription
            _state.update {
                it.copy(phase = VoicePhase.LISTENING, cloud = useCloud, elapsedSec = 0, partial = "", error = null, levels = emptyList())
            }
            startTimer()
            if (useCloud) startRecording() else startListening()
        }
    }

    // ---------------- Cloud (record → Groq Whisper) ----------------

    private fun startRecording() {
        runCatching { lastAudio = recorder.start() }.onFailure {
            fail("Couldn't start recording")
            return
        }
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
                    if (text.isBlank()) fail("Didn't catch that — try again or type it")
                    else save(text, EntrySource.VOICE) { file.delete(); lastAudio = null }
                },
                onFailure = { fail(it.message ?: "Couldn't transcribe — try again or type it") },
            )
        }
    }

    // ---------------- On-device (live STT) ----------------

    private fun startListening() {
        stt.start { event -> handle(event) }
    }

    private fun handle(event: SttEvent) {
        when (event) {
            is SttEvent.Ready -> {}
            is SttEvent.Rms -> {
                val norm = ((event.db + 2f) / 12f).coerceIn(0f, 1f)
                _state.update { s -> s.copy(levels = (s.levels + norm).takeLast(MAX_LEVELS)) }
            }
            is SttEvent.Partial -> _state.update { it.copy(partial = event.text) }
            is SttEvent.Final -> {
                if (event.text.isNotBlank()) _state.update { it.copy(partial = event.text) }
                if (submitting) finishOnDeviceSubmit()
            }
            is SttEvent.EndOfSpeech -> {}
            is SttEvent.Error -> {
                if (submitting && _state.value.partial.isNotBlank()) finishOnDeviceSubmit()
                else fail(event.message)
                stopTimer()
            }
        }
    }

    private fun finishOnDeviceSubmit() {
        val text = _state.value.partial.trim()
        if (text.isBlank()) { fail("Didn't catch that — try again or type it"); submitting = false; return }
        stt.cancelAndRelease()
        save(text, EntrySource.VOICE)
    }

    // ---------------- Shared ----------------

    /** Stop = submit. Cloud → stop recording + transcribe; on-device → finalize STT. */
    fun stopAndSubmitVoice() {
        if (submitting || didSave) return
        if (useCloud) {
            stopAndTranscribe()
        } else {
            submitting = true
            stopTimer()
            stt.stop()
            viewModelScope.launch {
                delay(1500)
                if (!didSave) finishOnDeviceSubmit()
            }
        }
    }

    fun onTypedChange(text: String) = _state.update { it.copy(typed = text) }

    fun submitTyped() {
        val text = _state.value.typed.trim()
        if (text.isBlank() || didSave) return
        save(text, EntrySource.TEXT)
    }

    fun retryVoice() {
        _state.update { it.copy(error = null) }
        val audio = lastAudio
        if (useCloud && audio != null && audio.exists()) transcribe(audio) else startVoice()
    }

    private fun save(text: String, source: EntrySource, onDone: () -> Unit = {}) {
        if (didSave) return
        didSave = true
        viewModelScope.launch {
            entries.capture(text, source)
            onDone()
            _saved.tryEmit(Unit)
        }
    }

    private fun fail(message: String) {
        submitting = false
        _state.update { it.copy(phase = VoicePhase.ERROR, error = message) }
    }

    private fun stopVoiceInternal() {
        stopTimer()
        ampJob?.cancel(); ampJob = null
        stt.cancelAndRelease()
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
        stt.cancelAndRelease()
        runCatching { recorder.cancel() }
        if (!didSave) lastAudio?.delete()
    }

    private companion object {
        const val MAX_LEVELS = 28
    }
}
