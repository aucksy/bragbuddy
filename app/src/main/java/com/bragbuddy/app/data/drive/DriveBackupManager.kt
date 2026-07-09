package com.bragbuddy.app.data.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.bragbuddy.app.data.backup.BackupRepository
import com.bragbuddy.app.data.prefs.SettingsStore
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** A user-facing failure talking to Google Drive (carries a short message for the UI). */
class DriveException(message: String) : Exception(message)

/**
 * Google Drive backup/restore (Phase 6). Mirrors the sibling apps' proven approach: Google Sign-In
 * with the narrow `drive.file` scope, one canonical restore file + one readable document inside a
 * dedicated "BragBuddy" folder, and the Drive v3 REST API hit directly with the OAuth access token
 * (no heavyweight Drive SDK). The bytes come from [BackupRepository]; this class only owns "where they
 * go". Fail-safe: any Drive error surfaces as a [DriveException]; local data is always the source of
 * truth and manual export still works.
 */
@Singleton
class DriveBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val settingsStore: SettingsStore,
) {
    private val signInOptions: GoogleSignInOptions by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveConfig.DRIVE_FILE_SCOPE))
            .requestIdToken(DriveConfig.WEB_CLIENT_ID)
            .build()
    }

    val isConfigured: Boolean get() = DriveConfig.isConfigured

    fun signInClient(): GoogleSignInClient = GoogleSignIn.getClient(context, signInOptions)

    /** The signed-in Google account email, or null if not connected. */
    fun currentEmail(): String? = GoogleSignIn.getLastSignedInAccount(context)?.email

    /** Parse the sign-in Activity result; returns the account email, or null if it failed/cancelled. */
    fun handleSignInResult(data: Intent?): String? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return runCatching { task.getResult(ApiException::class.java) }.getOrNull()?.email
    }

    suspend fun signOut() {
        withContext(Dispatchers.IO) { runCatching { Tasks.await(signInClient().signOut()) } }
    }

    /**
     * Auto-push a fresh backup when the backed-up data changes (debounced, silent). Start once from the
     * Application, AFTER any restore has run. Skips the initial hydration emission so connecting doesn't
     * immediately re-upload.
     */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        if (!DriveConfig.isConfigured) return
        backupRepository.changeSignal()
            .drop(1) // skip the initial hydration emission so connecting doesn't immediately re-upload
            .debounce(DEBOUNCE_MS)
            .onEach {
                if (!settingsStore.settings.first().driveAutoBackup) return@onEach
                if (currentEmail() == null) return@onEach
                // Never overwrite a real Drive backup with an empty local state (e.g. a fresh reinstall
                // that hasn't restored yet) — backupNow() also enforces this.
                if (backupRepository.isLocalEmpty()) return@onEach
                runCatching { backupNow() } // silent — manual backup stays available if this fails
            }
            .flowOn(Dispatchers.IO)
            .launchIn(scope)
    }

    /** Upload the restore JSON + the readable doc to Drive, creating the folder/files as needed. */
    suspend fun backupNow(): Long = withContext(Dispatchers.IO) {
        // Firm guard: an empty state must never replace a real backup (create-before-delete would then
        // delete the good file). On genuine first use there's simply nothing to back up.
        if (backupRepository.isLocalEmpty()) throw DriveException("Nothing to back up yet.")
        val token = accessToken()
        val folderId = ensureFolderId(token)
        uploadReplacing(token, folderId, DriveConfig.FILE_NAME, "application/json", backupRepository.exportJson())
        // Readable doc is a best-effort convenience — never fail the whole backup if it hiccups.
        runCatching {
            uploadReplacing(token, folderId, DriveConfig.DOC_FILE_NAME, "text/plain", backupRepository.exportReadableDoc())
        }
        val now = System.currentTimeMillis()
        settingsStore.setDriveLastBackupAt(now)
        now
    }

    /** Restore state from the Drive backup. Returns false if there's no backup yet. */
    suspend fun restoreFromDrive(): Boolean = withContext(Dispatchers.IO) {
        val token = accessToken()
        val folderId = findFolderId(token) ?: return@withContext false
        val id = findFileId(token, folderId, DriveConfig.FILE_NAME) ?: return@withContext false
        backupRepository.importJson(downloadFile(token, id))
    }

    /** Whether a restore file already exists on Drive — WITHOUT creating the folder or touching anything. */
    suspend fun backupExists(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val token = accessToken()
            val folderId = findFolderId(token) ?: return@runCatching false
            findFileId(token, folderId, DriveConfig.FILE_NAME) != null
        }.getOrDefault(false)
    }

    // --- Drive v3 REST (raw HTTP with the access token) ---

    private fun accessToken(): String {
        val account: Account = GoogleSignIn.getLastSignedInAccount(context)?.account
            ?: throw DriveException("Not signed in to Google.")
        return GoogleAuthUtil.getToken(context, account, "oauth2:${DriveConfig.DRIVE_FILE_SCOPE}")
    }

    private fun findFolderId(token: String): String? {
        val q = URLEncoder.encode(
            "name = '${DriveConfig.FOLDER_NAME}' and mimeType = '${DriveConfig.FOLDER_MIME}' and trashed = false",
            "UTF-8",
        )
        val list = request("GET", "$DRIVE/files?q=$q&spaces=drive&pageSize=1&fields=files(id)", token)
        return JSONObject(list).optJSONArray("files")?.takeIf { it.length() > 0 }
            ?.getJSONObject(0)?.getString("id")
    }

    private fun ensureFolderId(token: String): String {
        findFolderId(token)?.let { return it }
        val body = JSONObject()
            .put("name", DriveConfig.FOLDER_NAME)
            .put("mimeType", DriveConfig.FOLDER_MIME)
            .toString()
        val created = request("POST", "$DRIVE/files?fields=id", token, "application/json", body)
        return JSONObject(created).getString("id")
    }

    private fun findFileId(token: String, folderId: String, name: String): String? {
        val q = URLEncoder.encode(
            "name = '$name' and '$folderId' in parents and trashed = false",
            "UTF-8",
        )
        val res = request(
            "GET",
            "$DRIVE/files?q=$q&spaces=drive&orderBy=modifiedTime desc&pageSize=1&fields=files(id)",
            token,
        )
        return JSONObject(res).optJSONArray("files")?.takeIf { it.length() > 0 }
            ?.getJSONObject(0)?.getString("id")
    }

    /** Create the new file FIRST, then delete the old one — never delete-before-create (that once
     *  destroyed a real backup). If create fails, the old survives; if delete fails, a harmless dup. */
    private fun uploadReplacing(token: String, folderId: String, name: String, mime: String, data: String) {
        val existing = findFileId(token, folderId, name)
        createFile(token, folderId, name, mime, data)
        if (existing != null) runCatching { deleteFile(token, existing) }
    }

    private fun createFile(token: String, folderId: String, name: String, mime: String, data: String) {
        val boundary = "bragbuddyboundary"
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", mime)
            .put("parents", JSONArray().put(folderId))
            .toString()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n")
            append("--$boundary\r\nContent-Type: $mime; charset=UTF-8\r\n\r\n$data\r\n")
            append("--$boundary--")
        }
        request("POST", "$UPLOAD/files?uploadType=multipart&fields=id", token, "multipart/related; boundary=$boundary", body)
    }

    private fun deleteFile(token: String, id: String) {
        request("DELETE", "$DRIVE/files/$id", token)
    }

    private fun downloadFile(token: String, id: String): String =
        request("GET", "$DRIVE/files/$id?alt=media", token)

    private fun request(
        method: String,
        url: String,
        token: String,
        contentType: String? = null,
        body: String? = null,
    ): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            if (contentType != null) setRequestProperty("Content-Type", contentType)
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw DriveException("Google Drive request failed ($code). ${text.take(140)}")
            text
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val DRIVE = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val DEBOUNCE_MS = 5_000L
    }
}
