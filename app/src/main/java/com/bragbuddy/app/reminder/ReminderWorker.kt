package com.bragbuddy.app.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bragbuddy.app.notification.Notifications

/**
 * Posts the daily reminder. Instantiated by the default WorkManager factory (no injection needed —
 * it only posts a static notification), so it stays independent of Hilt.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Notifications.postReminder(applicationContext)
        return Result.success()
    }
}
