package com.bragbuddy.app.data.usage

import kotlinx.coroutines.flow.Flow

/**
 * The single usage-metering hook the PRD asks us to build "from the start" (PRD P0-12 / Build
 * Brief § model routing): count the two things that cost money per user — **summary generations**
 * and **cloud-transcription seconds**. No billing, no tiers, no UI here — just the counts, so the
 * future credits/subscription plan (PRD §11) is a config change, not a rewrite.
 *
 * Nothing increments these yet: summary generation lands in Phase 5, cloud transcription is an
 * opt-in swap in Phase 1/2. The seam exists now so those phases call it instead of bolting it on.
 * Counts are device-local (this is a single-user local-first app); they travel in backup later.
 */
interface UsageMeter {
    /** Observable current counts (current calendar month + lifetime totals). */
    val counts: Flow<UsageCounts>

    /** Call once per *fresh* summary generation (cached views must NOT be counted). */
    suspend fun recordSummaryGeneration()

    /** Call with the billed duration each time cloud transcription is used. */
    suspend fun recordTranscriptionSeconds(seconds: Long)
}

/**
 * Month-scoped + lifetime counters. The month scope exists because the future plan meters fresh
 * summaries per month (PRD §11 tiers); totals are for lifetime/debug visibility.
 */
data class UsageCounts(
    /** Current period as ISO year-month, e.g. "2026-07". */
    val yearMonth: String = "",
    val summaryGenerationsThisMonth: Int = 0,
    val summaryGenerationsTotal: Int = 0,
    val transcriptionSecondsTotal: Long = 0,
)
