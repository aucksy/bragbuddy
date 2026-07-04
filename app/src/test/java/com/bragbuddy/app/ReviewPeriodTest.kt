package com.bragbuddy.app

import com.bragbuddy.app.data.rollup.ReviewPeriods
import com.bragbuddy.app.data.rollup.SummaryPeriod
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Unit tests for review-year windowing (configurable start month). */
class ReviewPeriodTest {

    private val utc = ZoneId.of("UTC")
    private fun millis(y: Int, m: Int, d: Int) =
        LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun `calendar-year start resolves to Jan 1 of the current year`() {
        val start = ReviewPeriods.currentReviewYearStart(1, LocalDate.of(2026, 7, 4))
        assertThat(start).isEqualTo(LocalDate.of(2026, 1, 1))
    }

    @Test
    fun `an April fiscal start rolls back to last year before April`() {
        assertThat(ReviewPeriods.currentReviewYearStart(4, LocalDate.of(2026, 3, 15)))
            .isEqualTo(LocalDate.of(2025, 4, 1))
        assertThat(ReviewPeriods.currentReviewYearStart(4, LocalDate.of(2026, 5, 15)))
            .isEqualTo(LocalDate.of(2026, 4, 1))
    }

    @Test
    fun `year-end window spans the full review year`() {
        val w = ReviewPeriods.windowFor(SummaryPeriod.YEAR_END, startMonth = 1, today = LocalDate.of(2026, 7, 4), zone = utc)
        assertThat(w.startMillis).isEqualTo(millis(2026, 1, 1))
        assertThat(w.endMillisExclusive).isEqualTo(millis(2027, 1, 1))
        assertThat(w.rangeText).contains("2026")
    }

    @Test
    fun `mid-year window is the first six months of the review year`() {
        val w = ReviewPeriods.windowFor(SummaryPeriod.MID_YEAR, startMonth = 4, today = LocalDate.of(2026, 5, 1), zone = utc)
        assertThat(w.startMillis).isEqualTo(millis(2026, 4, 1))
        assertThat(w.endMillisExclusive).isEqualTo(millis(2026, 10, 1))
    }
}
