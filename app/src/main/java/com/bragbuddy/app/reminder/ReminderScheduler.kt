package com.bragbuddy.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.DayOfWeek
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder as an **exact** one-shot [AlarmManager] alarm re-armed each day.
 *
 * Why not WorkManager periodic work (the previous approach): periodic work is inexact — the OS
 * batches it into Doze maintenance windows and drops the time-of-day anchor after the first run, so
 * a reminder set for a fixed time drifted to seemingly random times. An exact alarm fires at the
 * chosen minute; the [ReminderReceiver] re-arms the next day's alarm when it fires (and on boot /
 * time change), so the anchor never drifts. (Default reminder time is 6:00 PM — see [SettingsStore].)
 *
 * The same machinery also arms the **weekly recap** (M2): a one-shot exact alarm at Sunday
 * [RECAP_HOUR]:00, re-armed each week by the receiver, on its own request code + action so it never
 * collides with the daily alarm.
 *
 * Exact scheduling uses `setExactAndAllowWhileIdle` (fires even in Doze). On Android 12+ that needs
 * exact-alarm permission — [USE_EXACT_ALARM] (API 33+, auto-granted) / [SCHEDULE_EXACT_ALARM]
 * (API 31-32, default-granted). If it's ever unavailable we fall back to an inexact allow-while-idle
 * alarm rather than crashing — a small drift beats no reminder.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(hour: Int, minute: Int) {
        // Migration: remove the legacy WorkManager periodic reminder from older installs so it can't
        // also fire (double reminders / the old drift). No-op if it was never enqueued.
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME) }

        val am = alarmManager ?: return
        val triggerAt = nextTriggerMillis(hour, minute)
        val pending = firePendingIntent()
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }.onFailure {
            // A SecurityException can still slip through if exact-alarm access was revoked between the
            // check and the call — degrade to inexact rather than crash.
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
        }
    }

    fun cancel() {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(LEGACY_WORK_NAME) }
        alarmManager?.cancel(firePendingIntent())
    }

    /** Arm (or re-arm) the weekly recap alarm for the next Sunday at [RECAP_HOUR]:00. Same exact-alarm
     *  degradation as [schedule]. */
    fun scheduleWeekly() {
        val am = alarmManager ?: return
        val triggerAt = nextWeeklyTriggerMillis()
        val pending = weeklyPendingIntent()
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
        runCatching {
            if (canExact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }.onFailure {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending) }
        }
    }

    fun cancelWeekly() {
        alarmManager?.cancel(weeklyPendingIntent())
    }

    private fun firePendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun weeklyPendingIntent(): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE_WEEKLY)
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE_WEEKLY, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Epoch-millis of the next Sunday at [RECAP_HOUR]:00 (today if it's Sunday and still ahead). */
    private fun nextWeeklyTriggerMillis(): Long {
        val now = ZonedDateTime.now()
        var next = now.withHour(RECAP_HOUR).withMinute(0).withSecond(0).withNano(0)
        while (next.dayOfWeek != DayOfWeek.SUNDAY || !next.isAfter(now)) {
            next = next.plusDays(1)
        }
        return next.toInstant().toEpochMilli()
    }

    /** Epoch-millis of the next occurrence of hour:minute (today if still ahead, else tomorrow). */
    private fun nextTriggerMillis(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var next = now.withHour(hour.coerceIn(0, 23)).withMinute(minute.coerceIn(0, 59)).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.toInstant().toEpochMilli()
    }

    private companion object {
        const val REQUEST_CODE = 4711
        const val REQUEST_CODE_WEEKLY = 4712
        /** Hour (local, 24h) the weekly recap fires on Sunday. */
        const val RECAP_HOUR = 18
        const val LEGACY_WORK_NAME = "daily_reminder"
    }
}
