package com.bragbuddy.app.data.backup

import com.bragbuddy.app.data.entry.EntryProcessor
import com.bragbuddy.app.data.framework.FrameworkStore
import com.bragbuddy.app.data.local.EntryDao
import com.bragbuddy.app.data.local.ProjectDao
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.project.ProjectRepository
import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.bragbuddy.app.data.local.BragBuddyDatabase
import com.bragbuddy.app.data.summary.SummaryStore
import com.bragbuddy.app.ui.home.buildHomeDoc
import com.bragbuddy.app.ui.home.exportDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gathers the whole device-local state into a portable [BackupSnapshot] (and applies one back). This
 * is the single source the Drive layer and the local export/import both use — the Drive manager owns
 * only "where the bytes go", never what's in them.
 *
 * Restore is a wholesale replace (a reinstall or an explicit user restore) followed by a rollup
 * reconcile, so the derived running rollup is rebuilt from the restored entries. The Groq key and any
 * audio are never part of a backup.
 */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: BragBuddyDatabase,
    private val entryDao: EntryDao,
    private val projectDao: ProjectDao,
    private val frameworkStore: FrameworkStore,
    private val settingsStore: SettingsStore,
    private val summaryStore: SummaryStore,
    private val projects: ProjectRepository,
    private val processor: EntryProcessor,
) {

    /** Serialise the current state to the backup JSON string. */
    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val s = settingsStore.settings.first()
        BackupCodec.encode(
            BackupSnapshot(
                entries = entryDao.getAllOnce(),
                projects = projectDao.getAllOnce(),
                pillars = frameworkStore.framework.first().pillars,
                settings = BackupSettings(
                    reminderEnabled = s.reminderEnabled,
                    reminderHour = s.reminderHour,
                    reminderMinute = s.reminderMinute,
                    lastCaptureMode = s.lastCaptureMode,
                    jobRole = s.jobRole,
                    rolePromptDismissed = s.rolePromptDismissed,
                    reviewYearStartMonth = s.reviewYearStartMonth,
                ),
                summariesRaw = summaryStore.exportRaw(),
            ),
        )
    }

    /** Apply a backup JSON, replacing the current state. Returns false if it isn't a valid backup.
     *  Validated BEFORE any write, so a garbage/foreign file can never wipe the user's data. */
    suspend fun importJson(text: String): Boolean = withContext(Dispatchers.IO) {
        val snap = BackupCodec.decode(text) ?: return@withContext false
        // Whole restore runs under the processing lock (no categorization can interleave) and rebuilds
        // the rollup afterwards; the log/folder replace is one Room transaction, so a mid-restore
        // failure rolls back rather than leaving the log half-wiped.
        processor.runRestore {
            db.withTransaction {
                // Replace the log + folders wholesale (ids preserved so anchors/relationships stay intact).
                entryDao.deleteAll()
                entryDao.insertAll(snap.entries)
                projectDao.deleteAll()
                projectDao.insertAll(snap.projects)
            }
            // Framework: an empty list resets to the shipped default (a valid backup always carries pillars).
            frameworkStore.save(snap.pillars)
            // Settings (never the Groq key, never Drive prefs).
            with(snap.settings) {
                settingsStore.setReminderEnabled(reminderEnabled)
                settingsStore.setReminderTime(reminderHour, reminderMinute)
                settingsStore.setLastCaptureMode(lastCaptureMode)
                settingsStore.setReviewYearStartMonth(reviewYearStartMonth)
                if (jobRole.isNotBlank()) settingsStore.setJobRole(jobRole)
                else if (rolePromptDismissed) settingsStore.dismissRolePrompt()
            }
            summaryStore.importRaw(snap.summariesRaw)
        }
        true
    }

    /** Write the current backup JSON to a user-picked document (SAF). The no-Drive fallback. */
    suspend fun exportToUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportJson().toByteArray(Charsets.UTF_8)) }
                ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    /** Restore from a user-picked backup document (SAF). */
    suspend fun importFromUri(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull() ?: return@withContext false
        importJson(text)
    }

    /** The human-readable appraisal document as clean plain text (for the Drive doc + device export). */
    suspend fun exportReadableDoc(): String = withContext(Dispatchers.IO) {
        val entries = entryDao.getAllOnce()
        val framework = frameworkStore.framework.first()
        val folders = projects.observeActive().first()
        exportDocument(buildHomeDoc(entries, framework, folders))
    }

    /** True if there is no logged data yet (drives the silent restore-on-reinstall check). */
    suspend fun isLocalEmpty(): Boolean = withContext(Dispatchers.IO) { entryDao.count() == 0 }

    /**
     * Emits whenever the **backed-up surface** changes (entries / folders / framework / the backed-up
     * settings). Deliberately excludes the Groq key and the Drive prefs (auto-backup flag, last-backup
     * time) so a backup writing `driveLastBackupAt` can't feed back and re-trigger itself.
     */
    fun changeSignal(): Flow<Int> = combine(
        entryDao.observeAll(),
        projects.observeActive(),
        frameworkStore.framework,
        settingsStore.settings,
    ) { entries, folders, fw, s ->
        listOf(
            entries, folders, fw.pillars,
            s.reminderEnabled, s.reminderHour, s.reminderMinute, s.lastCaptureMode,
            s.jobRole, s.rolePromptDismissed, s.reviewYearStartMonth,
        ).hashCode()
    }.distinctUntilChanged()

    /** A short human size of the current backup payload (for the "what gets backed up" row). */
    suspend fun backupSizeLabel(): String = withContext(Dispatchers.IO) {
        formatBytes(exportJson().toByteArray(Charsets.UTF_8).size.toLong())
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
    }
}
