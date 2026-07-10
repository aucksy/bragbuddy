package com.bragbuddy.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bragbuddy.app.R
import com.bragbuddy.app.ui.capture.CaptureLauncher

/** Notification channel + the daily reminder. Voice/tone per the design: warm, brief, never nagging. */
object Notifications {
    const val CHANNEL_REMINDER = "daily_reminder"
    private const val REMINDER_ID = 1001

    fun ensureChannels(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_REMINDER,
            "Daily reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Your once-a-day nudge to log a quick update." }
        mgr.createNotificationChannel(channel)
    }

    /** Post the reminder. Tapping it opens the capture surface into the user's *Default capture
     *  method* (Voice by default; "Ask each time" shows the 3-choice chooser) — resolved in the VM. */
    fun postReminder(context: Context) {
        val intent = CaptureLauncher.intentForDefault(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
}
