package com.bragbuddy.app.ui.home

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
 * Phase 1 Home state: the reverse-chronological list of captured entries. Phase 3 replaces this
 * flat list with the structured pillar document; for now it proves capture → save → show.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    entries: EntryRepository,
) : ViewModel() {

    val entries: StateFlow<List<EntryEntity>> = entries.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
