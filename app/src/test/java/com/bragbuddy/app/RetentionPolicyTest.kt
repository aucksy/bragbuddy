package com.bragbuddy.app

import com.bragbuddy.app.data.retention.RetentionPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Unit tests for the Phase 7 retention decisions (daily nudge / weekly catch-up / preview banner). */
class RetentionPolicyTest {

    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")

    /** 2026-07-04 is a Saturday; 2026-07-03 a Friday; 2026-07-06 a Monday. */
    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    // ---------------- day / week keys ----------------

    @Test
    fun `day and week keys are stable and zone-aware`() {
        val saturdayNoon = at(2026, 7, 4, 12)
        assertThat(RetentionPolicy.dayKey(saturdayNoon, zone)).isEqualTo("2026-07-04")
        assertThat(RetentionPolicy.weekKey(saturdayNoon, zone)).isEqualTo("2026-W27")
        // Monday starts the next ISO week.
        assertThat(RetentionPolicy.weekKey(at(2026, 7, 6, 0, 1), zone)).isEqualTo("2026-W28")
    }

    @Test
    fun `week key pads single-digit weeks`() {
        // 2026-01-07 is in ISO week 2 of 2026.
        assertThat(RetentionPolicy.weekKey(at(2026, 1, 7, 12), zone)).isEqualTo("2026-W02")
    }

    // ---------------- daily nudge ----------------

    private fun nudge(
        now: Long,
        lastEntryAt: Long? = at(2026, 7, 3, 10), // logged yesterday by default
        enabled: Boolean = true,
        hour: Int = 18,
        minute: Int = 0,
        dismissedDay: String = "",
    ) = RetentionPolicy.dailyNudgeVisible(now, zone, enabled, hour, minute, lastEntryAt, dismissedDay)

    @Test
    fun `shows after the reminder time when nothing logged today`() {
        assertThat(nudge(now = at(2026, 7, 4, 19))).isTrue()
    }

    @Test
    fun `hidden before the reminder time`() {
        assertThat(nudge(now = at(2026, 7, 4, 17, 59))).isFalse()
    }

    @Test
    fun `shows exactly at the reminder minute`() {
        assertThat(nudge(now = at(2026, 7, 4, 18, 0))).isTrue()
    }

    @Test
    fun `hidden when something was already logged today`() {
        assertThat(nudge(now = at(2026, 7, 4, 19), lastEntryAt = at(2026, 7, 4, 9))).isFalse()
    }

    @Test
    fun `hidden when the reminder is disabled`() {
        assertThat(nudge(now = at(2026, 7, 4, 19), enabled = false)).isFalse()
    }

    @Test
    fun `hidden for a brand-new user who never logged`() {
        assertThat(nudge(now = at(2026, 7, 4, 19), lastEntryAt = null)).isFalse()
    }

    @Test
    fun `a future-dated entry counts as logged (corrected clock never nags)`() {
        assertThat(nudge(now = at(2026, 7, 4, 19), lastEntryAt = at(2026, 7, 6, 9))).isFalse()
    }

    @Test
    fun `hidden after dismissing for today, back the next day`() {
        assertThat(nudge(now = at(2026, 7, 4, 19), dismissedDay = "2026-07-04")).isFalse()
        assertThat(nudge(now = at(2026, 7, 5, 19), dismissedDay = "2026-07-04")).isTrue()
    }

    // ---------------- weekly catch-up ----------------

    private fun catchup(
        now: Long,
        enabled: Boolean = true,
        lastShownWeek: String = "",
        hasAnyEntry: Boolean = true,
    ) = RetentionPolicy.catchupDue(now, zone, enabled, lastShownWeek, hasAnyEntry)

    @Test
    fun `window opens Friday 17-00 and spans the weekend`() {
        assertThat(catchup(at(2026, 7, 3, 16, 59))).isFalse() // Friday just before
        assertThat(catchup(at(2026, 7, 3, 17, 0))).isTrue() // Friday 5pm
        assertThat(catchup(at(2026, 7, 4, 9))).isTrue() // Saturday morning
        assertThat(catchup(at(2026, 7, 5, 23, 59))).isTrue() // Sunday night
        assertThat(catchup(at(2026, 7, 6, 0, 1))).isFalse() // Monday
        assertThat(catchup(at(2026, 7, 2, 19))).isFalse() // Thursday evening
    }

    @Test
    fun `shows at most once per ISO week`() {
        val saturday = at(2026, 7, 4, 10)
        assertThat(catchup(saturday, lastShownWeek = "2026-W27")).isFalse() // already shown this week
        assertThat(catchup(saturday, lastShownWeek = "2026-W26")).isTrue() // last week's stamp
    }

    @Test
    fun `respects the opt-out and skips a user who never logged`() {
        val saturday = at(2026, 7, 4, 10)
        assertThat(catchup(saturday, enabled = false)).isFalse()
        assertThat(catchup(saturday, hasAnyEntry = false)).isFalse()
    }

    // ---------------- early preview banner ----------------

    @Test
    fun `banner needs five filed entries`() {
        assertThat(RetentionPolicy.previewBannerVisible(4, hasAnySummary = false, dismissed = false)).isFalse()
        assertThat(RetentionPolicy.previewBannerVisible(5, hasAnySummary = false, dismissed = false)).isTrue()
    }

    @Test
    fun `banner hides forever once a summary exists or after dismiss`() {
        assertThat(RetentionPolicy.previewBannerVisible(12, hasAnySummary = true, dismissed = false)).isFalse()
        assertThat(RetentionPolicy.previewBannerVisible(12, hasAnySummary = false, dismissed = true)).isFalse()
    }
}
