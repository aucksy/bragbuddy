package com.bragbuddy.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.net.ConnectivityMonitor
import com.bragbuddy.app.data.project.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Inbox: entries the AI couldn't confidently place (INBOX) or couldn't reach the model for
 * (FAILED). Phase 3 adds **tap-to-resolve** — assign a suggested project, any existing folder, or
 * "Outside project" in one tap. Resolving keeps the cleaned bullet/behaviours the model already
 * produced (no AI re-call) and files the row PROCESSED. FAILED entries can also be retried.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val projects: ProjectRepository,
    private val frameworkStore: FrameworkStore,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    val entries: StateFlow<List<EntryEntity>> = repository.observeInbox()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val folders: StateFlow<List<ProjectEntity>> = projects.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Drives the calm offline copy on FAILED cards ("will retry when you're connected"). */
    val isOnline: StateFlow<Boolean> = connectivity.isOnline

    fun retry(id: Long) = repository.retry(id)

    fun deleteMany(ids: Collection<Long>) = repository.deleteMany(ids.toList())

    /** Resolve an entry into [projectName] — a suggested project or a chosen folder. If the tapped
     *  name isn't a folder yet (an AI suggestion for a project the user never created), create it
     *  first so the entry lands under that real folder instead of collapsing into "Outside project". */
    fun resolveToProject(entry: EntryEntity, projectName: String) = viewModelScope.launch {
        val folder = folders.value.firstOrNull { it.name.equals(projectName, ignoreCase = true) }
        val goalArea = folder?.goalArea ?: bestGoalArea(entry.goalCategory)
        if (folder == null) projects.create(projectName, goalArea) // idempotent (IGNORE on conflict)
        repository.resolve(entry.id, projectName, goalArea)
    }

    /** File an entry as "Outside project" — no named project, kept under its best goal area. */
    fun resolveOutside(entry: EntryEntity) = viewModelScope.launch {
        repository.resolve(entry.id, OUTSIDE_PROJECT, bestGoalArea(entry.goalCategory))
    }

    /** The entry's own goal area if it names a real (non-behaviour) pillar, else the first goal area. */
    private suspend fun bestGoalArea(current: String?): String {
        val pillars = frameworkStore.framework.first().pillars
        val areas = pillars.filter { it.kind != PillarKind.BEHAVIOUR }
        val keep = current?.takeIf { c -> areas.any { it.name.equals(c, ignoreCase = true) } }
        return keep ?: areas.firstOrNull()?.name ?: "Performance Goals"
    }
}
