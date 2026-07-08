package com.bragbuddy.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.legal.PrivacyPolicy
import com.bragbuddy.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the first-run onboarding wizard. Only writes device-local settings flags — the framework step
 * reuses [com.bragbuddy.app.ui.framework.FrameworkScreen], which persists live through its own VM, so
 * there's nothing to save here for it.
 *
 * The finish writes **complete before we navigate away**: the screen observes [finished] and only then
 * calls its `onFinished` (which pops this VM's nav entry, cancelling this scope). Doing it the other way
 * — launch-then-navigate — would cancel the write mid-flight and re-trigger onboarding on next launch.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
) : ViewModel() {

    private val _finished = MutableStateFlow(false)
    /** Flips true only after the finish write is durably persisted — the screen navigates on this. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /** The saved role (for seeding the role step on a re-onboard); "" for a fresh install. */
    val jobRole: StateFlow<String> = settings.settings
        .map { it.jobRole }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    /** Stamp the current privacy version as accepted (the hard gate, mid-flow — no nav pop follows). */
    fun acceptPrivacy() = viewModelScope.launch {
        settings.setAcceptedPrivacyVersion(PrivacyPolicy.VERSION)
    }

    /** Save the typed role (also marks the Home first-run role prompt handled). */
    fun saveRole(role: String) = viewModelScope.launch { settings.setJobRole(role) }

    /** Skip the role step — mark the Home role prompt handled too, so it doesn't re-ask. */
    fun skipRole() = viewModelScope.launch { settings.dismissRolePrompt() }

    /** Finish the full wizard: one atomic write (complete + stamp version), THEN signal [finished]. */
    fun finish() = viewModelScope.launch {
        settings.completeOnboarding(PrivacyPolicy.VERSION)
        _finished.value = true
    }

    /** Finish the privacy-only re-accept flow (already onboarded): stamp the version, THEN signal. */
    fun finishReaccept() = viewModelScope.launch {
        settings.setAcceptedPrivacyVersion(PrivacyPolicy.VERSION)
        _finished.value = true
    }
}
