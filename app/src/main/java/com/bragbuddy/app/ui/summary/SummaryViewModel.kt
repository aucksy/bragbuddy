package com.bragbuddy.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiPrompts
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.SummaryRequest
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.net.ConnectivityMonitor
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.rollup.PeriodWindow
import com.bragbuddy.app.data.rollup.ReviewPeriods
import com.bragbuddy.app.data.rollup.RollupAggregator
import com.bragbuddy.app.data.rollup.RollupStore
import com.bragbuddy.app.data.rollup.SummaryLength
import com.bragbuddy.app.data.rollup.SummaryPeriod
import com.bragbuddy.app.data.summary.BulletEdit
import com.bragbuddy.app.data.summary.CachedSummary
import com.bragbuddy.app.data.summary.RestoredNote
import com.bragbuddy.app.data.summary.SummaryOverrides
import com.bragbuddy.app.data.summary.SummaryStore
import com.bragbuddy.app.data.summary.applyOverrides
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
    ) { rollup, fw, settings, pinned -> Inputs(rollup.items, fw, settings.groqApiKey, settings.jobRole, settings.reviewYearStartMonth, pinned) }

    private data class Inputs(
        val items: List<com.bragbuddy.app.data.rollup.RollupItem>,
        val framework: Framework,
        val groqKey: String,
        val role: String,
        val reviewStartMonth: Int,
        val pinned: List<EntryEntity>,
    )

    val state: StateFlow<ScreenState?> = combine(
        inputs,
        combine(period, length) { p, l -> p to l },
        summaryStore.cache,
    ) { inp, sel, cache -> build(inp, sel.first, sel.second, cache) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun build(
        inp: Inputs,
        period: SummaryPeriod,
        length: SummaryLength,
        cache: Map<String, CachedSummary>,
    ): ScreenState {
        val window = ReviewPeriods.windowFor(period, inp.reviewStartMonth)
        // Length drives how many candidate highlights per area the model sees (Detailed = more).
        val agg = RollupAggregator.aggregate(
            inp.items, window.startMillis, window.endMillisExclusive, inp.framework, length.highlightCap,
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
        val key = "${period.name}::${length.name}"
        val cached = cache[key]
        val hasContent = !agg.isEmpty || pinnedInWindow.isNotEmpty()

        val gen = GenInputs(key, signature, frameworkBlock, inp.role, rollupString, pinnedForPrompt)

        val phase = when {
            inp.groqKey.isBlank() && cached == null -> Phase.NEEDS_KEY
            cached != null -> Phase.READY
            !hasContent -> Phase.EMPTY
            else -> Phase.NOT_GENERATED
        }
        return ScreenState(
            phase = phase,
            period = period,
            length = length,
            window = window,
            entryCount = agg.entryCount,
            framework = inp.framework,
            pinnedBullets = pinnedBullets,
            cached = cached,
            isStale = cached != null && cached.inputSignature != signature,
            hasContent = hasContent,
            gen = gen,
        )
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
                                    result = applyOverrides(result, overrides),
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
        cached.copy(result = applyOverrides(cached.result, overrides), overrides = overrides)
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
        cached.copy(result = applyOverrides(cached.result, overrides), overrides = overrides)
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
        cached.copy(result = applyOverrides(cached.result, overrides), overrides = overrides)
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
