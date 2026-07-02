package com.bragbuddy.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.ai.AiProvider
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.prefs.AppSettings
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.prefs.TranscriptionEngine
import com.bragbuddy.app.reminder.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    aiProvider: AiProvider,
    private val settingsStore: SettingsStore,
    private val reminderScheduler: ReminderScheduler,
    private val entryRepository: EntryRepository,
) : ViewModel() {

    val aiProviderLabel: String = aiProvider.label

    val settings: StateFlow<AppSettings> =
        settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setReminderEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setReminderEnabled(enabled)
        val s = settingsStore.settings.first()
        if (enabled) reminderScheduler.schedule(s.reminderHour, s.reminderMinute) else reminderScheduler.cancel()
    }

    fun setReminderTime(hour: Int, minute: Int) = viewModelScope.launch {
        settingsStore.setReminderTime(hour, minute)
        val s = settingsStore.settings.first()
        if (s.reminderEnabled) reminderScheduler.schedule(s.reminderHour, s.reminderMinute)
    }

    fun setTranscriptionEngine(engine: TranscriptionEngine) = viewModelScope.launch {
        settingsStore.setTranscriptionEngine(engine)
    }

    fun setGroqApiKey(key: String) = viewModelScope.launch {
        settingsStore.setGroqApiKey(key)
    }

    fun setOpenRouterApiKey(key: String) = viewModelScope.launch {
        val wasBlank = settingsStore.settings.first().openRouterApiKey.isBlank()
        settingsStore.setOpenRouterApiKey(key)
        // Just added a key? Re-run anything that failed while there was no brain to reach.
        if (wasBlank && key.isNotBlank()) entryRepository.reprocessFailed()
    }
}
