package com.bragbuddy.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.notification.Notifications
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Fires the daily reminder + the weekly recap and keeps both exact alarms alive.
 *
 *  - [ACTION_FIRE] (the daily alarm): post the reminder, then re-arm tomorrow's alarm so the anchor
 *    never drifts. Skips if the reminder was turned off (a stale alarm self-heals).
 *  - [ACTION_FIRE_WEEKLY] (the Sunday alarm): if the recap is enabled, count the week's filed wins
 *    (local data only — no AI) and post the recap only when there were any, then re-arm next Sunday.
 *  - BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED: alarms are cleared on
 *    reboot and can be knocked off by a clock/timezone change — reschedule BOTH from the saved settings.
 *
 * A plain [BroadcastReceiver] pulling singleton deps through a Hilt [EntryPoint] (the proven sibling-app
 * pattern) — no `@AndroidEntryPoint`/`super.onReceive()`, which the Hilt bytecode transform makes
 * fragile to compile for receivers.
 */
class ReminderReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReminderEntryPoint {
        fun settingsStore(): SettingsStore
        fun reminderScheduler(): ReminderScheduler
        fun entryDao(): EntryDao
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(app, ReminderEntryPoint::class.java)
        val settingsStore = entry.settingsStore()
        val scheduler = entry.reminderScheduler()
        val entryDao = entry.entryDao()

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val s = settingsStore.settings.first()
                when (intent.action) {
                    ACTION_FIRE -> {
                        if (!s.reminderEnabled || s.reminderDays.isEmpty()) return@launch
                        // Only nudge on an enabled weekday. The alarm is armed to land on enabled days
                        // only, but gate anyway so a stale alarm from a since-narrowed schedule can't
                        // fire on a disabled day. Always re-arm to the next enabled day.
                        if (ZonedDateTime.now().dayOfWeek in s.reminderDays) {
                            Notifications.postReminder(app)
                        }
                        scheduler.schedule(s.reminderHour, s.reminderMinute, s.reminderDays)
                    }
                    ACTION_FIRE_WEEKLY -> {
                        if (!s.weeklyRecapEnabled) return@launch
                        val now = System.currentTimeMillis()
                        val weekStart = now - SEVEN_DAYS_MS
                        val wins = entryDao.countByStatusBetween(EntryStatus.PROCESSED, weekStart, now)
                        if (wins > 0) {
                            val withNumbers = entryDao.countWithMetricBetween(EntryStatus.PROCESSED, weekStart, now)
                            Notifications.postWeeklyRecap(app, wins, withNumbers)
                        }
                        scheduler.scheduleWeekly() // re-arm next Sunday even on a silent (0-win) week
                    }
                    else -> {
                        // Boot / time change: re-arm whichever alarms are enabled (independent).
                        // schedule() no-ops (cancels) internally when reminderDays is empty.
                        if (s.reminderEnabled) scheduler.schedule(s.reminderHour, s.reminderMinute, s.reminderDays)
                        if (s.weeklyRecapEnabled) scheduler.scheduleWeekly()
                    }
                }
            } catch (_: Throwable) {
                // A reminder must never crash the app — swallow and let the next launch re-sync.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.bragbuddy.app.action.REMINDER_FIRE"
        const val ACTION_FIRE_WEEKLY = "com.bragbuddy.app.action.RECAP_FIRE"
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
