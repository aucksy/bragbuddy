package com.bragbuddy.app.ui.onboarding

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.drive.DriveBackupManager
import com.bragbuddy.app.data.entry.EntryRepository
import com.bragbuddy.app.data.legal.PrivacyPolicy
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.INBOX_PLACEMENT
import com.bragbuddy.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val repository: EntryRepository,
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

    // ---- Aha rehearsal (M2 · final step) ----

    /** The made-real "YOUR RECORD · READY" beat: log one real win, watch it file live. */
    sealed interface RehearsalUi {
        /** Waiting for the user to log their first win. */
        data object Prompt : RehearsalUi
        /** Captured — the categorizer is filing it. */
        data object Filing : RehearsalUi
        /** Filed into a real goal area (managed AI live) — the made-real card. */
        data class Ready(val bullet: String, val goalArea: String?) : RehearsalUi
        /** Saved but not yet filed into a category (no AI configured / low confidence). Still kept —
         *  it files itself once AI is set up. Honest graceful-degrade for the pre-proxy state. */
        data class Saved(val bullet: String) : RehearsalUi
    }

    /** Highest entry id that existed when the rehearsal step opened; any newer row is "the first win".
     *  Null = the step hasn't begun observing yet (so an unrelated background row can't trigger it). */
    private val rehearsalBaseline = MutableStateFlow<Long?>(null)

    /** Snapshot the baseline when the rehearsal step opens. Idempotent (a recomposition can't re-arm it). */
    fun beginRehearsal() {
        if (rehearsalBaseline.value != null) return
        viewModelScope.launch {
            rehearsalBaseline.value = repository.observeAll().first().maxOfOrNull { it.id } ?: 0L
        }
    }

    val rehearsal: StateFlow<RehearsalUi> = combine(
        repository.observeAll(), rehearsalBaseline,
    ) { entries, baseline ->
        if (baseline == null) return@combine RehearsalUi.Prompt
        val fresh = entries.filter { it.id > baseline }.maxByOrNull { it.id }
            ?: return@combine RehearsalUi.Prompt
        val text = fresh.bullet?.takeIf { it.isNotBlank() } ?: fresh.rawTranscript
        when (fresh.status) {
            EntryStatus.RAW, EntryStatus.PENDING_AUDIO, EntryStatus.PENDING_IMAGE -> RehearsalUi.Filing
            EntryStatus.PROCESSED -> RehearsalUi.Ready(
                bullet = text,
                goalArea = fresh.goalCategory?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.equals(INBOX_PLACEMENT, ignoreCase = true) },
            )
            EntryStatus.INBOX, EntryStatus.FAILED -> RehearsalUi.Saved(text)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RehearsalUi.Prompt)

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
