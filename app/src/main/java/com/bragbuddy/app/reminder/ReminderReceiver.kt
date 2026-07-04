package com.bragbuddy.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.notification.Notifications
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires the daily reminder and keeps the exact alarm alive.
 *
 *  - [ACTION_FIRE] (the alarm went off): post the notification, then re-arm tomorrow's alarm so the
 *    9 PM anchor never drifts. Skips both if the reminder was turned off (a stale alarm self-heals).
 *  - BOOT_COMPLETED / MY_PACKAGE_REPLACED / TIME_SET / TIMEZONE_CHANGED: alarms are cleared on
 *    reboot and can be knocked off by a clock/timezone change — reschedule from the saved settings.
 *
 * Injected via Hilt so it can read [SettingsStore] and reuse [ReminderScheduler] directly.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        // REQUIRED for a Hilt @AndroidEntryPoint receiver: the generated base performs field injection
        // here. Without this call settingsStore/scheduler stay uninitialized and every fire/boot crashes.
        super.onReceive(context, intent)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val s = settingsStore.settings.first()
                if (!s.reminderEnabled) return@launch
                if (intent.action == ACTION_FIRE) {
                    Notifications.postReminder(context)
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
