package com.bragbuddy.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.net.ConnectivityMonitor
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.data.retention.RetentionPolicy
import com.bragbuddy.app.data.summary.SummaryStore
import com.bragbuddy.app.reminder.ReliabilityCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/**
 * Home state (Phase 3): the flat entry list is shaped into the **living document** ([HomeDoc]) —
 * goal/growth pillars with project cards, behaviour pillars gathering evidence, and the Inbox peek.
 * Also carries the project **folders** (for quick create) and the first-run "what's your role?"
 * prompt. Per-entry editing now lives one tap in, on the deep pillar screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: EntryRepository,
    private val projects: ProjectRepository,
    private val frameworkStore: FrameworkStore,
    private val settings: SettingsStore,
    private val summaryStore: SummaryStore,
    connectivity: ConnectivityMonitor,
) : ViewModel() {

    /** Re-evaluates the time/system-state cards (daily nudge, reminder health) — bumped on resume,
     *  since neither "the reminder time passed" nor a granted system toggle emits a Flow. */
    private val refreshTick = MutableStateFlow(0)
    fun refresh() { refreshTick.value++ }

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
        HomeDoc(
            processing = emptyList(), waitingVoice = emptyList(), goals = emptyList(),
            behaviours = emptyList(), inbox = null, isEmpty = false,
        ),
    )

    /** Show the gentle first-run role prompt until the role is set or the prompt is dismissed. */
    val showRolePrompt: StateFlow<Boolean> = settings.settings
        .map { it.jobRole.isBlank() && !it.rolePromptDismissed }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun saveRole(role: String) = viewModelScope.launch { settings.setJobRole(role) }

    fun dismissRolePrompt() = viewModelScope.launch { settings.dismissRolePrompt() }

    // ---------------- Phase 7 · retention + reliability cards ----------------

    /** The on-open "you haven't logged today" fallback (PRD P0-1): the reminder's safety net,
     *  shown only after the reminder time has passed with nothing captured today. */
    val showDailyNudge: StateFlow<Boolean> = combine(
        repository.observeAll(), settings.settings, refreshTick,
    ) { entries, s, _ ->
        RetentionPolicy.dailyNudgeVisible(
            nowMillis = System.currentTimeMillis(),
            zone = ZoneId.systemDefault(),
            reminderEnabled = s.reminderEnabled,
            reminderHour = s.reminderHour,
            reminderMinute = s.reminderMinute,
            lastEntryAtMillis = entries.maxOfOrNull { it.createdAt },
            dismissedDayKey = s.dailyNudgeDismissedDay,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissDailyNudge() = viewModelScope.launch {
        settings.setDailyNudgeDismissedDay(RetentionPolicy.dayKey(System.currentTimeMillis(), ZoneId.systemDefault()))
    }

    /** The Design §7 early-preview banner: the filed-entry count to show ("From just N entries"),
     *  or null when hidden. Visible from 5 filed entries until the first summary ever generates. */
    val previewBannerCount: StateFlow<Int?> = combine(
        repository.observeAll(), summaryStore.cache, settings.settings,
    ) { entries, cache, s ->
        val processedCount = entries.count { it.status == EntryStatus.PROCESSED }
        if (RetentionPolicy.previewBannerVisible(processedCount, cache.isNotEmpty(), s.previewBannerDismissed)) {
            processedCount
        } else {
            null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun dismissPreviewBanner() = viewModelScope.launch { settings.setPreviewBannerDismissed(true) }

    /** The quiet "your phone may silence the reminder" card — only when a real risk is detected
     *  (notifications/channel blocked, exact alarms revoked, or battery optimization on an
     *  aggressive OEM) AND its risk set differs from the one the user already dismissed — so
     *  dismissing today's warning still lets a NEW risk resurface it later. */
    val showReliabilityCard: StateFlow<Boolean> = combine(
        settings.settings, refreshTick,
    ) { s, _ ->
        val health = ReliabilityCheck.check(appContext)
        s.reminderEnabled && health.atRisk && health.riskSignature != s.reliabilityDismissedRisks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissReliabilityCard() = viewModelScope.launch {
        settings.setReliabilityDismissedRisks(ReliabilityCheck.check(appContext).riskSignature)
    }

    /** Drives the waiting-voice strip's copy: offline → "waiting for network"; online → the honest
     *  "retrying shortly" (a queued clip while online usually means a service/key hiccup). */
    val isOnline: StateFlow<Boolean> = connectivity.isOnline

    /** Goal-area folders only — valid "Move to" targets for the entry-detail picker (a behaviour-area
     *  folder isn't a placement slot; moving there would strand the entry in the catch-all). */
    val folders: StateFlow<List<ProjectEntity>> = combine(
        projects.observeActive(), frameworkStore.framework,
    ) { fs, fw ->
        val goalNames = fw.pillars.filter { it.kind != PillarKind.BEHAVIOUR }.map { it.name.trim().lowercase() }.toSet()
        fs.filter { it.goalArea.trim().lowercase() in goalNames }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-entry actions for the inline folder expansion on Home (mirror the deep pillar view). */
    fun editText(id: Long, text: String) = repository.replaceText(id, text)

    fun delete(id: Long) = repository.delete(id)

    fun setExtra(id: Long, value: Boolean) = repository.setExtra(id, value)

    fun setPinned(id: Long, value: Boolean) = repository.setPinned(id, value)

    /** Move a filed entry into [projectName] (an existing folder); resolves its goal area. No AI re-call. */
    fun reassignToProject(entry: EntryEntity, projectName: String) = viewModelScope.launch {
        val folder = folders.value.firstOrNull { it.name.equals(projectName, ignoreCase = true) }
        val goalArea = folder?.goalArea ?: bestGoalArea(entry.goalCategory)
        if (folder == null) projects.create(projectName, goalArea)
        repository.reassign(entry.id, projectName, goalArea)
    }

    /** Move a filed entry to "Outside project", kept under its best goal area. */
    fun reassignOutside(entry: EntryEntity) = viewModelScope.launch {
        repository.reassign(entry.id, OUTSIDE_PROJECT, bestGoalArea(entry.goalCategory))
    }

    private suspend fun bestGoalArea(current: String?): String {
        val areas = frameworkStore.framework.first().pillars.filter { it.kind != PillarKind.BEHAVIOUR }
        return current?.takeIf { c -> areas.any { it.name.equals(c, ignoreCase = true) } }
            ?: areas.firstOrNull()?.name ?: "Performance Goals"
    }

    /** Create a folder under [goalArea] (or the framework's first goal category when null). */
    fun createFolder(name: String, goalArea: String? = null) = viewModelScope.launch {
        val area = goalArea?.takeIf { it.isNotBlank() }
            ?: frameworkStore.framework.first().goalAreas.firstOrNull()?.name ?: "Performance Goals"
        projects.create(name, area)
    }
}
