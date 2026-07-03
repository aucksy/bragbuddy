package com.bragbuddy.app.ui.framework

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.FrameworkRefineRequest
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.framework.slug
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.speech.AudioRecorder
import com.bragbuddy.app.data.speech.GroqTranscriber
import com.bragbuddy.app.data.speech.SpeechToText
import com.bragbuddy.app.data.speech.SttEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** How the refine sheet takes the user's description of how they're reviewed. */
enum class RefineMode { SPEAK, TYPE }

/** The refine-by-voice flow (a modal over the editor). */
sealed interface RefineState {
    data object Hidden : RefineState
    /** Collecting the spoken/typed instruction. */
    data class Input(
        val mode: RefineMode = RefineMode.SPEAK,
        val listening: Boolean = false,
        val transcribing: Boolean = false,
        val text: String = "",
        val error: String? = null,
    ) : RefineState
    /** Calling the model. */
    data object Thinking : RefineState
    /** The AI's proposed categories, editable before a one-tap confirm. */
    data class Review(val pillars: List<Pillar>) : RefineState
    data class Error(val message: String) : RefineState
}

/**
 * Drives the **Framework editor** (Design System §3) and its **refine-by-voice** flow (PART C). The
 * user's categories are the framework; they can be added, renamed, re-described, removed, or
 * reshaped by voice. Voice uses the SAME transcription path as capture — cloud Whisper (Groq) when a
 * key is set, on-device STT otherwise — so a working Groq key makes refine reliable. The AI is fed
 * the CURRENT framework + the instruction and returns the updated set (add / edit / remove), shown
 * as editable cards for a one-tap confirm. The company name is never asked.
 */
@HiltViewModel
class FrameworkViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val frameworkStore: FrameworkStore,
    private val aiProvider: AiProvider,
    private val settings: SettingsStore,
    private val groqTranscriber: GroqTranscriber,
) : ViewModel() {

    private val stt by lazy { SpeechToText(appContext) }
    private val recorder by lazy { AudioRecorder(appContext) }
    private var refineAudio: File? = null
    private var useCloud = false
    // The in-flight transcription / AI coroutine — cancelled on dismiss/reopen so a late callback
    // can't write into a sheet the user already closed or restarted.
    private var refineJob: Job? = null

    val framework: StateFlow<Framework> = frameworkStore.framework
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Framework.DEFAULT)

    private val _refine = MutableStateFlow<RefineState>(RefineState.Hidden)
    val refine: StateFlow<RefineState> = _refine.asStateFlow()

    // ---------------- Direct editor (on the saved framework) ----------------

    fun editCategory(id: String, name: String, description: String) {
        val n = name.trim().ifBlank { return }
        persist(framework.value.pillars.map { if (it.id == id) it.copy(name = n, blurb = description.trim()) else it })
    }

    fun remove(id: String) {
        persist(framework.value.pillars.filterNot { it.id == id })
    }

    fun addCategory(name: String, description: String, kind: PillarKind) {
        val clean = name.trim().ifBlank { return }
        val pillar = Pillar(
            id = uniqueId(clean, framework.value.pillars),
            name = clean,
            kind = kind,
            blurb = description.trim().ifBlank { defaultBlurb(kind) },
        )
        persist(framework.value.pillars + pillar)
    }

    private fun persist(pillars: List<Pillar>) = viewModelScope.launch { frameworkStore.save(pillars) }

    // ---------------- Refine-by-voice flow ----------------

    fun openRefine() {
        refineJob?.cancel(); refineJob = null
        _refine.value = RefineState.Input()
    }

    fun cancelRefine() {
        refineJob?.cancel(); refineJob = null
        stt.cancelAndRelease()
        runCatching { recorder.cancel() }
        refineAudio?.delete(); refineAudio = null
        _refine.value = RefineState.Hidden
    }

    fun setRefineMode(mode: RefineMode) {
        val s = _refine.value as? RefineState.Input ?: return
        if (mode == RefineMode.TYPE) { stt.cancelAndRelease(); runCatching { recorder.cancel() } }
        _refine.value = s.copy(mode = mode, listening = false, transcribing = false, error = null)
    }

    fun onDescriptionChange(text: String) {
        val s = _refine.value as? RefineState.Input ?: return
        _refine.value = s.copy(text = text)
    }

    /** Called once mic permission is granted. */
    fun startVoice() {
        val s = _refine.value as? RefineState.Input ?: return
        viewModelScope.launch {
            // Use Groq cloud Whisper for refine whenever a key exists — regardless of the capture
            // engine preference — so refine-by-voice is reliable for anyone with a Groq key.
            // On-device STT is only the no-key fallback (the AI step will then fail with a clear hint).
            useCloud = settings.settings.first().groqApiKey.isNotBlank()
            _refine.value = s.copy(mode = RefineMode.SPEAK, listening = true, transcribing = false, error = null)
            if (useCloud) startRecording() else stt.start { handleStt(it) }
        }
    }

    private fun startRecording() {
        runCatching { refineAudio = recorder.start() }.onFailure {
            (_refine.value as? RefineState.Input)?.let {
                _refine.value = it.copy(listening = false, error = "Couldn't start recording")
            }
        }
    }

    fun stopVoice() {
        if (useCloud) {
            val file = runCatching { recorder.stop() }.getOrNull()
            refineAudio = file
            val s = _refine.value as? RefineState.Input ?: return
            if (file == null) {
                _refine.value = s.copy(listening = false, error = "Didn't catch that — try again or type it")
                return
            }
            _refine.value = s.copy(listening = false, transcribing = true, error = null)
            refineJob = viewModelScope.launch {
                groqTranscriber.transcribe(file).fold(
                    onSuccess = { text ->
                        file.delete(); refineAudio = null
                        val cur = _refine.value as? RefineState.Input ?: return@fold
                        _refine.value = if (text.isBlank()) {
                            cur.copy(transcribing = false, error = "Didn't catch that — try again or type it")
                        } else {
                            cur.copy(text = text, transcribing = false)
                        }
                    },
                    onFailure = {
                        val cur = _refine.value as? RefineState.Input ?: return@fold
                        _refine.value = cur.copy(transcribing = false, error = it.message ?: "Couldn't transcribe — try again or type it")
                    },
                )
            }
        } else {
            stt.stop()
            (_refine.value as? RefineState.Input)?.let { _refine.value = it.copy(listening = false) }
        }
    }

    private fun handleStt(event: SttEvent) {
        val s = _refine.value as? RefineState.Input ?: return
        when (event) {
            is SttEvent.Partial -> _refine.value = s.copy(text = event.text)
            is SttEvent.Final -> _refine.value = s.copy(text = event.text.ifBlank { s.text }, listening = false)
            is SttEvent.Error -> _refine.value = s.copy(listening = false, error = event.message)
            else -> {}
        }
    }

    /** Send the current framework + the instruction to the AI and move to Review. */
    fun buildFromDescription() {
        val text = (_refine.value as? RefineState.Input)?.text?.trim().orEmpty()
        if (text.isBlank()) return
        stt.cancelAndRelease()
        _refine.value = RefineState.Thinking
        refineJob = viewModelScope.launch {
            val current = frameworkStore.framework.first().toPromptBlock()
            aiProvider.refineFramework(FrameworkRefineRequest(text, current)).fold(
                onSuccess = { result ->
                    // Ignore a late result if the user has since dismissed/restarted the sheet.
                    if (_refine.value !is RefineState.Thinking) return@fold
                    val pillars = result.pillars
                        .filter { it.name.isNotBlank() }
                        .mapIndexed { i, p ->
                            val kind = runCatching { PillarKind.valueOf(p.kind.trim().uppercase()) }
                                .getOrDefault(PillarKind.GOAL_AREA)
                            Pillar(
                                id = "refine-$i-${p.name.slug()}",
                                name = p.name.trim(),
                                kind = kind,
                                blurb = p.blurb.trim().ifBlank { defaultBlurb(kind) },
                            )
                        }
                    _refine.value = if (pillars.isEmpty()) {
                        RefineState.Error("I couldn't turn that into categories. Try describing what you're measured on.")
                    } else {
                        RefineState.Review(pillars)
                    }
                },
                onFailure = {
                    if (_refine.value !is RefineState.Thinking) return@fold
                    _refine.value = RefineState.Error(it.message ?: "Couldn't reach the AI. Check your Groq key in Settings and try again.")
                },
            )
        }
    }

    fun renameReviewPillar(index: Int, name: String) {
        val s = _refine.value as? RefineState.Review ?: return
        val clean = name.trim().ifBlank { return }
        _refine.value = s.copy(pillars = s.pillars.mapIndexed { i, p -> if (i == index) p.copy(name = clean) else p })
    }

    fun removeReviewPillar(index: Int) {
        val s = _refine.value as? RefineState.Review ?: return
        _refine.value = s.copy(pillars = s.pillars.filterIndexed { i, _ -> i != index })
    }

    /** One-tap confirm — replace the active framework with the reviewed categories. */
    fun confirmReview() {
        val s = _refine.value as? RefineState.Review ?: return
        if (s.pillars.isEmpty()) return
        persist(s.pillars)
        _refine.value = RefineState.Hidden
    }

    fun backToInput() {
        refineJob?.cancel(); refineJob = null
        _refine.value = RefineState.Input()
    }

    override fun onCleared() {
        stt.cancelAndRelease()
        runCatching { recorder.cancel() }
        refineAudio?.delete()
    }

    private companion object {
        fun defaultBlurb(kind: PillarKind): String = when (kind) {
            PillarKind.GOAL_AREA -> "What you delivered — results and projects nest here."
            PillarKind.BEHAVIOUR -> "How you worked — the behaviours you demonstrate."
            PillarKind.DEVELOPMENT -> "Skills and development."
        }

        fun uniqueId(name: String, existing: List<Pillar>): String {
            val base = name.slug()
            if (existing.none { it.id == base }) return base
            var i = 2
            while (existing.any { it.id == "$base-$i" }) i++
            return "$base-$i"
        }
    }
}
