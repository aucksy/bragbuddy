package com.bragbuddy.app

import android.app.Application
import com.bragbuddy.app.data.drive.DriveBackupManager
import com.bragbuddy.app.data.entry.OfflineRecovery
import com.bragbuddy.app.di.ApplicationScope
import com.bragbuddy.app.notification.Notifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BragBuddyApp : Application() {

    @Inject lateinit var driveBackupManager: DriveBackupManager
    @Inject lateinit var offlineRecovery: OfflineRecovery
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        // Offline recovery watches connectivity for the app's lifetime: it drains queued offline
        // voice notes (transcribe → file) and retries FAILED entries whenever the network is back
        // (including right now, if the app starts online). Injecting it also spins up the
        // ConnectivityMonitor singleton the calm offline UI states read.
        offlineRecovery.start()
        // Restore-on-reinstall (only when there's no local data yet), THEN start the silent auto-backup
        // observer — never the reverse, or the observer would back up the empty state before the
        // restore lands. Both are no-ops until Drive is connected.
        appScope.launch {
            runCatching { driveBackupManager.restoreIfEmpty() }
            driveBackupManager.start(appScope)
        }
    }
}
