package com.bragbuddy.app

import android.app.Application
import com.bragbuddy.app.data.drive.DriveBackupManager
import com.bragbuddy.app.di.ApplicationScope
import com.bragbuddy.app.notification.Notifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BragBuddyApp : Application() {

    @Inject lateinit var driveBackupManager: DriveBackupManager
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Restore-on-reinstall (only when there's no local data yet), THEN start the silent auto-backup
        // observer — never the reverse, or the observer would back up the empty state before the
        // restore lands. Both are no-ops until Drive is connected.
        appScope.launch {
            runCatching { driveBackupManager.restoreIfEmpty() }
            driveBackupManager.start(appScope)
        }
    }
}
