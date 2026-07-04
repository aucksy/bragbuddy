package com.bragbuddy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home state (Phase 3): the flat entry list is shaped into the **living document** ([HomeDoc]) —
 * goal/growth pillars with project cards, behaviour pillars gathering evidence, and the Inbox peek.
 * Also carries the project **folders** (for quick create) and the first-run "what's your role?"
 * prompt. Per-entry editing now lives one tap in, on the deep pillar screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val projects: ProjectRepository,
    private val frameworkStore: FrameworkStore,
    private val settings: SettingsStore,
) : ViewModel() {

    val doc: StateFlow<HomeDoc> = combine(
        repository.observeAll(),
        frameworkStore.framework,
        projects.observeActive(),
    ) { entries, framework, folders ->
        buildHomeDoc(entries, framework, folders)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // isEmpty=false initially so the first frame (before the DB/framework emit) doesn't flash the
        // empty-state CTA for a user who actually has content; the real value arrives immediately.
        HomeDoc(processing = emptyList(), goals = emptyList(), behaviours = emptyList(), inbox = null, isEmpty = false),
    )

    /** Show the gentle first-run role prompt until the role is set or the prompt is dismissed. */
    val showRolePrompt: StateFlow<Boolean> = settings.settings
        .map { it.jobRole.isBlank() && !it.rolePromptDismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun saveRole(role: String) = viewModelScope.launch { settings.setJobRole(role) }

    fun dismissRolePrompt() = viewModelScope.launch { settings.dismissRolePrompt() }

    /** Per-entry actions for the inline folder expansion on Home (mirror the deep pillar view). */
    fun editText(id: Long, text: String) = repository.replaceText(id, text)

    fun delete(id: Long) = repository.delete(id)

    /** Create a folder under [goalArea] (or the framework's first goal category when null). */
    fun createFolder(name: String, goalArea: String? = null) = viewModelScope.launch {
        val area = goalArea?.takeIf { it.isNotBlank() }
            ?: frameworkStore.framework.first().goalAreas.firstOrNull()?.name ?: "Performance Goals"
        projects.create(name, area)
    }
}
