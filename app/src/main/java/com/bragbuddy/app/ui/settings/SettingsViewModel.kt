package com.bragbuddy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.entry.OfflineRecovery
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.AppSettings
import com.bragbuddy.app.data.prefs.DefaultCaptureMethod
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setDefaultCaptureMethod(method: DefaultCaptureMethod) =
        viewModelScope.launch { settingsStore.setDefaultCaptureMethod(method) }

    fun setCatchupEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setCatchupEnabled(enabled) }

    fun setReviewYearStartMonth(month: Int) = viewModelScope.launch { settingsStore.setReviewYearStartMonth(month) }

    fun createProject(name: String, goalArea: String) = viewModelScope.launch {
        val area = goalArea.ifBlank { goalAreas.value.firstOrNull() ?: "Performance Goals" }
        projectRepository.create(name, area)
    }

    fun updateProject(id: Long, name: String, goalArea: String) = viewModelScope.launch {
        projectRepository.update(id, name, goalArea, description = null)
    }

    fun deleteProject(id: Long) = viewModelScope.launch { projectRepository.delete(id) }
}
