package com.bragbuddy.app.data.retention

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.IsoFields

/**
 * Phase 7 · pure decision logic for the three retention touches (PRD P0-1 + Design System §7):
 * the on-open "you haven't logged today" nudge, the gentle weekly catch-up, and the early preview
 * summary banner. Pure functions over plain inputs — no clock, no I/O — so every edge is
 * unit-tested ([RetentionPolicyTest]); callers pass `System.currentTimeMillis()` + the zone.
 */
object RetentionPolicy {

    // NOTE: LocalDate.ofInstant / LocalDateTime.ofInstant(java9+) are avoided — minSdk 26 ships
    // Java-8 java.time, so everything goes through Instant.atZone (available since API 26).

    /** Stable key for "today" (e.g. `2026-07-04`) — used to dismiss the daily nudge for one day. */
    fun dayKey(nowMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toString()

    /** Stable ISO-week key (e.g. `2026-W27`) — the weekly catch-up shows at most once per key.
     *  The Fri-evening→Sunday window never crosses an ISO week boundary (weeks run Mon–Sun). */
    fun weekKey(nowMillis: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val year = date.get(IsoFields.WEEK_BASED_YEAR)
        val week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    /**
     * The on-open daily nudge (PRD P0-1: "a missed notification never means a missed day").
     * Shows only when it's purely the reminder's safety net: the reminder is on, its time has
     * already passed today, nothing was captured today, the user hasn't dismissed it today —
     * and they've logged before (a brand-new user gets the empty-state invitation instead).
     */
    fun dailyNudgeVisible(
        nowMillis: Long,
        zone: ZoneId,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        lastEntryAtMillis: Long?,
        dismissedDayKey: String,
    ): Boolean {
        if (!reminderEnabled) return false
        if (lastEntryAtMillis == null) return false // never logged → the empty state owns onboarding
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
        val reminderToday = now.toLocalDate()
            .atTime(reminderHour.coerceIn(0, 23), reminderMinute.coerceIn(0, 59))
        if (now.isBefore(reminderToday)) return false
        val today = dayKey(nowMillis, zone)
        if (dismissedDayKey == today) return false
        val lastEntryDay = dayKey(lastEntryAtMillis, zone)
        // ISO day keys compare lexicographically = chronologically: today-or-later counts as
        // "already logged", so one future-dated entry (a corrected clock) can't nag every evening.
        return lastEntryDay < today
    }

    /** True inside the weekly catch-up window: Friday from [CATCHUP_START_HOUR]:00 through Sunday. */
    fun inCatchupWindow(nowMillis: Long, zone: ZoneId): Boolean {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDateTime()
        return when (now.dayOfWeek) {
            DayOfWeek.FRIDAY -> now.hour >= CATCHUP_START_HOUR
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> true
            else -> false
        }
    }

    /**
     * The weekly catch-up sheet (Design §7 · "Anything bigger this week you didn't log?").
     * Due on the first app-open inside the Fri-evening→Sunday window, at most once per ISO week
     * ("Not this week" writes the week key). Skipped entirely for a user who has never logged.
     */
    fun catchupDue(
        nowMillis: Long,
        zone: ZoneId,
        enabled: Boolean,
        lastShownWeekKey: String,
        hasAnyEntry: Boolean,
    ): Boolean {
        if (!enabled || !hasAnyEntry) return false
        if (!inCatchupWindow(nowMillis, zone)) return false
        return lastShownWeekKey != weekKey(nowMillis, zone)
    }

    /**
     * The early preview banner (Design §7 · "Your summary's taking shape … From just N entries").
     * Shows once a handful of entries are filed and disappears forever after the first summary is
     * ever generated (the payoff has been felt) or an explicit dismiss.
     */
    fun previewBannerVisible(
        processedCount: Int,
        hasAnySummary: Boolean,
        dismissed: Boolean,
    ): Boolean = !dismissed && !hasAnySummary && processedCount >= PREVIEW_MIN_ENTRIES

    /** Entries filed before the preview banner appears (the design's "From just 5 entries"). */
    const val PREVIEW_MIN_ENTRIES = 5

    /** The catch-up window opens Friday at 17:00 local. */
    const val CATCHUP_START_HOUR = 17
}
