package com.bragbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.reminder.ReminderScheduler
import com.bragbuddy.app.ui.navigation.BragNavHost
import com.bragbuddy.app.ui.theme.BragBuddyThemedApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsStore: SettingsStore
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var entryRepository: EntryRepository

    /** Flipped true once the theme prefs load ([BragBuddyThemedApp] `onReady`); until then the splash
     *  stays up so a forced Light/Dark theme never flashes the wrong colours on cold start. */
    private var themeReady = false

    /** Bumped each time we're launched/re-launched with [EXTRA_OPEN_CAPTURE] (the daily reminder tap):
     *  a monotonically increasing signal the shell observes to open the Home "+" capture radial. A
     *  counter (not a Boolean) so a fresh tap while the app is already open still triggers it; the shell
     *  tracks the last value it handled so a plain Home re-composition (e.g. returning from Settings)
     *  never re-opens it. Snapshot state so the running composition observes [onNewIntent] bumps. */
    private val openCaptureSignal = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        splash.setKeepOnScreenCondition { !themeReady }
        // Bump ONLY on a genuine fresh launch (savedInstanceState == null). A config-change / auto
        // dark-mode / process-death recreation re-runs onCreate with the same sticky launch intent, so
        // an unconditional bump would re-derive a stale signal and re-open the radial with no tap; the
        // shell's rememberSaveable last-handled counter would already be ahead. (onNewIntent handles the
        // app-already-running tap.)
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_OPEN_CAPTURE, false) == true) {
            openCaptureSignal.value++
        }

        // Notification permission (Android 13+) is NO LONGER requested here — the naked OS dialog used
        // to pop over the Welcome screen on a fresh install. It's now asked once via a rationale popup
        // on first Home (Phase 3 · NotificationPrimer / NotificationPrimerSheet in the main shell).

        // Keep the daily reminder + weekly recap alarms in sync with saved settings on every launch.
        lifecycleScope.launch {
            val s = settingsStore.settings.first()
            if (s.reminderEnabled) reminderScheduler.schedule(s.reminderHour, s.reminderMinute)
            else reminderScheduler.cancel()
            if (s.weeklyRecapEnabled) reminderScheduler.scheduleWeekly()
            else reminderScheduler.cancelWeekly()
        }

        // Categorize anything left RAW by an interrupted run (never lose an entry).
        entryRepository.processPending()
        // Rebuild/repair the running rollup from processed entries (seeds it on the v0.13 upgrade and
        // self-heals any drift). Off the summary path; the summary itself only ever reads the rollup.
        entryRepository.reconcileRollup()

        setContent {
            BragBuddyThemedApp(onReady = { themeReady = true }) {
                BragNavHost(openCaptureSignal = openCaptureSignal.value)
            }
        }
    }

    /** singleTask: a reminder tap while the app is already running arrives here. Bump the signal so the
     *  shell opens the capture radial (and stash the intent so a later read sees the latest). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CAPTURE, false)) openCaptureSignal.value++
    }

    companion object {
        /** Boolean extra: the daily reminder sets this so the shell opens the Home "+" capture radial
         *  (the 3-input chooser) instead of any fixed capture mode. */
        const val EXTRA_OPEN_CAPTURE = "open_capture"
    }
}
