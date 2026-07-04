package com.bragbuddy.app.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import com.bragbuddy.app.notification.Notifications
import java.util.Locale

/**
 * Snapshot of everything the OS lets us verify about whether the daily reminder can actually fire
 * (Phase 7 · OEM reliability). OEM auto-start permission is deliberately absent — no public API can
 * read it, so the Reliable-reminders screen tracks it as a user-confirmed step instead.
 */
data class ReminderHealth(
    /** Notifications can actually appear: allowed at the app level AND the "Daily reminder"
     *  channel isn't blocked (long-press → "Turn off notifications" blocks just the channel). */
    val notificationsEnabled: Boolean,
    /** The reminder CHANNEL specifically is blocked while app-level notifications are fine —
     *  the fix screen then deep-links to the channel, not the app page. */
    val reminderChannelBlocked: Boolean,
    /** Exact alarms are available ([AlarmManager.canScheduleExactAlarms]; always true below S). */
    val exactAlarmsAllowed: Boolean,
    /** The app is exempt from battery optimization (Doze can't silently kill the alarm receiver). */
    val batteryUnrestricted: Boolean,
    /** Battery optimization being active is only a REAL alarm-killer on aggressive OEMs (ColorOS,
     *  MIUI…). On stock-ish Android the default optimization still lets setExactAndAllowWhileIdle
     *  fire, so it alone must not flag every fresh install as at-risk. */
    val batteryMatters: Boolean,
) {
    /** A detectable condition that can genuinely silence the reminder on this device. */
    val atRisk: Boolean
        get() = !notificationsEnabled || !exactAlarmsAllowed || (batteryMatters && !batteryUnrestricted)

    /** Stable signature of the currently-unhealthy probes. The Home card's dismissal stores this —
     *  so dismissing today's risk still lets a DIFFERENT risk resurface the card later. */
    val riskSignature: String
        get() = buildString {
            if (!notificationsEnabled) append('n')
            if (!exactAlarmsAllowed) append('a')
            if (batteryMatters && !batteryUnrestricted) append('b')
        }
}

object ReliabilityCheck {

    /** Read the current state. Every probe is best-effort: an OEM that throws is treated as
     *  healthy (never nag on a false positive). */
    fun check(context: Context): ReminderHealth {
        val nm = runCatching { NotificationManagerCompat.from(context) }.getOrNull()
        val appNotifications = runCatching { nm?.areNotificationsEnabled() ?: true }.getOrDefault(true)
        // A channel that hasn't been created yet reads null → healthy.
        val channelBlocked = runCatching {
            nm?.getNotificationChannel(Notifications.CHANNEL_REMINDER)?.importance ==
                NotificationManager.IMPORTANCE_NONE
        }.getOrDefault(false)

        val exactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: true
            }.getOrDefault(true)
        } else {
            true
        }

        val battery = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) ?: true
        }.getOrDefault(true)

        return ReminderHealth(
            notificationsEnabled = appNotifications && !channelBlocked,
            reminderChannelBlocked = appNotifications && channelBlocked,
            exactAlarmsAllowed = exactAlarms,
            batteryUnrestricted = battery,
            batteryMatters = isAggressiveOem(),
        )
    }

    private fun isAggressiveOem(): Boolean {
        val make = Build.MANUFACTURER.lowercase(Locale.ROOT)
        return AGGRESSIVE_OEMS.any { make.contains(it) }
    }

    /** Manufacturers whose battery managers are known to kill exact alarms / background receivers
     *  unless the app is exempted (dontkillmyapp.com's usual suspects; the Find X9s is ColorOS). */
    private val AGGRESSIVE_OEMS = setOf(
        "oppo", "realme", "oneplus", "oplus", // ColorOS family
        "xiaomi", "redmi", "poco", // MIUI / HyperOS
        "vivo", "iqoo", // Funtouch / OriginOS
        "huawei", "honor", // EMUI / MagicOS
        "meizu", "asus", "tecno", "infinix", "itel", "letv", "unihertz",
    )
}
