package com.bragbuddy.app.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.legal.PrivacyPolicy
import com.bragbuddy.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The first-screen decision: whether the onboarding wizard must show, and whether it's the
 *  privacy-only re-accept variant (already onboarded, but the terms version was bumped). */
data class OnboardingGate(val showOnboarding: Boolean, val reacceptOnly: Boolean)

/**
 * Resolves the [BragNavHost] start destination from device-local settings. Kept as its own tiny VM so
 * the NavHost is only built once the flag is known — no start-destination flicker. [gate] is null while
 * DataStore is still loading.
 */
@HiltViewModel
class RootGateViewModel @Inject constructor(
    settings: SettingsStore,
) : ViewModel() {
    val gate: StateFlow<OnboardingGate?> = settings.settings
        .map { s ->
            val staleTerms = s.acceptedPrivacyVersion < PrivacyPolicy.VERSION
            OnboardingGate(
                showOnboarding = !s.onboardingComplete || staleTerms,
                reacceptOnly = s.onboardingComplete && staleTerms,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
