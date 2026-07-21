package com.bragbuddy.app.data.rollup

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Period windowing for the summary. The creator chose a **configurable review-year start** (fiscal
 * cycles differ), so a period is a date window computed from that start month — Mid-year is the first
 * half of the current review year, Year-end is the whole review year. The window bounds which
 * [RollupItem]s a summary aggregates, so a mid-year write-up doesn't pull in later work.
 *
 * [LAST_YEAR] exists because the year-end form is usually filled **after** the cycle closes (an
 * April–March review year is written up in April) — at that moment the "current" review year is the
 * brand-new, nearly-empty one, and only the previous year's window holds the work being reviewed
 * (VISION-FIT-ASSESSMENT §6.2).
 */
enum class SummaryPeriod(val label: String, val promptLabel: String) {
    MID_YEAR("Mid-year", "mid-year check-in"),
    YEAR_END("Year-end", "full-year review"),
    LAST_YEAR("Last year", "full-year review"),
}

/**
 * Length knob (Design System §5A). [cap] maps to the `{{LENGTH_CAP}}` the summary generator honours;
 * [highlightCap] bounds how many candidate highlights per goal area are fed to the model, so the knob
 * changes the actual input volume — not just the prompt wording. Detailed feeds a generous ceiling so
 * "every strong achievement" is honoured while the prompt stays bounded.
 */
enum class SummaryLength(val label: String, val sub: String, val cap: String, val highlightCap: Int) {
    BRIEF("Brief", "Top 3 per pillar", "brief — at most 3 achievements per goal area; keep it tight", 8),
    ONE_PAGE("One page", "Recommended · fits most forms", "about one page — at most 5 achievements per goal area", 15),
    DETAILED("Detailed", "Every strong achievement", "detailed — include every strong achievement per goal area", 60),
}

/** A resolved date window: `[startMillis, endMillisExclusive)` plus display text + entry count host. */
data class PeriodWindow(
    val startMillis: Long,
    val endMillisExclusive: Long,
    val rangeText: String,
)

object ReviewPeriods {

    private val RANGE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

    /**
     * The most recent occurrence of (startMonth, day 1) at or before [today] — the start of the
     * review year the user is currently in.
     */
    fun currentReviewYearStart(startMonth: Int, today: LocalDate): LocalDate {
        val m = startMonth.coerceIn(1, 12)
        val candidate = LocalDate.of(today.year, m, 1)
        return if (candidate.isAfter(today)) candidate.minusYears(1) else candidate
    }

    /** Resolve a [period] into a concrete window given the configured [startMonth] and [today]. */
    fun windowFor(
        period: SummaryPeriod,
        startMonth: Int,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): PeriodWindow {
        val current = currentReviewYearStart(startMonth, today)
        // LAST_YEAR anchors one review year back — the whole point of the period (see the enum doc).
        val start = if (period == SummaryPeriod.LAST_YEAR) current.minusYears(1) else current
        val endExclusive = when (period) {
            SummaryPeriod.MID_YEAR -> start.plusMonths(6)
            SummaryPeriod.YEAR_END, SummaryPeriod.LAST_YEAR -> start.plusYears(1)
        }
        val startMillis = start.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeText = "${start.format(RANGE_FMT)} – ${endExclusive.minusDays(1).format(RANGE_FMT)}"
        return PeriodWindow(startMillis, endMillis, rangeText)
    }
}
