package com.bragbuddy.app.ui.framework

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.ImageExtractRequest
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.image.ImageInput
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.ui.common.ProjectRemap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** State of the per-field document scan used inside the category edit sheet. */
enum class ScanState { IDLE, READING }

/** A pending category rename-remap offer: [count] filed records still carry [oldName]. */
data class CategoryRemap(val oldName: String, val newName: String, val count: Int)

/**
 * Drives the **Framework editor** (Design System §3, Phase B2). Categories are the framework; each is
 * added/edited with its **name** (typed), **detail** (typed or **scanned** from a document), and its
 * **projects** (each name + detail, typed or scanned). Editing is **per-item** — every detail box has
 * its own Save, so the effect can be confirmed distinctly (a category detail feeds the next summary; a
 * project detail feeds future filing). Voice dictation was removed (Phase B2 — Scan replaces the mic).
 * Renaming a category offers a deterministic, no-AI relabel of already-filed records. The company name
 * is never asked.
 */
@HiltViewModel
class FrameworkViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val frameworkStore: FrameworkStore,
    private val settings: SettingsStore,
    private val projects: ProjectRepository,
    private val entries: EntryRepository,
    private val aiProvider: AiProvider,
) : ViewModel() {

    val framework: StateFlow<Framework> = frameworkStore.framework
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Framework.DEFAULT)

    /** All folders (projects) across every category; the UI filters per category. Shared with Home —
     *  both read the one projects table, so edits stay in sync. */
    val folders: StateFlow<List<ProjectEntity>> = projects.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Whether document scanning is available (a Groq key is set). Without it, fields are type-only. */
    val scanEnabled: StateFlow<Boolean> = settings.settings
        .map { it.groqApiKey.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // ---------------- Category CRUD (per-item) ----------------

    fun addCategory(name: String, detail: String, kind: PillarKind) {
        val clean = name.trim().ifBlank { return }
        val pillar = Pillar(
            id = uniqueId(clean, framework.value.pillars),
            name = clean,
            kind = kind,
            blurb = detail.trim().ifBlank { defaultBlurb(kind) },
        )
        persist(framework.value.pillars + pillar)
    }

    fun remove(id: String) {
        val p = framework.value.pillars.firstOrNull { it.id == id }
        persist(framework.value.pillars.filterNot { it.id == id })
        if (p != null) viewModelScope.launch {
            // Removing a category also removes its folders AND their deliverables (entries already filed
            // stay in the record).
            runCatching { projects.deleteByCategory(p.name) }
            // ...and clears any manual category anchor pinned to it, so a later edit doesn't re-file an
            // entry into a category that no longer exists (v0.31.0).
            runCatching { entries.clearCategoryAnchor(p.name) }
            // ...and the deliverable tags of its entries, which would otherwise render a group header
            // for a deliverable that was just deleted (v0.33.0). Unlike the category label — which the
            // "Uncategorized" catch-all still surfaces — a dangling deliverable has nothing to fall into.
            runCatching { entries.clearCategoryDeliverables(p.name) }
        }
    }

    /**
     * Save one category's name / detail / axis (per-item Save). If the name changed, the folders
     * cascade with it (existing behaviour) and — when filed records still carry the old label — a
     * deterministic relabel is OFFERED via [pendingCategoryRemap]. Duplicate names are rejected (kept
     * as the old name) so a rename can't merge two categories' folder namespaces.
     */
    fun saveCategory(id: String, name: String, detail: String, kind: PillarKind) {
        val n = name.trim().ifBlank { return }
        val pillars = framework.value.pillars
        val old = pillars.firstOrNull { it.id == id } ?: return
        val finalName = if (pillars.any { it.id != id && it.name.equals(n, ignoreCase = true) }) old.name else n
        viewModelScope.launch {
            frameworkStore.save(
                pillars.map { if (it.id == id) it.copy(name = finalName, blurb = detail.trim(), kind = kind) else it },
            )
            if (!old.name.equals(finalName, ignoreCase = true)) {
                runCatching { projects.renameCategory(old.name, finalName) }
                val count = runCatching { entries.countCategoryReferences(old.name) }.getOrDefault(0)
                if (count > 0) _pendingCategoryRemap.value = CategoryRemap(old.name, finalName, count)
            }
        }
    }

    // ---------------- Project CRUD (per-item) ----------------

    /** Save one project row: create a new folder ([id] null) or update an existing one, under [category].
     *  [onSaved] receives the row's id (the new id after a create) so the sheet can switch a just-created
     *  row to update-mode and avoid a duplicate on a second save. When an EXISTING project is **renamed**
     *  ([previousName] differs) and it still has filed records, the deterministic 3-option remap is
     *  OFFERED via [pendingProjectRemap] (Phase B2b · project rename-remap). */
    fun saveProject(
        id: Long?,
        name: String,
        detail: String,
        category: String,
        previousName: String? = null,
        onSaved: (Long) -> Unit = {},
    ) {
        val rn = name.trim().ifBlank { return }
        val area = category.trim()
        viewModelScope.launch {
            val newId = runCatching {
                if (id == null) projects.create(rn, area, detail.trim().ifBlank { null })
                else { projects.update(id, rn, area, detail.trim().takeIf { it.isNotBlank() }); id }
            }.getOrDefault(id ?: 0L)
            onSaved(newId)
            val prev = previousName?.trim()
            // ⚠️ ORDER IS LOAD-BEARING (v0.33.0): `projects.update` above is AWAITED before the remap is
            // offered below, and it is what moves this project's deliverables to their new parent. The
            // remap's `clearDeliverablesNotUnder` then asks whether each tagged deliverable EXISTS at the
            // destination — so if the offer ever raced ahead of the update, a plain "carry" would find
            // nothing there and wipe every entry's deliverable tag.
            // A project row's category (`area`) doesn't change here, so old area == new area == area.
            if (id != null && !prev.isNullOrBlank() && !prev.equals(rn, ignoreCase = true)) {
                val count = runCatching { entries.countProjectReferences(prev, area) }.getOrDefault(0)
                if (count > 0) _pendingProjectRemap.value = ProjectRemap(prev, rn, area, area, count)
            }
        }
    }

    /** Delete a project. Its deliverables cascade away inside [projects]; its entries' now-dangling
     *  deliverable tags are cleared here (read the row FIRST — after the delete its name is gone). */
    fun deleteProject(id: Long) = viewModelScope.launch {
        val p = runCatching { projects.getById(id) }.getOrNull()
        runCatching { projects.delete(id) }
        if (p != null) runCatching { entries.clearProjectDeliverables(p.name, p.goalArea) }
    }

    private fun persist(pillars: List<Pillar>) = viewModelScope.launch { frameworkStore.save(pillars) }

    // ---------------- Category rename-remap (deterministic, no AI) ----------------

    private val _pendingCategoryRemap = MutableStateFlow<CategoryRemap?>(null)
    val pendingCategoryRemap: StateFlow<CategoryRemap?> = _pendingCategoryRemap.asStateFlow()

    /** The user confirmed the relabel — move every record's goal-area label + behaviour tags old → new. */
    fun applyCategoryRemap() {
        val r = _pendingCategoryRemap.value ?: return
        entries.renameCategoryEntries(r.oldName, r.newName)
        _pendingCategoryRemap.value = null
    }

    /** The user left records as-is — they'll surface under "Uncategorized" until re-homed. */
    fun dismissCategoryRemap() { _pendingCategoryRemap.value = null }

    // ---------------- Project rename-remap (deterministic, no AI · 3-option) ----------------

    private val _pendingProjectRemap = MutableStateFlow<ProjectRemap?>(null)
    val pendingProjectRemap: StateFlow<ProjectRemap?> = _pendingProjectRemap.asStateFlow()

    /** (a) Carry — move the records to the renamed folder's new name (and its new goal area, so they
     *  follow the folder even if its category changed in the same edit). */
    fun applyProjectCarry() {
        val r = _pendingProjectRemap.value ?: return
        entries.remapProjectEntries(r.oldName, r.oldArea, r.newName, r.newArea)
        _pendingProjectRemap.value = null
    }

    /** (b) Reassign — move the records to an existing project; its goal area follows. */
    fun applyProjectReassign(target: ProjectEntity) {
        val r = _pendingProjectRemap.value ?: return
        entries.remapProjectEntries(r.oldName, r.oldArea, target.name, target.goalArea)
        _pendingProjectRemap.value = null
    }

    /** (c) New project — create one under the folder's goal area, then move the records into it (the
     *  create runs durably in the processor, so it can't be orphaned by navigating away). */
    fun applyProjectCreateNew(newProjectName: String) {
        val r = _pendingProjectRemap.value ?: return
        val nm = newProjectName.trim().ifBlank { return }
        entries.remapProjectEntries(r.oldName, r.oldArea, nm, r.newArea, createTargetFolder = true)
        _pendingProjectRemap.value = null
    }

    /** The user left the records as-is — they surface under "Uncategorized" until re-homed. */
    fun dismissProjectRemap() { _pendingProjectRemap.value = null }

    // ---------------- Per-field document scan (category sheet) ----------------

    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    /** Emits the OCR'd text of a completed scan; the sheet appends it to the active field. */
    private val _scanText = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scanText: SharedFlow<String> = _scanText

    /** Emits a calm error message when a scan can't be read; the sheet surfaces it (no field change). */
    private val _scanError = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val scanError: SharedFlow<String> = _scanError

    private var scanJob: Job? = null

    /**
     * A document image (camera/gallery) was picked for the active field. Downscale + base64 it, read it
     * with Groq vision (the doc-scan prompt), and emit the text to append. Online-only + fail-safe: on
     * any error the field is left unchanged and a calm message is emitted. Guards a double-pick.
     */
    fun onScanImage(uri: Uri) {
        if (_scanState.value != ScanState.IDLE) return
        scanJob?.cancel()
        _scanState.value = ScanState.READING
        scanJob = viewModelScope.launch {
            val s = settings.settings.first()
            if (s.groqApiKey.isBlank()) {
                _scanState.value = ScanState.IDLE
                _scanError.tryEmit("Add your Groq key to scan — or type it instead.")
                return@launch
            }
            val dataUrl = withContext(Dispatchers.IO) { ImageInput.toDataUrl(appContext, uri) }
            if (dataUrl == null) {
                _scanState.value = ScanState.IDLE
                _scanError.tryEmit("Couldn't read that image — try another, or type it.")
                return@launch
            }
            aiProvider.readDocumentText(ImageExtractRequest(dataUrl, s.jobRole)).fold(
                onSuccess = { r ->
                    val text = r.text.trim()
                    if (text.isBlank()) _scanError.tryEmit("No readable text there — try another image, or type it.")
                    else _scanText.tryEmit(text)
                    _scanState.value = ScanState.IDLE
                },
                onFailure = {
                    _scanError.tryEmit(it.message ?: "Couldn't read the document — try again, or type it.")
                    _scanState.value = ScanState.IDLE
                },
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
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
