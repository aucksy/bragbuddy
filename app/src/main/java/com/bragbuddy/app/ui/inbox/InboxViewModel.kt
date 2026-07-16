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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Inbox: entries the AI couldn't confidently place (INBOX) or couldn't reach the model for
 * (FAILED). Phase 3 adds **tap-to-resolve** — assign a suggested project, any existing folder, or
 * "no specific project" in one tap. Resolving keeps the cleaned bullet/behaviours the model already
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
     *  first so the entry lands under that real folder instead of collapsing into the no-project bucket. */
    fun resolveToProject(entry: EntryEntity, projectName: String) = viewModelScope.launch {
        val folder = folders.value.firstOrNull { it.name.equals(projectName, ignoreCase = true) }
        val goalArea = folder?.goalArea ?: bestGoalArea(entry.goalCategory)
        if (folder == null) projects.create(projectName, goalArea) // idempotent (IGNORE on conflict)
        repository.resolve(entry.id, projectName, goalArea)
    }

    /** File an entry with no named project, kept under the category [bestGoalArea] resolves to — which
     *  the chip NAMES up front, so the user is accepting a category rather than unknowingly inheriting
     *  one (v0.31.0). The choice is anchored, so a later edit can't let the AI revert it. */
    fun resolveOutside(entry: EntryEntity) = viewModelScope.launch {
        repository.resolve(entry.id, OUTSIDE_PROJECT, bestGoalArea(entry.goalCategory))
    }

    /**
     * The placement categories (non-behaviour pillars), so the screen can show the user WHICH category
     * a "no specific project" resolve will land in — [bestGoalAreaOf] is the same rule this VM applies.
     */
    val placementAreas: StateFlow<List<String>> = frameworkStore.framework
        .map { fw -> fw.pillars.filter { it.kind != PillarKind.BEHAVIOUR }.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The entry's own goal area if it names a real (non-behaviour) pillar, else the first goal area. */
    private suspend fun bestGoalArea(current: String?): String {
        val areas = frameworkStore.framework.first().pillars
            .filter { it.kind != PillarKind.BEHAVIOUR }.map { it.name }
        return bestGoalAreaOf(current, areas) ?: FALLBACK_AREA
    }

    companion object {
        /** Only reachable with a framework that has no placement pillars at all — a degenerate state. */
        const val FALLBACK_AREA = "Performance Goals"

        /**
         * Which category a "no specific project" resolve inherits: the entry's own AI-guessed category
         * when it names a real placement pillar, else the first one. Pure, so the chip's LABEL and the
         * actual WRITE are guaranteed to agree — the user can never be shown one category and given
         * another. Null = the framework has no placement pillars.
         */
        fun bestGoalAreaOf(current: String?, areas: List<String>): String? {
            val keep = current?.takeIf { c -> areas.any { it.equals(c, ignoreCase = true) } }
            return keep ?: areas.firstOrNull()
        }
    }
}
