package com.bragbuddy.app.ui.framework

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.data.speech.AudioRecorder
import com.bragbuddy.app.data.speech.GroqTranscriber
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** A project row being edited in the category sheet ([id] null = a new, unsaved project). */
data class ProjectDraft(val id: Long?, val name: String, val summary: String)

/** State of the per-field voice dictation used inside the category edit sheet. */
enum class FieldVoice { IDLE, RECORDING, TRANSCRIBING }

/**
 * Drives the **Framework editor** (Design System §3). Categories are the framework; each category can
 * be renamed, re-described, removed, and given **projects that each carry their own summary**. Editing
 * happens in a dedicated sheet where every summary field takes **voice or text** (voice = cloud
 * Whisper via the Groq key). The old "refine the whole framework by voice" flow was removed — editing
 * is now direct and per-field. The company name is never asked.
 */
@HiltViewModel
class FrameworkViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val frameworkStore: FrameworkStore,
    private val settings: SettingsStore,
    private val groqTranscriber: GroqTranscriber,
    private val projects: ProjectRepository,
) : ViewModel() {

    private val recorder by lazy { AudioRecorder(appContext) }

    val framework: StateFlow<Framework> = frameworkStore.framework
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Framework.DEFAULT)

    /** All folders (projects) across every category; the UI filters per category. Shared with Home —
     *  both read the one projects table, so edits stay in sync. */
    val folders: StateFlow<List<ProjectEntity>> = projects.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether voice dictation is available (a Groq key is set). Without it, the sheet is type-only. */
    val voiceEnabled: StateFlow<Boolean> = settings.settings
        .map { it.groqApiKey.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---------------- Category CRUD ----------------

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

    fun remove(id: String) {
        val p = framework.value.pillars.firstOrNull { it.id == id }
        persist(framework.value.pillars.filterNot { it.id == id })
        // Removing a category also removes its folders (entries already filed stay in the record).
        if (p != null) viewModelScope.launch { projects.deleteByCategory(p.name) }
    }

    /**
     * Save everything edited in the category sheet: the category's name/summary/kind, plus its
     * projects (create new, update existing, delete removed). A rename carries the folders with it.
     */
    fun saveCategory(id: String, name: String, summary: String, kind: PillarKind, rows: List<ProjectDraft>) {
        val n = name.trim().ifBlank { return }
        val old = framework.value.pillars.firstOrNull { it.id == id } ?: return
        // Don't let a duplicate category name through (the sheet also blocks it): keep the old name so
        // renameCategory can't merge two categories' folder namespaces. Uniqueness is by pillar id +
        // name; a collision here means the UI guard was bypassed — fail safe to the current name.
        val finalName = if (framework.value.pillars.any { it.id != id && it.name.equals(n, ignoreCase = true) }) old.name else n
        persist(framework.value.pillars.map { if (it.id == id) it.copy(name = finalName, blurb = summary.trim(), kind = kind) else it })
        viewModelScope.launch {
            // The whole project diff is best-effort and must never crash the app (unique-index
            // violations are IGNOREd at the DAO; this catch is the final backstop).
            runCatching {
                if (old.name != finalName) projects.renameCategory(old.name, finalName)
                val originalIds = folders.value
                    .filter { it.goalArea.equals(old.name, ignoreCase = true) || it.goalArea.equals(finalName, ignoreCase = true) }
                    .map { it.id }
                val keptIds = rows.mapNotNull { it.id }.toSet()
                // Delete removed rows first (so a re-added same name doesn't collide with a leftover).
                originalIds.filterNot { it in keptIds }.forEach { projects.delete(it) }
                rows.forEach { r ->
                    val rn = r.name.trim()
                    if (r.id == null) {
                        if (rn.isNotBlank()) projects.create(rn, finalName, r.summary.trim().ifBlank { null })
                    } else if (rn.isNotBlank()) {
                        projects.update(r.id, rn, finalName, r.summary.trim().takeIf { it.isNotBlank() })
                    }
                }
            }
        }
    }

    private fun persist(pillars: List<Pillar>) = viewModelScope.launch { frameworkStore.save(pillars) }

    // ---------------- Per-field voice dictation (category sheet) ----------------

    private val _fieldVoice = MutableStateFlow(FieldVoice.IDLE)
    val fieldVoice: StateFlow<FieldVoice> = _fieldVoice.asStateFlow()

    /** Emits the transcript of a completed dictation; the sheet appends it to the active field. */
    private val _fieldTranscript = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val fieldTranscript: SharedFlow<String> = _fieldTranscript

    private var fieldAudio: File? = null
    private var fieldJob: Job? = null

    fun startFieldVoice() {
        if (_fieldVoice.value != FieldVoice.IDLE) return
        // Flip to RECORDING synchronously so a fast second tap (on another field) is rejected before
        // the async recorder start; revert if there's no key or the recorder won't start.
        _fieldVoice.value = FieldVoice.RECORDING
        viewModelScope.launch {
            if (settings.settings.first().groqApiKey.isBlank()) { _fieldVoice.value = FieldVoice.IDLE; return@launch }
            runCatching { fieldAudio = recorder.start() }.onFailure { _fieldVoice.value = FieldVoice.IDLE }
        }
    }

    fun stopFieldVoice() {
        if (_fieldVoice.value != FieldVoice.RECORDING) return
        val file = runCatching { recorder.stop() }.getOrNull()
        fieldAudio = file
        if (file == null) { _fieldVoice.value = FieldVoice.IDLE; return }
        _fieldVoice.value = FieldVoice.TRANSCRIBING
        fieldJob = viewModelScope.launch {
            groqTranscriber.transcribe(file).fold(
                onSuccess = { text ->
                    file.delete(); fieldAudio = null
                    if (text.isNotBlank()) _fieldTranscript.tryEmit(text.trim())
                    _fieldVoice.value = FieldVoice.IDLE
                },
                onFailure = { file.delete(); fieldAudio = null; _fieldVoice.value = FieldVoice.IDLE },
            )
        }
    }

    /** Cancel any in-flight dictation (sheet dismissed). */
    fun cancelFieldVoice() {
        fieldJob?.cancel(); fieldJob = null
        runCatching { recorder.cancel() }
        fieldAudio?.delete(); fieldAudio = null
        _fieldVoice.value = FieldVoice.IDLE
    }

    override fun onCleared() {
        cancelFieldVoice()
    }

    private companion object {
        fun defaultBlurb(kind: PillarKind): String = when (kind) {
            PillarKind.GOAL_AREA -> "What you delivered — results and projects nest here."
            PillarKind.BEHAVIOUR -> "How you worked — the behaviours you demonstrate."
            PillarKind.DEVELOPMENT -> "Skills and development."
        }

        fun uniqueId(name: String, existing: List<Pillar>): String {
            val base = name.slugId()
            if (existing.none { it.id == base }) return base
            var i = 2
            while (existing.any { it.id == "$base-$i" }) i++
            return "$base-$i"
        }

        private fun String.slugId(): String =
            lowercase().trim().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "category" }
    }
}
