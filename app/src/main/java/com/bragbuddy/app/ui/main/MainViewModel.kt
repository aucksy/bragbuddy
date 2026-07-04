package com.bragbuddy.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.retention.RetentionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** App-shell state — the Inbox badge count plus the Phase 7 weekly catch-up trigger. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: EntryRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val inboxCount: StateFlow<Int> = repository.observeInboxCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _showCatchup = MutableStateFlow(false)

    /** The Design §7 weekly catch-up sheet is on screen. */
    val showCatchup: StateFlow<Boolean> = _showCatchup.asStateFlow()

    /** The ISO week the visible sheet was shown FOR — stamped on handle. Using show-time (not
     *  handle-time) means a sheet skipped just past Sunday midnight can't eat next week's window. */
    private var shownWeekKey: String? = null

    /**
     * Evaluate the catch-up once per shell composition (an app open): first open inside the
     * Fri-evening→Sunday window, at most once per ISO week, opt-out in Settings, and only for
     * someone who has actually logged before. Never re-triggers while already showing.
     */
    fun maybeShowCatchup() = viewModelScope.launch {
        if (_showCatchup.value) return@launch
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val s = settingsStore.settings.first()
        val hasAnyEntry = repository.observeCount().first() > 0
        val due = RetentionPolicy.catchupDue(
            nowMillis = now,
            zone = zone,
            enabled = s.catchupEnabled,
            lastShownWeekKey = s.catchupLastShownWeek,
            hasAnyEntry = hasAnyEntry,
        )
        if (due) {
            shownWeekKey = RetentionPolicy.weekKey(now, zone)
            _showCatchup.value = true
        }
    }

    /** Close the sheet and stamp the week it was SHOWN for — "Add something", "Not this week" and
     *  the scrim all count as handled (one soft question, then it's gone for the week). */
    fun catchupHandled() = viewModelScope.launch {
        _showCatchup.value = false
        settingsStore.setCatchupLastShownWeek(
            shownWeekKey ?: RetentionPolicy.weekKey(System.currentTimeMillis(), ZoneId.systemDefault()),
        )
        shownWeekKey = null
    }
}
