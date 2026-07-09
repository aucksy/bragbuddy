package com.bragbuddy.app

import android.app.Application
import com.bragbuddy.app.data.drive.DriveBackupManager
import com.bragbuddy.app.data.entry.OfflineRecovery
import com.bragbuddy.app.di.ApplicationScope
import com.bragbuddy.app.notification.Notifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
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
        // Start the silent auto-backup observer. It never uploads an empty state and, once a backup
        // exists, stays paused until the user settles a Drive connection — so a reinstall's fresh/empty
        // state can't overwrite a previous backup. Recovery is now an EXPLICIT choice (the onboarding
        // "Recover from Drive" step, or Settings → connect), never a silent launch-time restore. No-op
        // until Drive is connected.
        driveBackupManager.start(appScope)
    }
}
