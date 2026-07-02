package com.bragbuddy.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.local.EntryDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Phase 0 Home state. Observing the entry count exercises the full Room + Hilt + Flow wiring, so a
 * clean run to the (empty) Home screen also proves the data layer is connected end-to-end.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    entryDao: EntryDao,
) : ViewModel() {

    val entryCount: StateFlow<Int> = entryDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
