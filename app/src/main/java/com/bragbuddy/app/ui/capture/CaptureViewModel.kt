package com.bragbuddy.app.ui.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.data.prefs.SettingsStore
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
import javax.inject.Inject

enum class VoicePhase { IDLE, LISTENING, ERROR }

data class CaptureUiState(
    val mode: CaptureMode = CaptureMode.SPEAK,
    val phase: VoicePhase = VoicePhase.IDLE,
    val elapsedSec: Int = 0,
    val partial: String = "",
    val typed: String = "",
    val levels: List<Float> = emptyList(),
    val error: String? = null,
)

/**
 * Drives the capture sheet. **Fire-and-forget:** on submit it saves the raw transcript and signals
 * [saved] so the surface can toast + dismiss — no AI, no blocking. Voice uses on-device STT; typing
 * is an equal peer. The sheet opens to whichever mode was used last.
 */
@HiltViewModel
class CaptureViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val entries: EntryRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    private val stt by lazy { SpeechToText(appContext) }

    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved

    private var timerJob: Job? = null
    private var submitting = false
    private var didSave = false

    init {
        viewModelScope.launch {
            val mode = settings.settings.first().lastCaptureMode
            _state.update { it.copy(mode = mode) }
        }
    }

    val sttAvailable: Boolean get() = stt.isAvailable()

    fun setMode(mode: CaptureMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode, error = null) }
        viewModelScope.launch { settings.setLastCaptureMode(mode) }
        if (mode == CaptureMode.TYPE) stopListening() else _state.update { it.copy(phase = VoicePhase.IDLE) }
    }

    /** Called once mic permission is confirmed (or on entering voice mode with permission). */
    fun startListening() {
        if (_state.value.phase == VoicePhase.LISTENING) return
        submitting = false
        didSave = false
        _state.update { it.copy(phase = VoicePhase.LISTENING, elapsedSec = 0, partial = "", error = null, levels = emptyList()) }
        startTimer()
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
                if (submitting) finishSubmit()
            }
            is SttEvent.EndOfSpeech -> {}
            is SttEvent.Error -> {
                // If the user already asked to submit and we have text, keep it; else surface the error.
                if (submitting && _state.value.partial.isNotBlank()) finishSubmit()
                else _state.update { it.copy(phase = VoicePhase.ERROR, error = event.message) }
                stopTimer()
            }
        }
    }

    /** Stop button = submit. Finalize STT, then save whatever we heard. */
    fun stopAndSubmitVoice() {
        if (submitting) return
        submitting = true
        stopTimer()
        stt.stop()
        // Safety net: if no final result arrives, save the latest partial anyway.
        viewModelScope.launch {
            delay(1500)
            if (!didSave) finishSubmit()
        }
    }

    private fun finishSubmit() {
        if (didSave) return
        val text = _state.value.partial.trim()
        if (text.isBlank()) {
            _state.update { it.copy(phase = VoicePhase.ERROR, error = "Didn't catch that — try again or type it") }
            submitting = false
            return
        }
        didSave = true
        stt.cancelAndRelease()
        viewModelScope.launch {
            entries.capture(text, EntrySource.VOICE)
            _saved.tryEmit(Unit)
        }
    }

    fun onTypedChange(text: String) = _state.update { it.copy(typed = text) }

    fun submitTyped() {
        val text = _state.value.typed.trim()
        if (text.isBlank() || didSave) return
        didSave = true
        viewModelScope.launch {
            entries.capture(text, EntrySource.TEXT)
            _saved.tryEmit(Unit)
        }
    }

    fun retryVoice() {
        _state.update { it.copy(phase = VoicePhase.IDLE, error = null) }
        startListening()
    }

    private fun stopListening() {
        stopTimer()
        stt.cancelAndRelease()
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
        stt.cancelAndRelease()
    }

    private companion object {
        const val MAX_LEVELS = 28
    }
}
