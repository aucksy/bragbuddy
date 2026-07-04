package com.bragbuddy.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.reminder.ReminderScheduler
import com.bragbuddy.app.ui.navigation.BragNavHost
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var entryRepository: EntryRepository

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* scheduling is independent of the result */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask to post the daily reminder (Android 13+); harmless if already granted/denied.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Keep the reminder in sync with the saved settings on every launch.
        lifecycleScope.launch {
            val s = settingsStore.settings.first()
            if (s.reminderEnabled) reminderScheduler.schedule(s.reminderHour, s.reminderMinute)
            else reminderScheduler.cancel()
        }

        // Categorize anything left RAW by an interrupted run (never lose an entry).
        entryRepository.processPending()
        // Rebuild/repair the running rollup from processed entries (seeds it on the v0.13 upgrade and
        // self-heals any drift). Off the summary path; the summary itself only ever reads the rollup.
        entryRepository.reconcileRollup()

        setContent {
            BragBuddyTheme {
                BragNavHost()
            }
        }
    }
}
