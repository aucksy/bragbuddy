package com.bragbuddy.app.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.local.EntryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The Inbox: entries the AI couldn't confidently place (status INBOX) or couldn't reach the model
 * for (status FAILED). Phase 2 shows them so nothing is silently lost and lets a failed entry be
 * retried. The tap-to-resolve quick-confirm (assign a suggested project) is Phase 3.
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: EntryRepository,
) : ViewModel() {

    val entries: StateFlow<List<EntryEntity>> = repository.observeInbox()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun retry(id: Long) = repository.retry(id)
}
