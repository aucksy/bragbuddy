package com.bragbuddy.app.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.reminder.ReliabilityCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-shell state — the Inbox badge count plus the first-run notification primer. (The weekly
 *  catch-up sheet was retired in M2 in favour of the weekly recap NOTIFICATION.) */
@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: EntryRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val inboxCount: StateFlow<Int> = repository.observeInboxCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Whether the first-run notification-rationale popup has been handled (Phase 3). Null while the
     *  DataStore value is still loading — the shell waits for a real value before deciding, so a
     *  brand-new install never briefly reads the `false` default and flashes the popup incorrectly. */
    val notifPrimerHandled: StateFlow<Boolean?> = settingsStore.settings
        .map { it.notifPrimerHandled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Record the notification primer as handled (the user allowed, or it was auto-satisfied). */
    fun markNotifPrimerHandled() = viewModelScope.launch {
        settingsStore.setNotifPrimerHandled(true)
    }

    /** The user declined the primer ("Maybe later" / dismissed / OS-denied). Mark it handled AND record
     *  the current reminder-risk signature as acknowledged, so the Home reliability card doesn't turn
     *  around and immediately re-nag about the very notifications they just declined. A genuinely NEW
     *  risk later (a different signature) still resurfaces the card. */
    fun markNotifPrimerDeclined() = viewModelScope.launch {
        val acknowledgedRisks = ReliabilityCheck.check(appContext).riskSignature
        settingsStore.setNotifPrimerDeclined(acknowledgedRisks)
    }
}
