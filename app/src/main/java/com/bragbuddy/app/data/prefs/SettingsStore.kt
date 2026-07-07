package com.bragbuddy.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Which capture mode the sheet opens to (last used). */
enum class CaptureMode { SPEAK, TYPE, IMAGE }

/** User settings for the daily reminder + capture preferences. Device-local. */
data class AppSettings(
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 18,      // 24h
    val reminderMinute: Int = 0,
    val lastCaptureMode: CaptureMode = CaptureMode.SPEAK,
    /** Groq API key (gsk_…). Powers BOTH cloud Whisper transcription and the AI brain (categorizer +
     *  framework refine). Stored on-device only — never committed or shipped. */
    val groqApiKey: String = "",
    /** The user's job role (e.g. "Product Owner"). Context for the AI — sharpens core-duty vs.
     *  beyond-scope/leadership judgement. Free text, device-local. NEVER the company name. */
    val jobRole: String = "",
    /** Set true once the first-run "what's your role?" prompt has been answered or dismissed. */
    val rolePromptDismissed: Boolean = false,
    /** The month (1–12) the user's appraisal/review year starts on. Windows the summary period
     *  (mid-year = first 6 months, year-end = full year). Default January. */
    val reviewYearStartMonth: Int = 1,
    /** Auto-push a fresh Drive backup when data changes (once connected). Default on — the backup is
     *  text-only and small (Build Brief: transcriptions-only is the always-on default). */
    val driveAutoBackup: Boolean = true,
    /** Epoch millis of the last successful Drive backup (0 = never). Drives the health card. */
    val driveLastBackupAt: Long = 0L,
    // ---- Phase 7 · retention + reliability ----
    /** The gentle weekly catch-up sheet (Design §7). Opt-out; default on. */
    val catchupEnabled: Boolean = true,
    /** ISO-week key (e.g. "2026-W27") the catch-up last showed for — max once per week. */
    val catchupLastShownWeek: String = "",
    /** Day key (e.g. "2026-07-04") the on-open daily nudge was dismissed for (one day only). */
    val dailyNudgeDismissedDay: String = "",
    /** The week-1 early-preview summary banner was dismissed (it also hides forever after the
     *  first summary is generated). */
    val previewBannerDismissed: Boolean = false,
    /** User confirmed they've allowed OEM auto-start (not detectable programmatically). */
    val oemAutostartDone: Boolean = false,
    /** The risk signature (see ReminderHealth.riskSignature) the at-risk Home card was dismissed
     *  for. "" = never dismissed. A DIFFERENT risk appearing later resurfaces the card. */
    val reliabilityDismissedRisks: String = "",
) {
    /** Voice transcription is cloud Whisper (Groq) — the only engine. It runs when a key is set;
     *  without a key, voice prompts the user to add one (on-device STT was removed — too inaccurate). */
    val cloudTranscription: Boolean get() = groqApiKey.isNotBlank()

    /** AI categorization only runs when the Groq key is present; otherwise entries wait in the Inbox. */
    val aiEnabled: Boolean get() = groqApiKey.isNotBlank()
}

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store get() = context.settingsDataStore

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            reminderEnabled = p[KEY_REMINDER_ENABLED] ?: true,
            reminderHour = p[KEY_REMINDER_HOUR] ?: 18,
            reminderMinute = p[KEY_REMINDER_MINUTE] ?: 0,
            lastCaptureMode = runCatching { CaptureMode.valueOf(p[KEY_LAST_MODE] ?: "SPEAK") }
                .getOrDefault(CaptureMode.SPEAK),
            groqApiKey = p[KEY_GROQ_KEY] ?: "",
            jobRole = p[KEY_JOB_ROLE] ?: "",
            rolePromptDismissed = p[KEY_ROLE_PROMPT_DISMISSED] ?: false,
            reviewYearStartMonth = (p[KEY_REVIEW_YEAR_START] ?: 1).coerceIn(1, 12),
            driveAutoBackup = p[KEY_DRIVE_AUTO_BACKUP] ?: true,
            driveLastBackupAt = p[KEY_DRIVE_LAST_BACKUP_AT] ?: 0L,
            catchupEnabled = p[KEY_CATCHUP_ENABLED] ?: true,
            catchupLastShownWeek = p[KEY_CATCHUP_LAST_WEEK] ?: "",
            dailyNudgeDismissedDay = p[KEY_NUDGE_DISMISSED_DAY] ?: "",
            previewBannerDismissed = p[KEY_PREVIEW_DISMISSED] ?: false,
            oemAutostartDone = p[KEY_OEM_AUTOSTART_DONE] ?: false,
            reliabilityDismissedRisks = p[KEY_RELIABILITY_DISMISSED_RISKS] ?: "",
        )
    }

    suspend fun setReminderEnabled(enabled: Boolean) =
        store.edit { it[KEY_REMINDER_ENABLED] = enabled }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        store.edit {
            it[KEY_REMINDER_HOUR] = hour.coerceIn(0, 23)
            it[KEY_REMINDER_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setLastCaptureMode(mode: CaptureMode) =
        store.edit { it[KEY_LAST_MODE] = mode.name }

    suspend fun setGroqApiKey(key: String) =
        store.edit { it[KEY_GROQ_KEY] = key.trim() }

    /** Setting the role also marks the first-run prompt handled. */
    suspend fun setJobRole(role: String) =
        store.edit {
            it[KEY_JOB_ROLE] = role.trim()
            it[KEY_ROLE_PROMPT_DISMISSED] = true
        }

    suspend fun dismissRolePrompt() =
        store.edit { it[KEY_ROLE_PROMPT_DISMISSED] = true }

    suspend fun setReviewYearStartMonth(month: Int) =
        store.edit { it[KEY_REVIEW_YEAR_START] = month.coerceIn(1, 12) }

    suspend fun setDriveAutoBackup(enabled: Boolean) =
        store.edit { it[KEY_DRIVE_AUTO_BACKUP] = enabled }

    suspend fun setDriveLastBackupAt(millis: Long) =
        store.edit { it[KEY_DRIVE_LAST_BACKUP_AT] = millis }

    suspend fun setCatchupEnabled(enabled: Boolean) =
        store.edit { it[KEY_CATCHUP_ENABLED] = enabled }

    suspend fun setCatchupLastShownWeek(weekKey: String) =
        store.edit { it[KEY_CATCHUP_LAST_WEEK] = weekKey }

    suspend fun setDailyNudgeDismissedDay(dayKey: String) =
        store.edit { it[KEY_NUDGE_DISMISSED_DAY] = dayKey }

    suspend fun setPreviewBannerDismissed(dismissed: Boolean) =
        store.edit { it[KEY_PREVIEW_DISMISSED] = dismissed }

    suspend fun setOemAutostartDone(done: Boolean) =
        store.edit { it[KEY_OEM_AUTOSTART_DONE] = done }

    suspend fun setReliabilityDismissedRisks(signature: String) =
        store.edit { it[KEY_RELIABILITY_DISMISSED_RISKS] = signature }

    private companion object {
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val KEY_LAST_MODE = stringPreferencesKey("last_capture_mode")
        val KEY_GROQ_KEY = stringPreferencesKey("groq_api_key")
        val KEY_JOB_ROLE = stringPreferencesKey("job_role")
        val KEY_ROLE_PROMPT_DISMISSED = booleanPreferencesKey("role_prompt_dismissed")
        val KEY_REVIEW_YEAR_START = intPreferencesKey("review_year_start_month")
        val KEY_DRIVE_AUTO_BACKUP = booleanPreferencesKey("drive_auto_backup")
        val KEY_DRIVE_LAST_BACKUP_AT = longPreferencesKey("drive_last_backup_at")
        val KEY_CATCHUP_ENABLED = booleanPreferencesKey("catchup_enabled")
        val KEY_CATCHUP_LAST_WEEK = stringPreferencesKey("catchup_last_shown_week")
        val KEY_NUDGE_DISMISSED_DAY = stringPreferencesKey("daily_nudge_dismissed_day")
        val KEY_PREVIEW_DISMISSED = booleanPreferencesKey("preview_banner_dismissed")
        val KEY_OEM_AUTOSTART_DONE = booleanPreferencesKey("oem_autostart_done")
        val KEY_RELIABILITY_DISMISSED_RISKS = stringPreferencesKey("reliability_dismissed_risks")
    }
}
