package com.bragbuddy.app

import com.bragbuddy.app.data.prefs.ThemeMode
import com.bragbuddy.app.data.theme.ThemeSchedule
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the Phase 2 theme resolution + AUTO schedule (pure, Android-free). */
class ThemeScheduleTest {

    private val darkDefault = ThemeSchedule.minuteOfDay(20, 0)  // 20:00 = 1200
    private val lightDefault = ThemeSchedule.minuteOfDay(7, 0)  // 07:00 = 420

    // ---------------- minuteOfDay ----------------

    @Test
    fun `minuteOfDay maps and clamps`() {
        assertThat(ThemeSchedule.minuteOfDay(0, 0)).isEqualTo(0)
        assertThat(ThemeSchedule.minuteOfDay(20, 0)).isEqualTo(1200)
        assertThat(ThemeSchedule.minuteOfDay(23, 59)).isEqualTo(1439)
        assertThat(ThemeSchedule.minuteOfDay(30, 90)).isEqualTo(1439) // clamped to 23:59
        assertThat(ThemeSchedule.minuteOfDay(-1, -5)).isEqualTo(0)
    }

    // ---------------- inDarkWindow · wrapping midnight (the default 20:00 → 07:00) ----------------

    @Test
    fun `dark window wrapping midnight covers evening and early morning`() {
        fun dark(h: Int, m: Int = 0) =
            ThemeSchedule.inDarkWindow(ThemeSchedule.minuteOfDay(h, m), darkDefault, lightDefault)
        assertThat(dark(21)).isTrue()      // 9 PM — night
        assertThat(dark(23, 59)).isTrue()  // just before midnight
        assertThat(dark(0)).isTrue()       // midnight
        assertThat(dark(6, 59)).isTrue()   // just before the light switch
        assertThat(dark(20)).isTrue()      // exactly at dark start (inclusive)
        assertThat(dark(7)).isFalse()      // exactly at light start (exclusive → light)
        assertThat(dark(8)).isFalse()      // morning
        assertThat(dark(12)).isFalse()     // noon
        assertThat(dark(19, 59)).isFalse() // just before dark
    }

    // ---------------- inDarkWindow · same-day window (dark 09:00 → light 17:00) ----------------

    @Test
    fun `dark window within one day`() {
        val d = ThemeSchedule.minuteOfDay(9, 0)
        val l = ThemeSchedule.minuteOfDay(17, 0)
        fun dark(h: Int) = ThemeSchedule.inDarkWindow(ThemeSchedule.minuteOfDay(h, 0), d, l)
        assertThat(dark(12)).isTrue()
        assertThat(dark(9)).isTrue()
        assertThat(dark(17)).isFalse()
        assertThat(dark(8)).isFalse()
        assertThat(dark(20)).isFalse()
    }

    @Test
    fun `equal start and end means never dark`() {
        val t = ThemeSchedule.minuteOfDay(9, 0)
        assertThat(ThemeSchedule.inDarkWindow(t, t, t)).isFalse()
        assertThat(ThemeSchedule.inDarkWindow(ThemeSchedule.minuteOfDay(23, 0), t, t)).isFalse()
    }

    // ---------------- resolveDark per mode ----------------

    @Test
    fun `forced modes ignore system and schedule`() {
        // LIGHT is always light, DARK always dark — even at a "dark o'clock" now and opposite systemDark.
        val night = ThemeSchedule.minuteOfDay(23, 0)
        assertThat(ThemeSchedule.resolveDark(ThemeMode.LIGHT, systemDark = true, night, darkDefault, lightDefault)).isFalse()
        assertThat(ThemeSchedule.resolveDark(ThemeMode.DARK, systemDark = false, ThemeSchedule.minuteOfDay(12, 0), darkDefault, lightDefault)).isTrue()
    }

    @Test
    fun `system mode passes through the device flag`() {
        val noon = ThemeSchedule.minuteOfDay(12, 0)
        assertThat(ThemeSchedule.resolveDark(ThemeMode.SYSTEM, systemDark = true, noon, darkDefault, lightDefault)).isTrue()
        assertThat(ThemeSchedule.resolveDark(ThemeMode.SYSTEM, systemDark = false, noon, darkDefault, lightDefault)).isFalse()
    }

    @Test
    fun `auto mode follows the schedule and ignores systemDark`() {
        val night = ThemeSchedule.minuteOfDay(22, 0)
        val day = ThemeSchedule.minuteOfDay(10, 0)
        assertThat(ThemeSchedule.resolveDark(ThemeMode.AUTO, systemDark = false, night, darkDefault, lightDefault)).isTrue()
        assertThat(ThemeSchedule.resolveDark(ThemeMode.AUTO, systemDark = true, day, darkDefault, lightDefault)).isFalse()
    }

    // ---------------- minutesUntilNextSwitch ----------------

    @Test
    fun `next switch picks the nearer upcoming boundary`() {
        // 21:00 → next boundary is light at 07:00, 10h away.
        assertThat(ThemeSchedule.minutesUntilNextSwitch(ThemeSchedule.minuteOfDay(21, 0), darkDefault, lightDefault))
            .isEqualTo(10 * 60)
        // 06:00 → light at 07:00 is 1h away.
        assertThat(ThemeSchedule.minutesUntilNextSwitch(ThemeSchedule.minuteOfDay(6, 0), darkDefault, lightDefault))
            .isEqualTo(60)
        // 12:00 → dark at 20:00 is 8h away (nearer than next-day 07:00).
        assertThat(ThemeSchedule.minutesUntilNextSwitch(ThemeSchedule.minuteOfDay(12, 0), darkDefault, lightDefault))
            .isEqualTo(8 * 60)
    }

    @Test
    fun `a boundary exactly at now is skipped to the other boundary (never zero)`() {
        // Exactly 20:00 (a boundary): the next switch is light at 07:00, 11h away — not 0.
        assertThat(ThemeSchedule.minutesUntilNextSwitch(darkDefault, darkDefault, lightDefault))
            .isEqualTo(11 * 60)
        // Result is always strictly positive.
        assertThat(ThemeSchedule.minutesUntilNextSwitch(lightDefault, darkDefault, lightDefault)).isGreaterThan(0)
    }
}
