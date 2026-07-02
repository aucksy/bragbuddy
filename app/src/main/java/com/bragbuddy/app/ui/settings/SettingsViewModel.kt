package com.bragbuddy.app.ui.settings

import androidx.lifecycle.ViewModel
import com.bragbuddy.app.data.ai.AiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Phase 0 Settings surfaces the active AI provider label — proving the swappable seam is wired. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    aiProvider: AiProvider,
) : ViewModel() {
    val aiProviderLabel: String = aiProvider.label
}
