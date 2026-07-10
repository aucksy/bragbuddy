package com.bragbuddy.app.data.theme

import com.bragbuddy.app.data.prefs.ThemeMode

/**
 * Pure, Android-free theme-resolution logic (Phase 2 · theme) — so the light/dark decision and the
 * [ThemeMode.AUTO] schedule are unit-tested and the composables stay thin. All times are **minutes of
 * day** (0..1439); the caller converts hour:minute via [minuteOfDay] and passes the device's current
 * minute + the system dark flag.
 */
object ThemeSchedule {

    /** Minutes of day (0..1439) for an hour:minute, clamped defensively. */
    fun minuteOfDay(hour: Int, minute: Int): Int = hour.coerceIn(0, 23) * 60 + minute.coerceIn(0, 59)

    /**
     * Is [nowMin] within the dark window [darkStartMin, lightStartMin) — wrapping midnight when dark
     * starts later in the day than light (the usual "dark 20:00 → light 07:00")? Equal starts mean no
     * window (always light) — a degenerate config the picker doesn't produce, floored here.
     */
    fun inDarkWindow(nowMin: Int, darkStartMin: Int, lightStartMin: Int): Boolean {
        if (darkStartMin == lightStartMin) return false
        return if (darkStartMin < lightStartMin) nowMin in darkStartMin until lightStartMin
        else nowMin >= darkStartMin || nowMin < lightStartMin
    }

    /** The effective dark flag for [mode]. [systemDark] comes from `isSystemInDarkTheme()`; the schedule
     *  args matter only for [ThemeMode.AUTO]. */
    fun resolveDark(
        mode: ThemeMode,
        systemDark: Boolean,
        nowMin: Int,
        darkStartMin: Int,
        lightStartMin: Int,
    ): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.AUTO -> inDarkWindow(nowMin, darkStartMin, lightStartMin)
    }

    /**
     * Minutes until the next [ThemeMode.AUTO] switch boundary strictly after [nowMin], in 1..1440 — used
     * to schedule the live recomposition that flips the theme while the app is open. A boundary exactly
     * at "now" returns a full day (1440) so the OTHER boundary is picked as the real next; the result is
     * never 0 (which would busy-loop). When both boundaries coincide, a full day is returned.
     */
    fun minutesUntilNextSwitch(nowMin: Int, darkStartMin: Int, lightStartMin: Int): Int {
        fun dist(boundary: Int): Int {
            val d = ((boundary - nowMin) % 1440 + 1440) % 1440
            return if (d == 0) 1440 else d
        }
        return minOf(dist(darkStartMin), dist(lightStartMin))
    }
}
