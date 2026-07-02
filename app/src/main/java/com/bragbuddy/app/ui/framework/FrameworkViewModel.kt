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
import com.bragbuddy.app.data.speech.SttEvent
import com.bragbuddy.app.data.speech.SpeechToText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How the refine sheet takes the user's description of how they're reviewed. */
enum class RefineMode { SPEAK, TYPE }

/** The refine-by-voice flow (a modal over the editor). */
sealed interface RefineState {
    data object Hidden : RefineState
    /** Collecting the spoken/typed description. */
    data class Input(
        val mode: RefineMode = RefineMode.SPEAK,
        val listening: Boolean = false,
        val text: String = "",
        val error: String? = null,
    ) : RefineState
    /** Calling the model. */
    data object Thinking : RefineState
    /** The AI's proposed pillars, editable before a one-tap confirm. */
    data class Review(val pillars: List<Pillar>) : RefineState
    data class Error(val message: String) : RefineState
}

/**
 * Drives the **Framework editor** (Design System §3) and its **refine-by-voice** flow (PART C).
 * The user speaks/types how they're judged → the AI builds pillars → shown as editable cards for a
 * one-tap confirm. The company name is never asked. Edits and confirms persist via [FrameworkStore]
 * and take effect on the very next categorization.
 */
@HiltViewModel
class FrameworkViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val frameworkStore: FrameworkStore,
    private val aiProvider: AiProvider,
) : ViewModel() {

    private val stt by lazy { SpeechToText(appContext) }

    val framework: StateFlow<Framework> = frameworkStore.framework
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Framework.DEFAULT)

    private val _refine = MutableStateFlow<RefineState>(RefineState.Hidden)
    val refine: StateFlow<RefineState> = _refine.asStateFlow()

    // ---------------- Direct editor (on the saved framework) ----------------

    fun rename(id: String, newName: String) {
        val name = newName.trim().ifBlank { return }
        persist(framework.value.pillars.map { if (it.id == id) it.copy(name = name) else it })
    }

    fun remove(id: String) {
        persist(framework.value.pillars.filterNot { it.id == id })
    }

    fun addPillar(name: String, kind: PillarKind) {
        val clean = name.trim().ifBlank { return }
        val pillar = Pillar(
            id = uniqueId(clean, framework.value.pillars),
            name = clean,
            kind = kind,
            blurb = defaultBlurb(kind),
        )
        persist(framework.value.pillars + pillar)
    }

    private fun persist(pillars: List<Pillar>) = viewModelScope.launch { frameworkStore.save(pillars) }

    // ---------------- Refine-by-voice flow ----------------

    fun openRefine() {
        _refine.value = RefineState.Input()
    }

    fun cancelRefine() {
        stt.cancelAndRelease()
        _refine.value = RefineState.Hidden
    }

    fun setRefineMode(mode: RefineMode) {
        val s = _refine.value as? RefineState.Input ?: return
        if (mode == RefineMode.TYPE) stt.cancelAndRelease()
        _refine.value = s.copy(mode = mode, listening = false, error = null)
    }

    fun onDescriptionChange(text: String) {
        val s = _refine.value as? RefineState.Input ?: return
        _refine.value = s.copy(text = text)
    }

    /** Called once mic permission is granted. */
    fun startVoice() {
        val s = _refine.value as? RefineState.Input ?: return
        _refine.value = s.copy(mode = RefineMode.SPEAK, listening = true, error = null)
        stt.start { event -> handleStt(event) }
    }

    fun stopVoice() {
        stt.stop()
        (_refine.value as? RefineState.Input)?.let { _refine.value = it.copy(listening = false) }
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

    /** Send the collected description to the AI and move to Review. */
    fun buildFromDescription() {
        val text = (_refine.value as? RefineState.Input)?.text?.trim().orEmpty()
        if (text.isBlank()) return
        stt.cancelAndRelease()
        _refine.value = RefineState.Thinking
        viewModelScope.launch {
            val current = frameworkStore.framework.first().toPromptBlock()
            aiProvider.refineFramework(FrameworkRefineRequest(text, current)).fold(
                onSuccess = { result ->
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
                    if (pillars.isEmpty()) {
                        _refine.value = RefineState.Error("I couldn't turn that into pillars. Try describing what you're measured on.")
                    } else {
                        _refine.value = RefineState.Review(pillars)
                    }
                },
                onFailure = {
                    _refine.value = RefineState.Error(it.message ?: "Couldn't reach the AI. Check your OpenRouter key and try again.")
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

    /** One-tap confirm — replace the active framework with the reviewed pillars. */
    fun confirmReview() {
        val s = _refine.value as? RefineState.Review ?: return
        if (s.pillars.isEmpty()) return
        persist(s.pillars)
        _refine.value = RefineState.Hidden
    }

    fun backToInput() {
        _refine.value = RefineState.Input()
    }

    override fun onCleared() {
        stt.cancelAndRelease()
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
