package com.bragbuddy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.entry.OfflineRecovery
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.AppSettings
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.prefs.ThemeMode
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.reminder.ReminderScheduler
import com.bragbuddy.app.ui.common.ProjectRemap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    aiProvider: AiProvider,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
    private val entryRepository: EntryRepository,
    private val projectRepository: ProjectRepository,
    private val frameworkStore: FrameworkStore,
    private val offlineRecovery: OfflineRecovery,
) : ViewModel() {

    val aiProviderLabel: String = aiProvider.label

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val folders: StateFlow<List<ProjectEntity>> =
        projectRepository.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The goal-area category names a project can roll up to (for the folder's category picker). */
    val goalAreas: StateFlow<List<String>> = frameworkStore.framework
        .map { fw -> fw.goalAreas.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setReminderEnabled(enabled)
        val s = settingsStore.settings.first()
        if (enabled) reminderScheduler.schedule(s.reminderHour, s.reminderMinute) else reminderScheduler.cancel()
    }

    fun setReminderTime(hour: Int, minute: Int) = viewModelScope.launch {
        settingsStore.setReminderTime(hour, minute)
        val s = settingsStore.settings.first()
        if (s.reminderEnabled) reminderScheduler.schedule(s.reminderHour, s.reminderMinute)
    }

    /** Debounces the recovery kick while the key is being typed/edited — one pass per settle. */
    private var keyRecoveryJob: Job? = null

    fun setGroqApiKey(key: String) = viewModelScope.launch {
        val previous = settingsStore.settings.first().groqApiKey
        settingsStore.setGroqApiKey(key)
        // A newly added OR replaced key (fixing a revoked one keeps queued voice notes waiting on
        // exactly this) runs one recovery pass: retry FAILED entries + drain the voice-note queue.
        // Debounced so per-keystroke edits don't fire a pass per character; a launch-time recovery
        // still covers a user who leaves Settings before the debounce lands.
        if (key.trim().isNotBlank() && key.trim() != previous) {
            keyRecoveryJob?.cancel()
            keyRecoveryJob = viewModelScope.launch {
                delay(1_200)
                offlineRecovery.kick()
            }
        }
    }

    fun setJobRole(role: String) = viewModelScope.launch { settingsStore.setJobRole(role) }

    fun setWeeklyRecapEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setWeeklyRecapEnabled(enabled)
        if (enabled) reminderScheduler.scheduleWeekly() else reminderScheduler.cancelWeekly()
    }

    // ---------------- Theme (Phase 2 · device-local appearance) ----------------

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsStore.setThemeMode(mode) }

    fun setAutoDarkTime(hour: Int, minute: Int) = viewModelScope.launch { settingsStore.setAutoDarkTime(hour, minute) }

    fun setAutoLightTime(hour: Int, minute: Int) = viewModelScope.launch { settingsStore.setAutoLightTime(hour, minute) }

    fun setReviewYearStartMonth(month: Int) = viewModelScope.launch { settingsStore.setReviewYearStartMonth(month) }

    fun createProject(name: String, goalArea: String) = viewModelScope.launch {
        val area = goalArea.ifBlank { goalAreas.value.firstOrNull() ?: "Performance Goals" }
        projectRepository.create(name, area)
    }

    fun updateProject(id: Long, name: String, goalArea: String) = viewModelScope.launch {
        val existing = projectRepository.getById(id)
        // Preserve the folder's existing detail — the Settings dialog doesn't edit it, and passing null
        // silently wiped it before. Only the name / goal area change here.
        // `stored` is the row AS SAVED — NOT what was asked for. See the remap gate below.
        val stored = projectRepository.update(id, name, goalArea, description = existing?.description)
        // ⚠️ ORDER IS LOAD-BEARING (v0.33.0): the update above is AWAITED, and it is what moves this
        // folder's deliverables to their new parent. The remap offered below resolves each entry's
        // deliverable tag by asking whether it EXISTS at the destination — so a remap that ran before
        // the move would find nothing and wipe every tag. See EntryDao.clearDeliverablesNotUnder.
        //
        // ⚠️ And the offer is gated on the STORED name/area, never the requested ones. `ProjectDao.update`
        // is `UPDATE OR IGNORE`, so renaming onto an existing sibling folder is silently skipped and
        // nothing here validates it. Offering a remap for a rename that never landed let "Carry" merge
        // this folder's records into the unrelated folder that already owned the name — and wipe their
        // deliverable tags on the way (v0.33.0 stability assessment).
        val oldName = existing?.name
        val oldArea = existing?.goalArea.orEmpty()
        val newName = stored?.name ?: return@launch
        val newArea = stored.goalArea
        // The dialog can change the folder's goal area as well as its name, so BOTH count as a move: a
        // category-only re-home used to skip the offer entirely, stranding every record's `goalCategory`
        // on the old category — the folder then rendered under BOTH (records under the old, its
        // deliverable groups under the new), so the structure silently vanished from the records' side.
        val moved = oldName != null &&
            (!oldName.equals(newName, ignoreCase = true) || !oldArea.equals(newArea, ignoreCase = true))
        if (moved) {
            val count = runCatching { entryRepository.countProjectReferences(oldName!!, oldArea) }.getOrDefault(0)
            if (count > 0) _pendingProjectRemap.value = ProjectRemap(oldName!!, newName, oldArea, newArea, count)
        }
    }

    /** Delete a folder. Its deliverables cascade away inside [projectRepository]; its entries' dangling
     *  deliverable tags are cleared here (read the row FIRST — the delete takes its name with it). */
    fun deleteProject(id: Long) = viewModelScope.launch {
        val p = projectRepository.getById(id)
        projectRepository.delete(id)
        if (p != null) entryRepository.clearProjectDeliverables(p.name, p.goalArea)
    }

    // ---------------- Project rename-remap (deterministic, no AI · 3-option) ----------------

    private val _pendingProjectRemap = MutableStateFlow<ProjectRemap?>(null)
    val pendingProjectRemap: StateFlow<ProjectRemap?> = _pendingProjectRemap.asStateFlow()

    /** (a) Carry — move records to the renamed folder's new name (and its new goal area, so they follow
     *  the folder even if its category changed in the same edit). */
    fun applyProjectCarry() {
        val r = _pendingProjectRemap.value ?: return
        entryRepository.remapProjectEntries(r.oldName, r.oldArea, r.newName, r.newArea)
        _pendingProjectRemap.value = null
    }

    /** (b) Reassign — move records to an existing project; its goal area follows. */
    fun applyProjectReassign(target: ProjectEntity) {
        val r = _pendingProjectRemap.value ?: return
        entryRepository.remapProjectEntries(r.oldName, r.oldArea, target.name, target.goalArea)
        _pendingProjectRemap.value = null
    }

    /** (c) New project — create one under the folder's goal area, then move records into it (the create
     *  runs durably in the processor, so it can't be orphaned by navigating away). */
    fun applyProjectCreateNew(newProjectName: String) {
        val r = _pendingProjectRemap.value ?: return
        val nm = newProjectName.trim().ifBlank { return }
        entryRepository.remapProjectEntries(r.oldName, r.oldArea, nm, r.newArea, createTargetFolder = true)
        _pendingProjectRemap.value = null
    }

    /** The user left records as-is — they surface under "Uncategorized" until re-homed. */
    fun dismissProjectRemap() { _pendingProjectRemap.value = null }

    /** Reset the appraisal framework to the shipped default (Phase B2). Projects and filed records are
     *  KEPT — records under changed categories simply surface under "Uncategorized" until re-homed. */
    fun resetFramework() = viewModelScope.launch { frameworkStore.reset() }
}
