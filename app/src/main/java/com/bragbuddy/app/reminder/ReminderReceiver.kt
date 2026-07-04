package com.bragbuddy.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

/**
 * Fires the daily reminder and keeps the exact alarm alive.
 *
 *  - [ACTION_FIRE] (the alarm went off): post the notification, then re-arm tomorrow's alarm so the
 *    9 PM anchor never drifts. Skips both if the reminder was turned off (a stale alarm self-heals).
 *  - BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED: alarms are cleared on
 *    reboot and can be knocked off by a clock/timezone change — reschedule from the saved settings.
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
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(app, ReminderEntryPoint::class.java)
        val settingsStore = entry.settingsStore()
        val scheduler = entry.reminderScheduler()

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val s = settingsStore.settings.first()
                if (!s.reminderEnabled) return@launch
                if (intent.action == ACTION_FIRE) {
                    Notifications.postReminder(app)
                }
                // Re-arm the next occurrence in every case (fire → tomorrow; boot/time change → next).
                scheduler.schedule(s.reminderHour, s.reminderMinute)
            } catch (_: Throwable) {
                // A reminder must never crash the app — swallow and let the next launch re-sync.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.bragbuddy.app.action.REMINDER_FIRE"
    }
}
