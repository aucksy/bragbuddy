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
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.data.speech.AudioRecorder
import com.bragbuddy.app.data.speech.GroqTranscriber
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
    private val projects: ProjectRepository,
) : ViewModel() {

    private val recorder by lazy { AudioRecorder(appContext) }
    private var refineAudio: File? = null
    // The in-flight transcription / AI coroutine — cancelled on dismiss/reopen so a late callback
    // can't write into a sheet the user already closed or restarted.
    private var refineJob: Job? = null

    val framework: StateFlow<Framework> = frameworkStore.framework
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Framework.DEFAULT)

    /** All sub-folders (across every category) — filtered per-category in the UI. Shared with Home:
     *  both read the one projects table, so adding/removing a folder in either place is in sync. */
    val folders: StateFlow<List<ProjectEntity>> = projects.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refine = MutableStateFlow<RefineState>(RefineState.Hidden)
    val refine: StateFlow<RefineState> = _refine.asStateFlow()

    // ---------------- Direct editor (on the saved framework) ----------------

    fun editCategory(id: String, name: String, description: String) {
        val n = name.trim().ifBlank { return }
        val old = framework.value.pillars.firstOrNull { it.id == id } ?: return
        persist(framework.value.pillars.map { if (it.id == id) it.copy(name = n, blurb = description.trim()) else it })
        // A renamed category must carry its sub-folders with it (their goalArea = the category name).
        if (old.name != n) viewModelScope.launch { projects.renameCategory(old.name, n) }
    }

    fun remove(id: String) {
        val p = framework.value.pillars.firstOrNull { it.id == id }
        persist(framework.value.pillars.filterNot { it.id == id })
        // Removing a category also removes its sub-folders (entries already filed stay in the record).
        if (p != null) viewModelScope.launch { projects.deleteByCategory(p.name) }
    }

    // ---------------- Sub-folders under a category ----------------

    fun addSubFolder(category: String, name: String) = viewModelScope.launch {
        projects.create(name, category)
    }

    fun renameSubFolder(project: ProjectEntity, name: String) = viewModelScope.launch {
        projects.update(project.id, name, project.goalArea, project.description)
    }

    fun deleteSubFolder(id: Long) = viewModelScope.launch { projects.delete(id) }

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
        runCatching { recorder.cancel() }
        refineAudio?.delete(); refineAudio = null
        _refine.value = RefineState.Hidden
    }

    fun setRefineMode(mode: RefineMode) {
        val s = _refine.value as? RefineState.Input ?: return
        if (mode == RefineMode.TYPE) runCatching { recorder.cancel() }
        _refine.value = s.copy(mode = mode, listening = false, transcribing = false, error = null)
    }

    fun onDescriptionChange(text: String) {
        val s = _refine.value as? RefineState.Input ?: return
        _refine.value = s.copy(text = text)
    }

    /** Called once mic permission is granted. Refine-by-voice is cloud-only (Groq); without a key it
     *  points the user to Settings and typing stays available. */
    fun startVoice() {
        val s = _refine.value as? RefineState.Input ?: return
        viewModelScope.launch {
            if (settings.settings.first().groqApiKey.isBlank()) {
                _refine.value = s.copy(
                    mode = RefineMode.SPEAK, listening = false, transcribing = false,
                    error = "Add your Groq key in Settings to refine by voice — or type it instead.",
                )
                return@launch
            }
            _refine.value = s.copy(mode = RefineMode.SPEAK, listening = true, transcribing = false, error = null)
            startRecording()
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
    }

    /** Send the current framework + the instruction to the AI and move to Review. */
    fun buildFromDescription() {
        val text = (_refine.value as? RefineState.Input)?.text?.trim().orEmpty()
        if (text.isBlank()) return
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
