package com.bragbuddy.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bragbuddy.app.MainActivity
import com.bragbuddy.app.R

/** Notification channels + the daily reminder and weekly recap. Voice/tone per the design: warm,
 *  brief, never nagging. */
object Notifications {
    const val CHANNEL_REMINDER = "daily_reminder"
    const val CHANNEL_RECAP = "weekly_recap"
    private const val REMINDER_ID = 1001
    private const val RECAP_ID = 1002

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                "Daily reminder",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Your once-a-day nudge to log a quick update." },
        )
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECAP,
                "Weekly recap",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "A quiet Sunday summary of the wins you logged this week." },
        )
    }

    /** Post the reminder. Tapping it opens the app on Home with the **3-input capture radial** already
     *  fanned out (the same state as tapping "+") — never a fixed mode, never auto-recording. The shell
     *  (MainScaffold) opens the radial off [MainActivity.EXTRA_OPEN_CAPTURE]. */
    fun postReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_CAPTURE, true)
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_briefcase)
            .setContentTitle("Log today's work wins now")
            .setContentText("Before you forget them.")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()
        // On Android 13+ this is a no-op without POST_NOTIFICATIONS; that permission is requested once
        // via the first-run rationale popup on Home (see NotificationPrimer / NotificationPrimerSheet).
        runCatching { NotificationManagerCompat.from(context).notify(REMINDER_ID, notification) }
    }

    /**
     * Post the weekly recap (M2) — celebratory, local-data-only ("This week: N wins, M with numbers").
     * Tapping opens the app (its launcher = Home) to review. Caller decides whether to post (only when
     * [wins] > 0 and the recap is enabled). No-op without POST_NOTIFICATIONS, like the reminder.
     */
    fun postWeeklyRecap(context: Context, wins: Int, withNumbers: Int) {
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = open?.let {
            PendingIntent.getActivity(
                context, 2, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val winWord = if (wins == 1) "win" else "wins"
        val text = if (withNumbers > 0) {
            "$wins $winWord logged — $withNumbers with a number. Tap to review your record."
        } else {
            "$wins $winWord logged this week. Tap to review your record."
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RECAP)
            .setSmallIcon(R.drawable.ic_stat_briefcase)
            .setContentTitle("This week on BragBuddy")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (pending != null) builder.setContentIntent(pending)
        runCatching { NotificationManagerCompat.from(context).notify(RECAP_ID, builder.build()) }
    }
}
