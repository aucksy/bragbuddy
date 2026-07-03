package com.bragbuddy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home state: the reverse-chronological entry list, the project **folders**, and the first-run
 * "what's your role?" prompt. Per-entry actions (edit → re-file, delete); "Redo" and "capture into a
 * folder" are handled by the screen launching the capture sheet.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val projects: ProjectRepository,
    private val frameworkStore: FrameworkStore,
    private val settings: SettingsStore,
) : ViewModel() {

    val entries: StateFlow<List<EntryEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<ProjectEntity>> = projects.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Show the gentle first-run role prompt until the role is set or the prompt is dismissed. */
    val showRolePrompt: StateFlow<Boolean> = settings.settings
        .map { it.jobRole.isBlank() && !it.rolePromptDismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Edit an entry's words and re-run the AI on the corrected text. */
    fun editText(id: Long, text: String) = repository.replaceText(id, text)

    fun delete(id: Long) = repository.delete(id)

    fun saveRole(role: String) = viewModelScope.launch { settings.setJobRole(role) }

    fun dismissRolePrompt() = viewModelScope.launch { settings.dismissRolePrompt() }

    /** Create a folder; its goal area defaults to the framework's first goal category. */
    fun createFolder(name: String) = viewModelScope.launch {
        val defaultGoalArea = frameworkStore.framework.first().goalAreas.firstOrNull()?.name ?: "Performance Goals"
        projects.create(name, defaultGoalArea)
    }
}
