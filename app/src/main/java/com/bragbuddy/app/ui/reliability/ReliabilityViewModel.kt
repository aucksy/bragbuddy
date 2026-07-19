package com.bragbuddy.app.ui.reliability

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.prefs.AppSettings
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.notification.Notifications
import com.bragbuddy.app.reminder.ReliabilityCheck
import com.bragbuddy.app.reminder.ReminderHealth
import com.bragbuddy.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the "Reliable reminders" guided screen (Phase 7 · the OEM battery/alarm wizard). Holds the
 * live [ReminderHealth] probe and the user-confirmed auto-start step; every [refresh] also re-arms
 * the alarm so a just-granted permission takes effect immediately, not on the next launch.
 */
@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val _health = MutableStateFlow(ReliabilityCheck.check(appContext))
    val health: StateFlow<ReminderHealth> = _health.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Re-probe the system state (after returning from a settings screen / system dialog) and
     *  re-arm the daily alarm under whatever access is now granted. */
    fun refresh() {
        _health.value = ReliabilityCheck.check(appContext)
        viewModelScope.launch {
            val s = settingsStore.settings.first()
            if (s.reminderEnabled) reminderScheduler.schedule(s.reminderHour, s.reminderMinute, s.reminderDays)
        }
    }

    /** The OEM auto-start toggle can't be read programmatically — the user confirms it by hand. */
    fun setAutostartDone(done: Boolean) = viewModelScope.launch { settingsStore.setOemAutostartDone(done) }

    /** Fire the real reminder notification right now — the honest end-to-end check. */
    fun sendTestReminder() {
        Notifications.postReminder(appContext)
    }
}
