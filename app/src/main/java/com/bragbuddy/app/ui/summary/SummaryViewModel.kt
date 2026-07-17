package com.bragbuddy.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiPrompts
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.SummaryRequest
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.deliverable.DeliverableRepository
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.OUTSIDE_PROJECT
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.data.net.ConnectivityMonitor
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import com.bragbuddy.app.data.rollup.AggregatedRollup
import com.bragbuddy.app.data.rollup.DeliverableFact
import com.bragbuddy.app.data.rollup.PeriodWindow
import com.bragbuddy.app.data.rollup.ReviewPeriods
import com.bragbuddy.app.data.rollup.RollupAggregator
import com.bragbuddy.app.data.rollup.RollupStore
import com.bragbuddy.app.data.rollup.SummaryLength
import com.bragbuddy.app.data.rollup.SummaryPeriod
import com.bragbuddy.app.data.summary.BulletEdit
import com.bragbuddy.app.data.summary.CachedSummary
import com.bragbuddy.app.data.summary.PlacementOverride
import com.bragbuddy.app.data.summary.RestoredNote
import com.bragbuddy.app.data.summary.SummaryOverrides
import com.bragbuddy.app.data.summary.SummaryResolver
import com.bragbuddy.app.data.summary.SummaryStore
import com.bragbuddy.app.data.summary.applyOverrides
import com.bragbuddy.app.data.summary.retargetRestored
import com.bragbuddy.app.data.summary.summaryKey
import com.bragbuddy.app.data.usage.UsageMeter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Phase 5 · the appraisal summary. Reads the **running rollup** (never the raw log), windows it to the
 * chosen period, and turns it into a curated write-up via the already-baked PART B prompt behind the
 * [AiProvider] seam. A generated summary is a cached artefact: viewing the last one is free; only an
 * explicit **Regenerate** calls the model, and only when the input changed (the input signature).
 * Pin lives on the entry (v0.12.0) and rides in live; promote/demote reorders the cached draft
 * locally (no model call); each *fresh* generation is metered via [UsageMeter].
 */
@HiltViewModel
class SummaryViewModel @Inject constructor(
    private val rollupStore: RollupStore,
    private val summaryStore: SummaryStore,
    private val frameworkStore: FrameworkStore,
    private val settingsStore: SettingsStore,
    private val entryRepository: EntryRepository,
    private val projectRepository: ProjectRepository,
    private val deliverableRepository: DeliverableRepository,
    private val aiProvider: AiProvider,
    private val usageMeter: UsageMeter,
    private val connectivity: ConnectivityMonitor,
) : ViewModel() {

    /** What surface to show. */
    enum class Phase { LOADING, NEEDS_KEY, EMPTY, NOT_GENERATED, READY }

    /** Everything the screen renders + the captured inputs so [generate] is a pure step. */
    data class ScreenState(
        val phase: Phase,
        val period: SummaryPeriod,
        val length: SummaryLength,
        val window: PeriodWindow,
        val entryCount: Int,
        val framework: Framework,
        val pinnedBullets: List<String>,
        val cached: CachedSummary?,
        val isStale: Boolean,
        val hasContent: Boolean,
        val gen: GenInputs,
        /** Every project folder the user has, across all placement categories — the retag picker's
         *  universe. Deliberately NOT filtered to the card's own category: the whole point is moving a
         *  wrongly-filed win to a project somewhere else, including one no summary has ever mentioned. */
        val allFolders: List<FolderRef> = emptyList(),
        /**
         * What the model was offered but didn't use — the REAL set-aside (v0.31.0). Derived client-side
         * (rollup candidates − the lines actually rendered), so each carries its true text, its area and
         * its source entry ids. The model's own `setAside` notes are categorical labels ("Routine
         * check-ins") with nothing behind them, so they can explain but never be restored faithfully.
         */
        val setAsideItems: List<SetAsideItem> = emptyList(),
    )

    /** A project folder: its name and the category it lives under. */
    data class FolderRef(val name: String, val goalArea: String)

    /** One genuinely-dropped rollup candidate, restorable as itself. */
    data class SetAsideItem(
        val bullet: String,
        val area: String,
        val metric: String?,
        val entryIds: List<Long>,
    )

    /** Snapshot of the model-call inputs for the current selection. */
    data class GenInputs(
        val key: String,
        val signature: String,
        val frameworkBlock: String,
        val role: String,
        val rollupString: String,
        val pinnedForPrompt: List<String>,
    )

    private val period = MutableStateFlow(SummaryPeriod.YEAR_END)
    private val length = MutableStateFlow(SummaryLength.ONE_PAGE)

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun consumeMessage() { _message.value = null }

    private val inputs = combine(
        rollupStore.state,
        frameworkStore.framework,
        settingsStore.settings,
        entryRepository.observePinned(),
        // Folders + deliverables are nested into ONE flow so this stays combine's 5-argument TYPED form
        // (a 6th drops to the untyped vararg and loses compile-time safety on a hot path). Deliverables
        // belong in `inputs` rather than beside it because the rollup now depends on them (v0.34.0), and
        // both retag write-back sites recompute the signature from `inputs.first()` — reading them from
        // a separate WhileSubscribed StateFlow there could see an empty list and compute a signature the
        // screen never would, permanently stranding the summary as stale.
        combine(projectRepository.observeActive(), deliverableRepository.observeAll()) { f, d -> f to d },
    ) { rollup, fw, settings, pinned, structure ->
        Inputs(
            rollup.items, fw, settings.groqApiKey, settings.jobRole, settings.reviewYearStartMonth,
            pinned, structure.first, structure.second,
        )
    }

    /**
     * Every deliverable — the retag picker's third level (v0.33.0), unfiltered for the same reason
     * [ScreenState.allFolders] is: a correction must reach anywhere.
     *
     * Kept as its own flow rather than read off [inputs] because the two answer different questions and
     * must NOT converge: this one is the PICKER's options, deliberately **unfiltered** (a correction has
     * to reach anywhere — including a Done deliverable, and one under a project this summary never
     * mentions), whereas [Inputs.deliverables] feeds the ROLLUP, where `done` is a fact to report rather
     * than a filter.
     *
     * Since v0.34.0 a card CAN now carry a deliverable (the model files into them). That does **not**
     * make it a preselect: the sheet's deliverable axis stays opt-in ("Leave as is"), which is the
     * v0.33.1 F1 fix and is still right for the same reason — one card can be a merge of several
     * entries with DIFFERENT deliverables, so any single preselected value would silently retag the
     * rest of them on Apply.
     */
    val allDeliverables: StateFlow<List<DeliverableEntity>> = deliverableRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class Inputs(
        val items: List<com.bragbuddy.app.data.rollup.RollupItem>,
        val framework: Framework,
        val groqKey: String,
        val role: String,
        val reviewStartMonth: Int,
        val pinned: List<EntryEntity>,
        val folders: List<ProjectEntity>,
        /** Live deliverable rows — the rollup reads `done` from these (v0.34.0). */
        val deliverables: List<DeliverableEntity>,
    )

    val state: StateFlow<ScreenState?> = combine(
        inputs,
        combine(period, length) { p, l -> p to l },
        summaryStore.cache,
    ) { inp, sel, cache -> build(inp, sel.first, sel.second, cache) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Everything derivable from the rollup for one (period, length) selection. Extracted from [build]
     * so the retag write-back can recompute the input signature from the FRESH rollup with exactly the
     * same rule the screen uses — two implementations of a signature would silently disagree and either
     * strand the summary as permanently stale or falsely mark it up to date.
     */
    private data class Computed(
        val window: PeriodWindow,
        val agg: AggregatedRollup,
        val rollupString: String,
        val frameworkBlock: String,
        val pinnedInWindow: List<EntryEntity>,
        val pinnedBullets: List<String>,
        val pinnedForPrompt: List<String>,
        val signature: String,
    )

    private fun compute(inp: Inputs, period: SummaryPeriod, length: SummaryLength): Computed {
        val window = ReviewPeriods.windowFor(period, inp.reviewStartMonth)
        // Length drives how many candidate highlights per area the model sees (Detailed = more).
        val agg = RollupAggregator.aggregate(
            inp.items, window.startMillis, window.endMillisExclusive, inp.framework,
            // v0.34.0 · supplies each deliverable's live Done state, which the rollup items can't carry.
            // It rides in the serialized rollup, so marking one Done correctly marks the summary stale.
            deliverables = inp.deliverables.map {
                DeliverableFact(name = it.name, project = it.project, goalArea = it.goalArea, done = it.done)
            },
            highlightCap = length.highlightCap,
        )
        val rollupString = RollupAggregator.serialize(agg)

        // Pinned entries in this window (PROCESSED only) → forced into the summary + tagged in the UI.
        val pinnedInWindow = inp.pinned.filter {
            it.status == EntryStatus.PROCESSED &&
                (it.occurredAt ?: it.createdAt) in window.startMillis until window.endMillisExclusive &&
                !it.bullet.isNullOrBlank()
        }
        val pinnedBullets = pinnedInWindow.mapNotNull { it.bullet?.trim()?.takeIf { b -> b.isNotBlank() } }
        val pinnedForPrompt = pinnedInWindow.map { e ->
            val area = e.goalCategory?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
            "${e.bullet?.trim()}$area"
        }

        val frameworkBlock = inp.framework.toPromptBlock()
        // The prompt-template fingerprint rides in the signature so a prompt change (an AI phase)
        // marks cached summaries stale even when the rollup itself didn't move (AI-2 review fix).
        val signature = RollupAggregator.signature(
            rollupString, pinnedForPrompt.joinToString("\n"), frameworkBlock, inp.role, period.name, length.name,
            AiPrompts.summaryTemplateFingerprint,
        )
        return Computed(window, agg, rollupString, frameworkBlock, pinnedInWindow, pinnedBullets, pinnedForPrompt, signature)
    }

    private fun build(
        inp: Inputs,
        period: SummaryPeriod,
        length: SummaryLength,
        cache: Map<String, CachedSummary>,
    ): ScreenState {
        val c = compute(inp, period, length)
        val key = "${period.name}::${length.name}"
        val cached = cache[key]
        val hasContent = !c.agg.isEmpty || c.pinnedInWindow.isNotEmpty()

        val gen = GenInputs(key, c.signature, c.frameworkBlock, inp.role, c.rollupString, c.pinnedForPrompt)

        val phase = when {
            inp.groqKey.isBlank() && cached == null -> Phase.NEEDS_KEY
            cached != null -> Phase.READY
            !hasContent -> Phase.EMPTY
            else -> Phase.NOT_GENERATED
        }
        val isStale = cached != null && cached.inputSignature != c.signature
        // v0.34.0 · snap each card's echoed deliverable onto one the rollup really named, for DISPLAY +
        // EXPORT. Applied here, at the single point the cached document becomes screen state, so the
        // rendered sub-headers and the copied `[Project ▸ Deliverable]` tags can't disagree. The stored
        // blob is left untouched — this is a lens over the model's answer, not an edit to it.
        val shown = cached?.let { cd ->
            cd.copy(
                result = cd.result.copy(
                    summary = cd.result.summary.copy(
                        goalAreas = cd.result.summary.goalAreas.map { ga ->
                            ga.copy(
                                achievements = resolveAchievementDeliverables(
                                    ga.achievements,
                                    c.agg.goalAreas.firstOrNull { it.name.equals(ga.name, ignoreCase = true) }
                                        ?.deliverables.orEmpty(),
                                ),
                            )
                        },
                    ),
                ),
            )
        }
        return ScreenState(
            phase = phase,
            period = period,
            length = length,
            window = c.window,
            entryCount = c.agg.entryCount,
            framework = inp.framework,
            pinnedBullets = c.pinnedBullets,
            cached = shown,
            isStale = isStale,
            hasContent = hasContent,
            gen = gen,
            allFolders = inp.folders.map { FolderRef(it.name, it.goalArea) },
            // Only meaningful when the cached summary matches the current rollup: a STALE summary was
            // built from a different aggregate, so "candidates − rendered" would surface brand-new
            // entries as "set aside" and offer to restore what was never left out. Blank until the user
            // regenerates against the fresh rollup.
            setAsideItems = if (isStale) emptyList() else deriveSetAside(c.agg, cached),
        )
    }

    /**
     * The genuinely-dropped candidates: everything the aggregate offered the model, minus everything
     * the rendered summary actually shows. This is what "see what was set aside, restore all or part of
     * it" needs — real bullets with real areas and real entry ids.
     *
     * Why derive rather than read `result.setAside`: those notes are a CATEGORICAL explanation the model
     * writes ("Routine check-ins" / "condensed to keep to one page"). Restoring one literally injected a
     * bullet reading "Routine check-ins" — a label masquerading as an achievement. The notes stay, as the
     * panel's explanation; the restorable list is computed here.
     */
    private fun deriveSetAside(agg: AggregatedRollup, cached: CachedSummary?): List<SetAsideItem> {
        val result = cached?.result ?: return emptyList()

        // "Accounted for" must be judged with the SAME fuzzy rule the rest of this feature uses, never by
        // exact key. The model rewords: the user deletes *its* phrasing ("Shipped checkout redesign to
        // 100% traffic"), while the candidate holds the RECORD's phrasing ("Shipped the checkout redesign
        // to 100% of traffic, cutting drop-off 18%"). Those normalize to different keys, so an exact-key
        // filter never fires — and the line the user just deleted reappears in Set-aside, in wording they
        // never saw, offering to restore what they removed. Feeding these texts into the used-set instead
        // routes them through the same Jaccard match as everything else.
        val accountedFor = cached.overrides.deleted +
            cached.overrides.edits.map { it.from } +
            cached.overrides.edits.map { it.to } +
            cached.overrides.restored.map { it.text } +
            // A line moved by the summary-only fallback has LEFT its origin area; without this its
            // candidates resurface as set aside and Restore all would duplicate the line into two areas.
            cached.overrides.moved.map { it.key }

        // Rendered anywhere in the document counts as rendered — a retag (write-back or fallback) can put
        // a line under an area whose rollup candidates live elsewhere, and per-area matching would call it
        // "dropped" from its origin while it is plainly on screen.
        val renderedAnywhere = result.summary.goalAreas.flatMap { it.achievements.map { a -> a.bullet } } +
            result.summary.development

        val used = renderedAnywhere + accountedFor
        return agg.goalAreas.flatMap { area ->
            SummaryResolver.dropped(area.highlights, used)
                .map { SetAsideItem(it.bullet, area.name, it.metric, it.ids) }
        }
    }

    fun selectPeriod(p: SummaryPeriod) { period.value = p }
    fun selectLength(l: SummaryLength) { length.value = l }

    /**
     * Generate (or regenerate) the summary for the current selection — the ONLY path that calls the
     * model. Guardrails (Build Brief § "Guardrails on generation"): never re-call the model for an
     * already up-to-date cached summary (viewing is free); meter only a FRESH generation. The
     * re-entrancy flag is set SYNCHRONOUSLY here (before the first suspend point) so a fast double-tap
     * — from the sheet's button OR the stale-status chip — can't launch two concurrent generations.
     */
    fun generate() {
        val s = state.value ?: return
        if (_generating.value) return
        if (s.gen.frameworkBlock.isBlank()) return
        // A cached summary whose input hasn't changed must NOT re-call the model (protects both the
        // sheet's "Regenerate" button and the status-chip path uniformly).
        if (s.cached != null && !s.isStale) { _message.value = "Already up to date."; return }
        if (!s.hasContent) { _message.value = "Nothing to summarise in this period yet."; return }
        // Calm offline state (Phase 7): say why up front instead of a spinner ending in an error.
        // Advisory only — if the callback never registered the signal may be stuck false, so fall
        // through to the real call (its onFailure already shows a calm error) rather than gate on it.
        if (connectivity.callbackRegistered && !connectivity.isOnline.value) {
            _message.value = "You're offline — connect to generate your summary."
            return
        }
        // Set the guard before any suspension (all calls are on the main dispatcher, so this is atomic
        // w.r.t. other generate() calls) — fixes the check-then-set race the reviewer found.
        _generating.value = true
        viewModelScope.launch {
            try {
                val key = settingsStore.settings.first().groqApiKey
                if (key.isBlank()) { _message.value = "Add your Groq key in Settings to generate a summary."; return@launch }
                val request = SummaryRequest(
                    period = s.period.promptLabel,
                    lengthCap = s.length.cap,
                    framework = s.gen.frameworkBlock,
                    pinned = s.gen.pinnedForPrompt,
                    rollup = s.gen.rollupString,
                    role = s.gen.role,
                )
                aiProvider.generateSummary(request).fold(
                    onSuccess = { result ->
                        // Meter only a FRESH generation (viewing a cached summary is free).
                        runCatching { usageMeter.recordSummaryGeneration() }
                        // Persist under editMutex + re-read the FRESHEST overrides so a local edit made
                        // WHILE the model was running isn't lost, and this write can't interleave with a
                        // concurrent mutateCached. Carry the user's edits/deletes/restores onto the fresh
                        // generation (deletes stay gone, restores stay in) so they survive the Regenerate.
                        editMutex.withLock {
                            val overrides = summaryStore.cache.first()[s.gen.key]?.overrides ?: SummaryOverrides()
                            summaryStore.put(
                                s.gen.key,
                                CachedSummary(
                                    period = s.period.name,
                                    length = s.length.name,
                                    inputSignature = s.gen.signature,
                                    result = applyOverrides(result, overrides, currentDevAreas()),
                                    periodRangeText = s.window.rangeText,
                                    generatedAtMillis = System.currentTimeMillis(),
                                    overrides = overrides,
                                ),
                            )
                        }
                        maybeWarnSoftCap()
                    },
                    onFailure = {
                        _message.value = "Couldn't generate the summary — check your connection and try again."
                    },
                )
            } finally {
                _generating.value = false
            }
        }
    }

    /**
     * Swap two achievements within a goal area, addressed by their index in the area's flat
     * achievements list (local edit of the cached draft; no model call). The Summary screen scopes
     * the up/down arrows to within a project folder (item 5) by passing the two adjacent-in-folder
     * flat slots; a flat (un-foldered) area passes i and i±1. Keeps the SAME inputSignature — this
     * changes the output, not the input, so the summary stays "Up to date".
     */
    fun swapAchievements(areaName: String, indexA: Int, indexB: Int) = mutateCached { cached ->
        if (indexA == indexB) return@mutateCached null
        val areas = cached.result.summary.goalAreas.toMutableList()
        val ai = areas.indexOfFirst { it.name == areaName }
        if (ai < 0) return@mutateCached null
        val achievements = areas[ai].achievements.toMutableList()
        if (indexA !in achievements.indices || indexB !in achievements.indices) return@mutateCached null
        val tmp = achievements[indexA]
        achievements[indexA] = achievements[indexB]
        achievements[indexB] = tmp
        areas[ai] = areas[ai].copy(achievements = achievements)
        cached.copy(result = cached.result.copy(summary = cached.result.summary.copy(goalAreas = areas)))
    }

    /**
     * Delete a summary pointer (feature #1). Suppressed via the persistent overrides so a Regenerate
     * can't bring it back; the user's saved RECORD on Home is never touched. Same inputSignature (a
     * local output edit, so the summary stays "Up to date").
     */
    fun deletePointer(text: String) = mutateCached { cached ->
        if (text.isBlank()) return@mutateCached null
        val overrides = cached.overrides.copy(
            deleted = (cached.overrides.deleted + summaryKey(text)).distinct(),
        )
        // Say what actually happened. The ⋮ makes Delete a 2-tap action with no sheet in front of it, and
        // "delete" reads destructive — but this only ever hides a line from THIS summary. Naming the
        // scope is what keeps that honest (and cheaper than a confirmation nobody wants on an undoable,
        // record-preserving action).
        _message.value = "Removed from this summary — your record still has it"
        cached.copy(result = applyOverrides(cached.result, overrides, currentDevAreas()), overrides = overrides)
    }

    /** Edit a pointer's wording (feature #1). A blank new text clears the line (= delete). */
    fun editPointer(oldText: String, newText: String) = mutateCached { cached ->
        if (oldText.isBlank()) return@mutateCached null
        val trimmed = newText.trim()
        val key = summaryKey(oldText)
        val overrides = if (trimmed.isBlank()) {
            cached.overrides.copy(deleted = (cached.overrides.deleted + key).distinct())
        } else {
            val edits = cached.overrides.edits.filterNot { summaryKey(it.from) == key } + BulletEdit(oldText, trimmed)
            cached.overrides.copy(edits = edits)
        }
        cached.copy(result = applyOverrides(cached.result, overrides, currentDevAreas()), overrides = overrides)
    }

    /**
     * Restore a set-aside note into a chosen goal area (feature #5). Sticky across a Regenerate (the
     * note is re-injected and kept out of the Set-aside panel).
     */
    fun restoreSetAside(noteWhat: String, areaName: String) = mutateCached { cached ->
        if (noteWhat.isBlank() || areaName.isBlank()) return@mutateCached null
        val overrides = cached.overrides.copy(
            restored = (cached.overrides.restored + RestoredNote(noteWhat, areaName)).distinct(),
        )
        cached.copy(result = applyOverrides(cached.result, overrides, currentDevAreas()), overrides = overrides)
    }

    /**
     * Restore EVERY genuinely-dropped candidate at once (v0.31.0 · "restore all of it or parts of it").
     * Each item already knows its own goal area, so unlike the legacy categorical notes there is nothing
     * to ask the user — no destination picker, one tap.
     */
    fun restoreAllSetAside() {
        val items = state.value?.setAsideItems.orEmpty()
        if (items.isEmpty()) return
        mutateCached { cached ->
            val overrides = cached.overrides.copy(
                restored = (cached.overrides.restored + items.map { RestoredNote(it.bullet, it.area) }).distinct(),
            )
            cached.copy(result = applyOverrides(cached.result, overrides, currentDevAreas()), overrides = overrides)
        }
    }

    /**
     * **Retag a summary line to another category/project — and fix the RECORD, not just this page.**
     *
     * The owner's rationale: the AI mis-files often enough that every level of classification has to be
     * correctable. A summary-only fix would leave the record wrong, so the same mistake would return in
     * every other period and every other length. So:
     *
     *  1. Resolve the rendered line back to its rollup candidate and hence its source entry ids
     *     ([SummaryResolver]) — the model never sees an id, so this is re-derived from the text.
     *  2. Write the correction through the normal, AI-free [EntryProcessor.recategorize], which also
     *     ANCHORS it (so a later edit can't let the AI revert it) — Home and every future summary agree.
     *  3. Mirror the move into the cached summary and re-stamp its signature from the FRESH rollup, so
     *     the correction shows immediately and doesn't demand a metered Regenerate to be seen. This is
     *     honest, not a lie: the move is a change we applied deterministically to the output ourselves.
     *
     * When the line can't be resolved (an ambiguous or heavily-reworded bullet), it falls back to a
     * summary-only [PlacementOverride] — never a dead end. The caller is told which happened via
     * [message], because "fixed everywhere" and "fixed on this page" are materially different promises.
     *
     * [toProject] null = no specific project. A merged card (`count > 1`) re-files EVERY entry behind it.
     * [toDeliverable] null = not part of one (v0.33.0); [createDeliverable] means it was named in the
     * sheet and must be created before the win is filed into it.
     *
     * [deliverableTouched] false = **the user didn't answer the deliverable question**, so each entry
     * keeps its own. This sheet cannot preselect that axis — a summary line is AI prose that only
     * resolves to its source entries here, and a merged card resolves to several which may hold
     * different deliverables. Treating "unanswered" as "none" silently wiped the deliverable (and its
     * anchor) off any win the user had tap-in filed, whenever they opened this sheet to fix something
     * else — a HIGH found in the v0.33.0 assessment, and the v0.31.0 project-axis bug repeated.
     * Passing each entry's own value keeps the per-entry truth a merged card would otherwise flatten.
     */
    fun retagAchievement(
        areaName: String,
        bullet: String,
        toCategory: String,
        toProject: String?,
        toDeliverable: String? = null,
        createDeliverable: Boolean = false,
        deliverableTouched: Boolean = true,
        projectTouched: Boolean = true,
    ) {
        val s = state.value ?: return
        if (bullet.isBlank() || toCategory.isBlank()) return
        viewModelScope.launch {
            val inp = inputs.first()
            val before = compute(inp, s.period, s.length)
            val candidates = before.agg.goalAreas
                .firstOrNull { it.name.equals(areaName, ignoreCase = true) }
                ?.highlights.orEmpty()
            val match = SummaryResolver.resolve(bullet, candidates)

            var wroteBack = 0
            if (match != null) {
                for (id in match.entryIds) {
                    val e = entryRepository.getById(id) ?: continue
                    // Behaviour evidence is the AI's call and is preserved verbatim — this action is
                    // about PLACEMENT only, and recategorize() rewrites `demonstrates` wholesale.
                    //
                    // `createDeliverable` is passed on EVERY iteration, not just the first. A merged card
                    // re-files several entries, and an earlier attempt to "create once then reuse" was a
                    // real bug: `recategorize` **returns normally when it skips a row** (a still-filing
                    // RAW or offline PENDING_* entry is deliberately left alone), so the flag would be
                    // cleared by a call that never reached the insert — and every later entry would then
                    // fail the destination validation and silently lose the deliverable the user just
                    // named. The insert IGNOREs duplicates, so repeating it is free and cannot double-create.
                    runCatching {
                        entryRepository.recategorizeNow(
                            id = id,
                            goalArea = toCategory,
                            // Untouched → this entry's OWN project. Only a DEVELOPMENT line can get here
                            // untouched: its project isn't in the model's output, so the sheet has none
                            // to preselect and must not answer for the user. Treating that as "none"
                            // wrote OUTSIDE_PROJECT over a real project nobody chose to leave.
                            project = if (projectTouched) toProject?.takeIf { it.isNotBlank() } ?: OUTSIDE_PROJECT
                            else e.project?.takeIf { it.isNotBlank() } ?: OUTSIDE_PROJECT,
                            // Untouched → this entry's OWN deliverable, not a blanket null.
                            //
                            // Safe ONLY because the sheet forces `deliverableTouched` whenever the
                            // project changes. Relying on the destination validation to drop a stale tag
                            // here would be wrong: it resolves by NAME, so moving a win tagged "Phase 1"
                            // into a project that also has a "Phase 1" would silently adopt it — and
                            // anchor it — into a deliverable the user never picked.
                            deliverable = if (deliverableTouched) toDeliverable?.takeIf { it.isNotBlank() }
                            else e.deliverable,
                            demonstrates = e.demonstrates,
                            createDeliverable = createDeliverable && deliverableTouched,
                        )
                    }.onSuccess { wroteBack++ }
                }
            }

            if (wroteBack > 0) {
                // Re-stamp from the rollup as it stands AFTER the write (recategorizeNow has returned,
                // so syncRollup has committed) — using compute(), the same rule build() uses.
                val fresh = compute(inputs.first(), s.period, s.length)
                editMutex.withLock {
                    val current = summaryStore.cache.first()[s.gen.key] ?: return@withLock
                    val moved = movedLocally(
                        current.result, bullet, toCategory, toProject, toDeliverable, deliverableTouched,
                    )
                    summaryStore.put(
                        s.gen.key,
                        current.copy(
                            result = moved,
                            inputSignature = fresh.signature,
                            // A RESTORED line carries a sticky note pinning it to its old area. Retagging
                            // it without retargeting that note would re-inject it into the OLD area on the
                            // next local edit while the move holds it in the new one — the same line,
                            // permanently in two places.
                            overrides = current.overrides.retargetRestored(bullet, toCategory),
                        ),
                    )
                }
                _message.value = if (wroteBack == 1) {
                    "Re-filed in your record → $toCategory"
                } else {
                    "Re-filed $wroteBack entries in your record → $toCategory"
                }
            } else {
                // Fallback: couldn't tie this line to a record row, so correct the page and say so.
                mutateCached { cached ->
                    val overrides = cached.overrides
                        .retargetRestored(bullet, toCategory)
                        .let { ov ->
                            ov.copy(
                                moved = ov.moved.filterNot { it.key == summaryKey(bullet) } +
                                    PlacementOverride(summaryKey(bullet), toCategory, toProject?.takeIf { it.isNotBlank() }),
                            )
                        }
                    cached.copy(result = applyOverrides(cached.result, overrides, currentDevAreas()), overrides = overrides)
                }
                _message.value = "Moved in this summary only — couldn't match it to a saved entry"
            }
        }
    }

    /**
     * Apply the retag to the cached document immediately (the write-back path needs no override —
     * a Regenerate re-derives the right placement from the corrected record).
     *
     * The DELIVERABLE axis has to be mirrored here by hand (v0.34.0). [applyOverrides] knows only about
     * area + project: on a deliverable-only retag it hits its own idempotency guard (`here.project ==
     * mv.project`) and returns the card untouched, and on a category/project retag its `inject` rebuilds
     * the card from the bullet text alone and drops the deliverable. Either way the write-back then
     * re-stamps `inputSignature`, so the page reads **Up to date** while the sub-header and the exported
     * `[Project ▸ Deliverable]` tag still show the OLD deliverable — with no stale banner to prompt a
     * Regenerate. That is exactly the v0.31.0 F1 bug (the record corrected, the page silently not),
     * repeated on the axis this phase adds; the card carried no deliverable before, so it couldn't bite.
     */
    private fun movedLocally(
        result: com.bragbuddy.app.data.ai.SummaryResult,
        bullet: String,
        toCategory: String,
        toProject: String?,
        toDeliverable: String?,
        deliverableTouched: Boolean,
    ): com.bragbuddy.app.data.ai.SummaryResult {
        val target = summaryKey(bullet)
        // Untouched ("Leave as is") → the card keeps its own, mirroring what the record just did per
        // entry. Safe for the same reason the write-back loop above is: the sheet FORCES
        // deliverableTouched whenever the project changes, so untouched means the parents didn't move.
        val keep = result.summary.goalAreas
            .flatMap { it.achievements }
            .firstOrNull { summaryKey(it.bullet) == target }
            ?.deliverable
        val next = if (deliverableTouched) toDeliverable?.takeIf { it.isNotBlank() } else keep
        val moved = applyOverrides(
            result,
            SummaryOverrides(
                moved = listOf(PlacementOverride(target, toCategory, toProject?.takeIf { it.isNotBlank() })),
            ),
            currentDevAreas(),
        )
        return moved.copy(
            summary = moved.summary.copy(
                goalAreas = moved.summary.goalAreas.map { area ->
                    area.copy(
                        achievements = area.achievements.map {
                            if (summaryKey(it.bullet) == target) it.copy(deliverable = next) else it
                        },
                    )
                },
            ),
        )
    }

    /**
     * Shared local-edit helper. Serializes edits (so rapid taps can't clobber each other) and always
     * reads the FRESHEST persisted cached summary before transforming, then re-persists it under the
     * SAME inputSignature. Return null from [transform] to make it a no-op.
     */
    private fun mutateCached(transform: (CachedSummary) -> CachedSummary?) {
        val s = state.value ?: return
        viewModelScope.launch {
            editMutex.withLock {
                val current = summaryStore.cache.first()[s.gen.key] ?: return@withLock
                val next = transform(current) ?: return@withLock
                summaryStore.put(s.gen.key, next)
            }
        }
    }

    private val editMutex = Mutex()

    /** The framework's DEVELOPMENT-area names (lowercased) — so [applyOverrides] routes a restore or
     *  move across the goal/development boundary into [SummaryBody.development] rather than fabricating a
     *  duplicate goal-area header. Read live: framework edits are rare and dev-area names are stable. */
    private fun currentDevAreas(): Set<String> =
        state.value?.framework?.pillars
            ?.filter { it.kind == PillarKind.DEVELOPMENT }
            ?.map { it.name.lowercase() }?.toSet()
            ?: emptySet()

    private suspend fun maybeWarnSoftCap() {
        val count = runCatching { usageMeter.counts.first().summaryGenerationsThisMonth }.getOrDefault(0)
        if (count >= SOFT_CAP) {
            _message.value = "You've generated $count summaries this month — going strong."
        }
    }

    private companion object {
        // Generous abuse guard only; normal use never approaches it (non-blocking).
        const val SOFT_CAP = 50
    }
}
