package com.bragbuddy.app.ui.backup

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.backup.BackupRepository
import com.bragbuddy.app.data.drive.DriveBackupManager
import com.bragbuddy.app.data.prefs.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings → Google Drive backup (Design System §6). Connect Drive, see backup health + size, back up
 * now, restore, and the local export/import fallback. All Drive work is fail-safe — errors surface as
 * a toast and local data is always the source of truth.
 */
@HiltViewModel
class BackupViewModel @Inject constructor(
    private val driveBackupManager: DriveBackupManager,
    private val backupRepository: BackupRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    enum class Busy { CONNECTING, BACKING_UP, RESTORING, EXPORTING }

    data class UiState(
        val configured: Boolean = true,
        val connectedEmail: String? = null,
        val autoBackup: Boolean = true,
        val lastBackupAt: Long = 0L,
        val sizeLabel: String? = null,
        val backupExists: Boolean = false,
        val busy: Busy? = null,
    )

    private val email = MutableStateFlow(driveBackupManager.currentEmail())
    private val size = MutableStateFlow<String?>(null)
    private val backupExists = MutableStateFlow(false)
    private val busy = MutableStateFlow<Busy?>(null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun consumeMessage() { _message.value = null }

    val state: StateFlow<UiState> = combine(
        settingsStore.settings, email, size, backupExists, busy,
    ) { s, e, sz, exists, b ->
        UiState(
            configured = driveBackupManager.isConfigured,
            connectedEmail = e,
            autoBackup = s.driveAutoBackup,
            lastBackupAt = s.driveLastBackupAt,
            sizeLabel = sz,
            backupExists = exists,
            busy = b,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    init {
        refreshSize()
        refreshBackupExists()
    }

    private fun refreshSize() = viewModelScope.launch {
        size.value = runCatching { backupRepository.backupSizeLabel() }.getOrNull()
    }

    private fun refreshBackupExists() = viewModelScope.launch {
        if (email.value != null) backupExists.value = driveBackupManager.backupExists()
    }

    /** The Intent to start Google Sign-In (launched from the screen's Activity-result launcher). */
    fun signInIntent(): Intent = driveBackupManager.signInClient().signInIntent

    fun onSignInResult(data: Intent?) = viewModelScope.launch {
        busy.value = Busy.CONNECTING
        val connected = driveBackupManager.handleSignInResult(data)
        email.value = driveBackupManager.currentEmail()
        if (connected != null && email.value != null) {
            val exists = runCatching { driveBackupManager.backupExists() }.getOrDefault(false)
            backupExists.value = exists
            val empty = backupRepository.isLocalEmpty()
            when {
                // Restore-on-reinstall: local is empty and Drive has a backup → pull it back (nothing
                // to lose, and it closes the window where a first capture would clobber the cloud copy).
                exists && empty -> {
                    val ok = runCatching { driveBackupManager.restoreFromDrive() }.getOrElse { _message.value = driveError(it); false }
                    if (ok) _message.value = "Restored from Drive"
                }
                // Seed the first cloud backup from existing local data (never overwrites an existing one).
                !exists && !empty -> runCatching { driveBackupManager.backupNow() }
                    .onSuccess { backupExists.value = true }
                    .onFailure { _message.value = driveError(it) }
                // exists && !empty → leave the choice to the user (Back up now / Restore).
                // !exists && empty → nothing to back up yet.
            }
        } else {
            _message.value = "Couldn't connect to Google Drive."
        }
        busy.value = null
        refreshSize()
    }

    fun disconnect() = viewModelScope.launch {
        driveBackupManager.signOut()
        email.value = null
        backupExists.value = false
    }

    fun setAutoBackup(enabled: Boolean) = viewModelScope.launch { settingsStore.setDriveAutoBackup(enabled) }

    fun backupNow() = viewModelScope.launch {
        busy.value = Busy.BACKING_UP
        runCatching { driveBackupManager.backupNow() }
            .onSuccess { backupExists.value = true; _message.value = "Backed up to Drive" }
            .onFailure { _message.value = driveError(it) }
        busy.value = null
    }

    fun restoreFromDrive() = viewModelScope.launch {
        busy.value = Busy.RESTORING
        val ok = runCatching { driveBackupManager.restoreFromDrive() }.getOrElse { _message.value = driveError(it); false }
        _message.value = if (ok) "Restored from Drive" else _message.value ?: "No backup found on Drive."
        busy.value = null
        refreshSize()
    }

    fun exportFileName(): String = "bragbuddy-backup.json"

    fun exportToUri(uri: Uri) = viewModelScope.launch {
        busy.value = Busy.EXPORTING
        val ok = backupRepository.exportToUri(uri)
        _message.value = if (ok) "Saved a copy to your device" else "Couldn't save the file"
        busy.value = null
    }

    fun importFromUri(uri: Uri) = viewModelScope.launch {
        busy.value = Busy.RESTORING
        val ok = backupRepository.importFromUri(uri)
        _message.value = if (ok) "Restored from file" else "That file isn't a BragBuddy backup"
        busy.value = null
        refreshSize()
    }

    private fun driveError(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: "Google Drive error — try again."
}
