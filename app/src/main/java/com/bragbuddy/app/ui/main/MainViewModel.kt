package com.bragbuddy.app.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-shell state — just the Inbox badge count for the bottom bar. */
@HiltViewModel
class MainViewModel @Inject constructor(
    repository: EntryRepository,
) : ViewModel() {
    val inboxCount: StateFlow<Int> = repository.observeInboxCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
