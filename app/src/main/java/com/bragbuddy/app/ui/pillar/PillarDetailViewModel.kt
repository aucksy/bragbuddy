package com.bragbuddy.app.ui.pillar

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.deliverable.DeliverableRepository
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.ui.home.OUTSIDE_PROJECT_LABEL
import com.bragbuddy.app.ui.home.ProjectBullets
import com.bragbuddy.app.ui.home.UNCATEGORIZED_ID
import com.bragbuddy.app.ui.home.UNCATEGORIZED_LABEL
import com.bragbuddy.app.ui.home.behaviourEvidence
import com.bragbuddy.app.ui.home.goalProjectGroups
import com.bragbuddy.app.ui.home.uncategorizedEntries
import com.bragbuddy.app.ui.home.uncategorizedPillar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Argument key for the pillar id passed on the nav route "pillar/{pillarId}". */
const val ARG_PILLAR_ID = "pillarId"

/** Optional query arg: when set, the screen is scoped to a single folder (the "See more" target from
 *  Home's inline folder expansion) instead of listing every folder in the pillar. */
const val ARG_FOLDER = "folder"

/** Everything the deep pillar screen renders. [found] is false if the id no longer matches a pillar
 *  (e.g. the framework was edited while the screen was open) → the screen shows a graceful fallback. */
data class PillarDetail(
    val found: Boolean = false,
    val name: String = "",
    val blurb: String = "",
    val colorIndex: Int = 0,
    val isBehaviour: Boolean = false,
    /** The catch-all "Uncategorized" view — triage only (no add-project / add-detail affordances). */
    val synthetic: Boolean = false,
    /** Scoped to one folder ("See more" from Home): render its entries directly (no folder headers,
     *  no add-project / add-detail); [name] is the folder name and [projects] holds just that one. */
    val singleFolder: Boolean = false,
    val projects: List<ProjectBullets> = emptyList(),
    val evidence: List<EntryEntity> = emptyList(),
    /**
     * The **category (goal area)** this screen is showing — stated explicitly rather than re-derived,
     * because it is NOT always [name]: in the single-folder view [name] is the *folder*, and the pillar
     * name rides in [blurb]. Every deliverable action scopes by it (a deliverable is unique by
     * `(name, project, goalArea)`), so both the screen and the VM must read the same one — deriving it
     * in two places is how they drift, and a wrong area silently no-ops the action or writes elsewhere.
     * Blank for the behaviour / synthetic / not-found views, which own no deliverables.
     */
    val goalArea: String = "",
)

/**
 * The deep pillar view (Design System §1 · "Tap a pillar → add detail"): its blurb, its projects
 * with dated bullets (goal / growth pillars), or the entries that evidence it (behaviour pillars),
 * plus per-entry edit / delete. Reuses the same pure grouping the Home overview uses, so the two
 * views can never disagree about where an entry sits.
 */
@HiltViewModel
class PillarDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: EntryRepository,
    private val frameworkStore: FrameworkStore,
    private val projects: ProjectRepository,
    private val deliverablesRepo: DeliverableRepository,
) : ViewModel() {

    private val pillarId: String = savedStateHandle.get<String>(ARG_PILLAR_ID).orEmpty()
    private val folderArg: String = savedStateHandle.get<String>(ARG_FOLDER).orEmpty()

    val detail: StateFlow<PillarDetail> = combine(
        repository.observeAll(),
        frameworkStore.framework,
        projects.observeActive(),
        deliverablesRepo.observeAll(),
    ) { entries, framework, folders, dels ->
        val processed = entries.filter { it.status == EntryStatus.PROCESSED }
        val folderName = folderArg.trim()
        if (pillarId == UNCATEGORIZED_ID) {
            val orphans = uncategorizedEntries(processed, framework)
            return@combine PillarDetail(
                found = orphans.isNotEmpty(),
                name = if (folderName.isNotBlank()) OUTSIDE_PROJECT_LABEL else UNCATEGORIZED_LABEL,
                blurb = uncategorizedPillar().blurb,
                colorIndex = framework.pillars.size,
                isBehaviour = false,
                synthetic = true,
                singleFolder = folderName.isNotBlank(),
                projects = listOf(ProjectBullets(OUTSIDE_PROJECT_LABEL, isOutside = true, entries = orphans)),
            )
        }
        val index = framework.pillars.indexOfFirst { it.id == pillarId }
        val pillar = framework.pillars.getOrNull(index) ?: return@combine PillarDetail(found = false)
        if (pillar.kind == PillarKind.BEHAVIOUR) {
            PillarDetail(
                found = true,
                name = pillar.name,
                blurb = pillar.blurb,
                colorIndex = index,
                isBehaviour = true,
                evidence = behaviourEvidence(processed, pillar),
            )
        } else {
            val allGroups = goalProjectGroups(processed, folders, pillar, dels)
            if (folderName.isNotBlank()) {
                // "See more" from Home → scope to just that one folder's entries.
                val isOutside = folderName.equals(OUTSIDE_PROJECT_LABEL, ignoreCase = true)
                val match = allGroups.firstOrNull {
                    if (isOutside) it.isOutside else it.name.equals(folderName, ignoreCase = true)
                }
                PillarDetail(
                    found = true,
                    name = if (isOutside) OUTSIDE_PROJECT_LABEL else (match?.name ?: folderName),
                    blurb = pillar.name,
                    colorIndex = index,
                    isBehaviour = false,
                    singleFolder = true,
                    projects = listOfNotNull(match),
                    goalArea = pillar.name,
                )
            } else {
                PillarDetail(
                    found = true,
                    name = pillar.name,
                    blurb = pillar.blurb,
                    colorIndex = index,
                    isBehaviour = false,
                    projects = allGroups,
                    goalArea = pillar.name,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PillarDetail(found = true))

    /** Goal-area folders only — valid "Move to" targets for the entry-detail picker (a behaviour-area
     *  folder isn't a placement slot; moving there would strand the entry in the catch-all). */
    val folders: StateFlow<List<ProjectEntity>> = combine(
        projects.observeActive(), frameworkStore.framework,
    ) { fs, fw ->
        val goalNames = fw.pillars.filter { it.kind != PillarKind.BEHAVIOUR }.map { it.name.trim().lowercase() }.toSet()
        fs.filter { it.goalArea.trim().lowercase() in goalNames }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The active framework — feeds the entry-detail Recategorize picker. */
    val framework: StateFlow<Framework> = frameworkStore.framework
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Framework.DEFAULT)

    /** Every deliverable — feeds the move sheet's third picker level (see [HomeViewModel.deliverables]
     *  for why it's deliberately unfiltered). */
    val deliverables: StateFlow<List<DeliverableEntity>> = deliverablesRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- Deliverable CRUD (v0.33.0) — mirrors HomeViewModel; identity is the name triple.
    // This screen always knows its own category, so callers pass the project and it supplies the area.

    /** This screen's category — the one [PillarDetail.goalArea] the screen renders, so an action can
     *  never scope to a different area than the header the user is looking at. Blank (→ every action
     *  below no-ops) for the behaviour / synthetic / not-found views, which own no deliverables. */
    private fun currentArea(): String = detail.value.goalArea.trim()

    fun createDeliverable(name: String, project: String) = viewModelScope.launch {
        val area = currentArea().ifBlank { return@launch }
        runCatching { deliverablesRepo.create(name, project, area) }
    }

    /** Rename a deliverable. The row and every record that references it move together, in one
     *  transaction inside the processor — see [EntryRepository.renameDeliverable]. */
    fun renameDeliverableByName(oldName: String, project: String, newName: String) = viewModelScope.launch {
        val area = currentArea().ifBlank { return@launch }
        val d = runCatching { deliverablesRepo.byIdentity(oldName, project, area) }.getOrNull() ?: return@launch
        runCatching { repository.renameDeliverable(d.id, newName, d.description) }
    }

    fun setDeliverableDoneByName(name: String, project: String, done: Boolean) = viewModelScope.launch {
        val area = currentArea().ifBlank { return@launch }
        val d = runCatching { deliverablesRepo.byIdentity(name, project, area) }.getOrNull() ?: return@launch
        runCatching { deliverablesRepo.setDone(d.id, done) }
    }

    /** Delete the grouping — never its entries (see [HomeViewModel.deleteDeliverableByName]). */
    fun deleteDeliverableByName(name: String, project: String) = viewModelScope.launch {
        val area = currentArea().ifBlank { return@launch }
        val d = runCatching { deliverablesRepo.byIdentity(name, project, area) }.getOrNull() ?: return@launch
        runCatching { deliverablesRepo.delete(d.id) }
        repository.clearDeliverable(d.name, d.project, d.goalArea)
    }

    fun editText(id: Long, text: String) = repository.replaceText(id, text)

    fun delete(id: Long) = repository.delete(id)

    fun deleteMany(ids: Collection<Long>) = repository.deleteMany(ids.toList())

    fun setExtra(id: Long, value: Boolean) = repository.setExtra(id, value)

    fun setPinned(id: Long, value: Boolean) = repository.setPinned(id, value)

    /** Recategorize a filed entry: set its placement category + project AND its behaviour evidence,
     *  no AI re-call (Phase 2 · fix-a-wrong-category). Supersedes the old reassign/"Move". */
    fun recategorize(
        entry: EntryEntity,
        goalArea: String,
        project: String,
        deliverable: String?,
        demonstrates: List<String>,
        createDeliverable: Boolean = false,
    ) = repository.recategorize(entry.id, goalArea, project, deliverable, demonstrates, createDeliverable)

    /** Add a project folder under this pillar's goal area (goal / growth pillars only). */
    fun createFolder(name: String) {
        val area = detail.value.name.ifBlank { return }
        viewModelScope.launch { projects.create(name, area) }
    }
}
