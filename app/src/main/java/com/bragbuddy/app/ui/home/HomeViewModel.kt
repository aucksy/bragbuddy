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
 * Home state: the reverse-chronological list of captured entries, plus per-entry actions
 * (edit the text → re-file, or delete). "Redo" (re-record from scratch) is handled by the screen
 * launching the capture sheet in replace mode. Phase 3 replaces this flat list with the structured
 * document.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: EntryRepository,
) : ViewModel() {

    val entries: StateFlow<List<EntryEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Edit an entry's words and re-run the AI on the corrected text. */
    fun editText(id: Long, text: String) = repository.replaceText(id, text)

    fun delete(id: Long) = repository.delete(id)
}
