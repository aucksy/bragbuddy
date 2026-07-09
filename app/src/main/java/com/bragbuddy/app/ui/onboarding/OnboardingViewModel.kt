package com.bragbuddy.app.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.drive.DriveBackupManager
import com.bragbuddy.app.data.legal.PrivacyPolicy
import com.bragbuddy.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the first-run onboarding wizard. Writes device-local settings flags and drives the optional
 * **Recover from Drive** step. The framework step reuses [com.bragbuddy.app.ui.framework.FrameworkScreen]
 * (persists live through its own VM), so there's nothing to save here for it.
 *
 * The finish writes **complete before we navigate away**: the screen observes [finished] and only then
 * calls its `onFinished` (which pops this VM's nav entry, cancelling this scope). Doing it the other way
 * — launch-then-navigate — would cancel the write mid-flight and re-trigger onboarding on next launch. A
 * successful Drive restore uses the same signal (restore → mark complete → [finished]) to jump to Home.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val drive: DriveBackupManager,
) : ViewModel() {

    private val _finished = MutableStateFlow(false)
    /** Flips true only after the finish (or restore) write is durably persisted — the screen navigates on this. */
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    /** The saved role (for seeding the role step on a re-onboard); "" for a fresh install. */
    val jobRole: StateFlow<String> = settings.settings
        .map { it.jobRole }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // ---- Recover-from-Drive step ----

    /** State of the optional recovery step. Fresh installs are the only ones that reach onboarding, so
     *  local data is empty here — a restore is always safe (nothing to overwrite). */
    data class DriveStepState(
        val connectedEmail: String? = null,
        val checking: Boolean = false,   // signing in / checking for a backup
        val backupChecked: Boolean = false,
        val backupExists: Boolean = false,
        val restoring: Boolean = false,
        val message: String? = null,     // a calm error to show inline
    )

    // Starts NOT connected: the step always opens at "Connect Google Drive". connectedEmail only goes
    // non-null AFTER onDriveSignInResult (which also sets backupChecked), so a "connected but not yet
    // backup-checked" state can never render the "no backup found / start fresh" copy by mistake.
    private val _driveState = MutableStateFlow(DriveStepState())
    val driveState: StateFlow<DriveStepState> = _driveState.asStateFlow()

    val driveConfigured: Boolean get() = drive.isConfigured

    fun driveSignInIntent(): Intent = drive.signInClient().signInIntent

    /** After the Google Sign-In result: record the account and whether a backup exists for it. */
    fun onDriveSignInResult(data: Intent?) = viewModelScope.launch {
        _driveState.update { it.copy(checking = true, message = null) }
        drive.handleSignInResult(data)
        val email = drive.currentEmail()
        if (email == null) {
            _driveState.update {
                it.copy(checking = false, connectedEmail = null, message = "Couldn't connect to Google Drive.")
            }
            return@launch
        }
        val exists = runCatching { drive.backupExists() }.getOrDefault(false)
        _driveState.update {
            it.copy(checking = false, connectedEmail = email, backupChecked = true, backupExists = exists)
        }
    }

    /** Restore the Drive backup, then finish onboarding straight to Home (entries + role + framework all
     *  come back with it). Keep auto-backup on so the restored record stays in sync. */
    fun restoreFromDriveAndFinish() = viewModelScope.launch {
        _driveState.update { it.copy(restoring = true, message = null) }
        val ok = runCatching { drive.restoreFromDrive() }.getOrDefault(false)
        if (ok) {
            settings.setDriveAutoBackup(true)
            settings.completeOnboarding(PrivacyPolicy.VERSION)
            _finished.value = true
        } else {
            _driveState.update { it.copy(restoring = false, message = "Couldn't restore — try again, or continue and set up fresh.") }
        }
    }

    /** The user connected but chose NOT to restore an existing backup — preserve that backup by pausing
     *  auto-backup so a fresh capture can't overwrite it. They can restore later or re-enable in Settings. */
    fun declineRestore() = viewModelScope.launch {
        if (_driveState.value.connectedEmail != null && _driveState.value.backupExists) {
            settings.setDriveAutoBackup(false)
        }
    }

    // ---- Standard steps ----

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
