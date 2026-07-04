package com.bragbuddy.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.ai.SummaryRequest
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.rollup.PeriodWindow
import com.bragbuddy.app.data.rollup.ReviewPeriods
import com.bragbuddy.app.data.rollup.RollupAggregator
import com.bragbuddy.app.data.rollup.RollupStore
import com.bragbuddy.app.data.rollup.SummaryLength
import com.bragbuddy.app.data.rollup.SummaryPeriod
import com.bragbuddy.app.data.summary.CachedSummary
import com.bragbuddy.app.data.summary.SummaryStore
import com.bragbuddy.app.data.usage.UsageMeter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        val signature = RollupAggregator.signature(
            rollupString, pinnedForPrompt.joinToString("\n"), frameworkBlock, inp.role, period.name, length.name,
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
                        summaryStore.put(
                            s.gen.key,
                            CachedSummary(
                                period = s.period.name,
                                length = s.length.name,
                                inputSignature = s.gen.signature,
                                result = result,
                                periodRangeText = s.window.rangeText,
                                generatedAtMillis = System.currentTimeMillis(),
                            ),
                        )
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

    /** Reorder an achievement up within its goal area (local edit of the cached draft; no model call). */
    fun promote(areaName: String, index: Int) = reorder(areaName, index, -1)

    /** Reorder an achievement down within its goal area. */
    fun demote(areaName: String, index: Int) = reorder(areaName, index, +1)

    private fun reorder(areaName: String, index: Int, delta: Int) {
        val s = state.value ?: return
        val cached = s.cached ?: return
        viewModelScope.launch {
            val areas = cached.result.summary.goalAreas.toMutableList()
            val ai = areas.indexOfFirst { it.name == areaName }
            if (ai < 0) return@launch
            val achievements = areas[ai].achievements.toMutableList()
            val target = index + delta
            if (index !in achievements.indices || target !in achievements.indices) return@launch
            val moved = achievements.removeAt(index)
            achievements.add(target, moved)
            areas[ai] = areas[ai].copy(achievements = achievements)
            val newResult = cached.result.copy(summary = cached.result.summary.copy(goalAreas = areas))
            // Keep the SAME inputSignature — this changes the output, not the input, so it stays fresh.
            summaryStore.put(s.gen.key, cached.copy(result = newResult))
        }
    }

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
