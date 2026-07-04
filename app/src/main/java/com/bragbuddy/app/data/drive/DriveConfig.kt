package com.bragbuddy.app.data.drive

/**
 * Google Drive backup configuration (Phase 6).
 *
 * Reuses the owner's existing Google Cloud project — the SAME one ColorCloset / NotDigest use — so no
 * new Web client is needed. The one gated owner step is to add an **Android OAuth client** for
 * `com.bragbuddy.app` + the release-keystore SHA-1 to that project, so Google Sign-In succeeds on the
 * signed build. We request only the narrow `drive.file` scope, so the app can ever see just the files
 * it creates in its own folder — nothing else in the user's Drive.
 *
 * Until that Android client is added, the app builds and installs fine; connecting Drive simply
 * returns a clear sign-in error and manual "Export to device" still works.
 */
object DriveConfig {
    /** Web OAuth client id — project-level, shared with the sibling apps' project (gmailapi-491903). */
    const val WEB_CLIENT_ID = "240978491498-ddd3k6hv2bgqv6ovkl662mguv5tt8nr2.apps.googleusercontent.com"

    /** The visible folder created in the user's Drive; holds the restore data + the readable doc. */
    const val FOLDER_NAME = "BragBuddy"
    const val FOLDER_MIME = "application/vnd.google-apps.folder"

    /** The structured restore backup (machine-readable). */
    const val FILE_NAME = "bragbuddy-backup.json"

    /** The human-readable appraisal document, openable from any computer (Build Brief § Backup). */
    const val DOC_FILE_NAME = "BragBuddy record.txt"

    const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

    /** True once a real Web client id is present (lets the UI hide Drive cleanly if it's ever blanked). */
    val isConfigured: Boolean get() = WEB_CLIENT_ID.isNotBlank() && !WEB_CLIENT_ID.startsWith("REPLACE")
}
