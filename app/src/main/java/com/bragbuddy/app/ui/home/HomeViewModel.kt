package com.bragbuddy.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.ImpactSuggestRequest
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.entry.TextCaps
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.impact.ImpactCandidates
import com.bragbuddy.app.data.deliverable.DeliverableRepository
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.INBOX_PLACEMENT
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.net.ConnectivityMonitor
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.data.retention.RetentionPolicy
import com.bragbuddy.app.data.summary.SummaryStore
import com.bragbuddy.app.reminder.ReliabilityCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    private val deliverablesRepo: DeliverableRepository,
    private val frameworkStore: FrameworkStore,
    private val settings: SettingsStore,
    private val summaryStore: SummaryStore,
    private val aiProvider: AiProvider,
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
        deliverablesRepo.observeAll(),
    ) { entries, framework, folders, dels ->
        buildHomeDoc(entries, framework, folders, dels)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        // isEmpty=false initially so the first frame (before the DB/framework emit) doesn't flash the
        // empty-state CTA for a user who actually has content; the real value arrives immediately.
        HomeDoc(
            processing = emptyList(), waiting = emptyList(), goals = emptyList(),
            behaviours = emptyList(), inbox = null, isEmpty = false,
        ),
    )

    /**
     * A "Filed ✓ → <goal area>" confirmation to flash as a snackbar the moment an entry finishes
     * filing (M2). Fires only on a genuine RAW/INBOX/FAILED/PENDING → PROCESSED transition observed
     * during this session — the first snapshot is a silent seed (so opening the app doesn't replay
     * every past win) and a Drive restore, which inserts rows already PROCESSED with no prior state,
     * never triggers it.
     */
    private val _filedConfirmation = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val filedConfirmation: SharedFlow<String> = _filedConfirmation.asSharedFlow()

    init {
        viewModelScope.launch {
            var prev: Map<Long, EntryStatus>? = null
            repository.observeAll().collect { entries ->
                val was = prev
                if (was != null) {
                    val filed = entries.filter { e ->
                        val before = was[e.id]
                        e.status == EntryStatus.PROCESSED && before != null && before != EntryStatus.PROCESSED
                    }
                    // A single capture flips 1 (or a few split siblings) rows to PROCESSED. A wholesale
                    // Drive restore flips many at once (its transaction emits one snapshot) — treat that
                    // as bulk and stay silent, so a restore never machine-guns "Filed ✓".
                    if (filed.isNotEmpty() && filed.size <= BULK_FILE_THRESHOLD) {
                        val cat = filed.last().goalCategory?.trim()
                            ?.takeIf { it.isNotEmpty() && !it.equals(INBOX_PLACEMENT, ignoreCase = true) }
                        _filedConfirmation.tryEmit(if (cat != null) "Filed ✓ → $cat" else "Filed ✓")
                    }
                }
                prev = entries.associate { it.id to it.status }
            }
        }
    }

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
     *  dismissing today's warning still lets a NEW risk resurface it later.
     *
     *  Gated on `notifPrimerHandled` (Phase 3): the card stays quiet until the first-run notification
     *  primer has been shown/acted upon, so the primer popup and this card never nag about notifications
     *  at the same time. Declining the primer records the current risk as acknowledged (see
     *  `MainViewModel.markNotifPrimerDeclined`), so it also doesn't turn around and re-nag. */
    val showReliabilityCard: StateFlow<Boolean> = combine(
        settings.settings, refreshTick,
    ) { s, _ ->
        val health = ReliabilityCheck.check(appContext)
        s.reminderEnabled && s.notifPrimerHandled && health.atRisk &&
            health.riskSignature != s.reliabilityDismissedRisks
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissReliabilityCard() = viewModelScope.launch {
        settings.setReliabilityDismissedRisks(ReliabilityCheck.check(appContext).riskSignature)
    }

    // ---------------- M2 · Home one-slot nudge queue ----------------

    /**
     * The single dismissible nudge card Home may show (the "card diet" — at most ONE, so the record
     * starts near the top). The two auto strips (filing / waiting-offline) are status-driven and
     * render independently; this only arbitrates the four *dismissible* cards by fixed priority:
     * **reliability > daily nudge > impact > preview**. (The notification primer is a MainScaffold
     * overlay that gates the reliability card, not a Home card.)
     */
    sealed interface HomeNudge {
        data object None : HomeNudge
        data object Reliability : HomeNudge
        data object Daily : HomeNudge
        data object Impact : HomeNudge
        data class Preview(val count: Int) : HomeNudge
    }
    // `activeNudge` (the resolver) is declared AFTER the impact flows below — it references them, and a
    // property initializer can't read a `val` declared later (it would be null at construction).

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

    /** The active framework — feeds the entry-detail Recategorize picker (placement categories +
     *  behaviour-evidence options). */
    val framework: StateFlow<Framework> = frameworkStore.framework
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Framework.DEFAULT)

    /** Every deliverable — feeds the move sheet's third picker level. Unfiltered on purpose: a move is
     *  a correction, so it must be able to reach any deliverable, including a Done one (that's where a
     *  win from months ago genuinely belongs). The picker scopes by the chosen (category, project). */
    val deliverables: StateFlow<List<DeliverableEntity>> = deliverablesRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------------- Deliverable CRUD (v0.33.0) ----------------
    //
    // Addressed by (name, project, goalArea) rather than row id, because that triple IS a deliverable's
    // identity — it's what the entries reference and what the rendered document holds. Making the UI
    // carry ids would mean two competing notions of "which deliverable", and the screens would have to
    // thread an id purely to hand it back.

    fun createDeliverable(name: String, project: String, goalArea: String) = viewModelScope.launch {
        runCatching { deliverablesRepo.create(name, project, goalArea) }
    }

    /** Rename a deliverable. The row and every record that references it move together, in one
     *  transaction inside the processor — see [EntryRepository.renameDeliverable]. */
    fun renameDeliverableByName(
        oldName: String,
        project: String,
        goalArea: String,
        newName: String,
    ) = viewModelScope.launch {
        val d = runCatching { deliverablesRepo.byIdentity(oldName, project, goalArea) }.getOrNull() ?: return@launch
        // The rename itself runs on the APP scope inside the repository — it can wait on the processing
        // mutex for the length of a live AI call, and this coroutine dies the moment the sheet closes.
        repository.renameDeliverable(d.id, newName, d.description)
    }

    fun setDeliverableDoneByName(
        name: String,
        project: String,
        goalArea: String,
        done: Boolean,
    ) = viewModelScope.launch {
        val d = runCatching { deliverablesRepo.byIdentity(name, project, goalArea) }.getOrNull() ?: return@launch
        runCatching { deliverablesRepo.setDone(d.id, done) }
    }

    /** Delete the grouping — never its entries. They fall back to listing plainly under the project, so
     *  nothing logged is ever lost to a structural change (the "never lose an entry" invariant). */
    fun deleteDeliverableByName(name: String, project: String, goalArea: String) = viewModelScope.launch {
        val d = runCatching { deliverablesRepo.byIdentity(name, project, goalArea) }.getOrNull() ?: return@launch
        runCatching { deliverablesRepo.delete(d.id) }
        repository.clearDeliverable(d.name, d.project, d.goalArea)
    }

    // ---------------- Phase 4 · "Add impact" list ----------------

    /** UI state while fetching (or falling back for) the project-aware coaching question. */
    sealed interface ImpactSuggestUi {
        data object Loading : ImpactSuggestUi
        /** [isAi] false = the generic fallback (no key / AI failed) rather than a tailored question. */
        data class Ready(val question: String, val isAi: Boolean) : ImpactSuggestUi
    }

    /** Filed wins that would be stronger with a number (see [ImpactCandidates]). Gated on a Groq key:
     *  the add-impact merge re-runs the categorizer, so without AI the card is hidden (and, in practice,
     *  there'd be no PROCESSED entries to list anyway). */
    val impactCandidates: StateFlow<List<EntryEntity>> = combine(
        repository.observeAll(), settings.settings,
    ) { entries, s ->
        if (!s.aiEnabled) emptyList() else ImpactCandidates.from(entries)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Session-only "not now" for the impact card (resets on next app open; there's still un-quantified
     *  work, so it's fine to offer again — just not naggy within a session). */
    private val _impactCardDismissed = MutableStateFlow(false)
    val impactCardDismissed: StateFlow<Boolean> = _impactCardDismissed.asStateFlow()
    fun dismissImpactCard() { _impactCardDismissed.value = true }

    /** The M2 one-slot resolver (declared here, after the impact flows it reads). Priority:
     *  reliability > daily > impact > preview; [HomeNudge.None] when nothing qualifies. */
    val activeNudge: StateFlow<HomeNudge> = combine(
        showReliabilityCard,
        showDailyNudge,
        combine(impactCandidates, impactCardDismissed) { c, dismissed -> c.isNotEmpty() && !dismissed },
        previewBannerCount,
    ) { reliability, daily, impact, preview ->
        when {
            reliability -> HomeNudge.Reliability
            daily -> HomeNudge.Daily
            impact -> HomeNudge.Impact
            preview != null -> HomeNudge.Preview(preview)
            else -> HomeNudge.None
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeNudge.None)

    /** The coaching question for the entry whose "Add impact" sheet is open (null = no sheet). */
    private val _impactSuggestion = MutableStateFlow<ImpactSuggestUi?>(null)
    val impactSuggestion: StateFlow<ImpactSuggestUi?> = _impactSuggestion.asStateFlow()
    /** The in-flight suggestion fetch — cancelled when a new sheet opens or the sheet closes, so a slow
     *  result for a previously-open win can't flash into the sheet now showing a different one. */
    private var suggestJob: Job? = null

    /** Fetch the project-aware "what to quantify" question for [entry] (called when its sheet opens).
     *  Falls back to a generic prompt on no-key / AI failure — never blocks adding the number. */
    fun loadImpactSuggestion(entry: EntryEntity) {
        suggestJob?.cancel()
        _impactSuggestion.value = ImpactSuggestUi.Loading
        suggestJob = viewModelScope.launch {
            val s = settings.settings.first()
            val detail = TextCaps.cap(
                folders.value.firstOrNull { it.name.equals(entry.project, ignoreCase = true) }
                    ?.description.orEmpty(),
            )
            val ai = s.groqApiKey.isNotBlank()
            val question = if (!ai) {
                GENERIC_IMPACT_QUESTION
            } else {
                aiProvider.suggestImpact(
                    ImpactSuggestRequest(
                        bullet = entry.bullet.orEmpty(),
                        project = entry.project.orEmpty(),
                        projectDetail = detail,
                        goalArea = entry.goalCategory.orEmpty(),
                        role = s.jobRole,
                    ),
                ).getOrNull()?.question?.takeIf { it.isNotBlank() } ?: GENERIC_IMPACT_QUESTION
            }
            _impactSuggestion.value = ImpactSuggestUi.Ready(
                question = question,
                isAi = ai && question != GENERIC_IMPACT_QUESTION,
            )
        }
    }

    fun clearImpactSuggestion() {
        suggestJob?.cancel()
        _impactSuggestion.value = null
    }

    /** Merge the user-typed impact into the win as one combined bullet (keeps its placement). */
    fun addImpact(entry: EntryEntity, text: String) = repository.addImpact(entry, text)

    /** Per-entry actions for the inline folder expansion on Home (mirror the deep pillar view). */
    fun editText(id: Long, text: String) = repository.replaceText(id, text)

    fun delete(id: Long) = repository.delete(id)

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

    /** Create a folder under [goalArea] (or the framework's first goal category when null). */
    fun createFolder(name: String, goalArea: String? = null) = viewModelScope.launch {
        val area = goalArea?.takeIf { it.isNotBlank() }
            ?: frameworkStore.framework.first().goalAreas.firstOrNull()?.name ?: "Performance Goals"
        projects.create(name, area)
    }

    private companion object {
        /** The fallback coaching prompt when there's no key / the AI can't tailor one. Kept identical to
         *  the [StubAiProvider] wording so the experience reads the same with or without AI. */
        const val GENERIC_IMPACT_QUESTION = "What changed or improved — can you put a number on it?"

        /** More than this many rows flipping to PROCESSED in one snapshot = a bulk restore, not a
         *  live capture — suppress the "Filed ✓" confirmation for it. */
        const val BULK_FILE_THRESHOLD = 3
    }
}
