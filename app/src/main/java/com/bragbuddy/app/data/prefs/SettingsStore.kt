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
import com.bragbuddy.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Which capture mode the sheet opens to (last used). */
enum class CaptureMode { SPEAK, TYPE, IMAGE }

/**
 * What a "generic" capture launch (the notification tap, the daily-nudge / catch-up buttons) opens to.
 * [ASK] shows the 3-choice chooser; the others go straight to that mode. The Home "+" FAB always shows
 * the radial regardless of this — this governs only the surfaces that can't host the radial animation.
 */
enum class DefaultCaptureMethod { ASK, SPEAK, TYPE, IMAGE }

/**
 * How the app resolves light vs. dark (Phase 2 · theme).
 *  - [SYSTEM]: follow the device's light/dark setting (the previous, only, behaviour).
 *  - [LIGHT] / [DARK]: force it, regardless of the system.
 *  - [AUTO]: switch on a device-local schedule — dark from [AppSettings.autoDarkHour]:[autoDarkMinute]
 *    to [AppSettings.autoLightHour]:[autoLightMinute] (wrapping midnight). Device-local, not backed up.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK, AUTO }

/** User settings for the daily reminder + capture preferences. Device-local. */
data class AppSettings(
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 18,      // 24h
    val reminderMinute: Int = 0,
    /** Which weekdays the daily reminder fires on. Device-local, deliberately NOT backed up — mirrors
     *  [weeklyRecapEnabled] (a per-device preference). Default = all seven days, preserving the
     *  pre-existing "every day" behaviour. **Empty = no day chosen = treated as reminder off** (never
     *  schedule a never-firing alarm). */
    val reminderDays: Set<DayOfWeek> = ALL_WEEK_DAYS,
    val lastCaptureMode: CaptureMode = CaptureMode.SPEAK,
    /** What the notification / daily-nudge open to (Voice by default, preserving today's behaviour).
     *  "Ask each time" ([DefaultCaptureMethod.ASK]) opens the 3-choice chooser instead. */
    val defaultCaptureMethod: DefaultCaptureMethod = DefaultCaptureMethod.SPEAK,
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
    /** The gentle weekly recap NOTIFICATION (M2 — replaced the in-app catch-up sheet). Opt-out;
     *  default on. (Persisted under the legacy "catchup_enabled" key so the old opt-out carries over.) */
    val weeklyRecapEnabled: Boolean = true,
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
    // ---- Phase C · onboarding + privacy ----
    /** Set true once the first-run onboarding wizard has been finished (or skipped through). Gates the
     *  [BragNavHost] start destination. Device-local; deliberately NOT backed up/restored — accepting
     *  the privacy terms is treated as per-install. */
    val onboardingComplete: Boolean = false,
    /** The privacy/terms version the user has accepted (0 = never). Re-prompted only when the shipped
     *  [com.bragbuddy.app.data.legal.PrivacyPolicy.VERSION] is bumped for a material change. Device-local. */
    val acceptedPrivacyVersion: Int = 0,
    /** Set true once the first-run notification-rationale popup (Phase 3) has been shown and acted upon
     *  — allowed, "maybe later", or auto-satisfied (below Android 13 / already granted). Gates that
     *  one-time popup AND the Home reliability card (the card stays quiet until the primer is handled,
     *  so the two never nag about notifications at once). Device-local; not backed up (per-install). */
    val notifPrimerHandled: Boolean = false,
    // ---- Phase 2 · theme (device-local; deliberately NOT backed up — a per-device visual preference) ----
    /** Light / dark selection. Default [ThemeMode.SYSTEM] preserves the pre-existing follow-the-system behaviour. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** [ThemeMode.AUTO] · when to switch TO dark (24h, device-local). Default 8:00 PM. */
    val autoDarkHour: Int = 20,
    val autoDarkMinute: Int = 0,
    /** [ThemeMode.AUTO] · when to switch TO light (24h, device-local). Default 7:00 AM. */
    val autoLightHour: Int = 7,
    val autoLightMinute: Int = 0,
) {
    /** Voice transcription is cloud Whisper (Groq) — the only engine. It runs when a BYOK key is set
     *  OR the managed proxy is configured (Phase M1); without either, voice prompts the user to add a
     *  key (on-device STT was removed — too inaccurate). */
    val cloudTranscription: Boolean get() = groqApiKey.isNotBlank() || managedAiAvailable

    /** AI categorization runs when a BYOK key is present OR the managed proxy is configured (Phase M1);
     *  otherwise entries wait in the Inbox. */
    val aiEnabled: Boolean get() = groqApiKey.isNotBlank() || managedAiAvailable
}

/** True once the owner has baked the managed-proxy URL (`PROXY_BASE_URL` secret → [BuildConfig]).
 *  Empty in local/debug builds and until the owner deploys → keyless installs behave exactly as before.
 *  Kept here (reading [BuildConfig] directly, not `data.ai`) so `prefs` needn't depend on `data.ai`.
 *  Normalised (trim + strip trailing slashes) identically to `AiEndpointConfig.proxyBaseUrl` so
 *  `aiEnabled` can never disagree with the actual route usability. */
private val managedAiAvailable: Boolean get() = BuildConfig.PROXY_BASE_URL.trim().trimEnd('/').isNotEmpty()

/** All seven days — the default reminder-days set (fire every day). */
val ALL_WEEK_DAYS: Set<DayOfWeek> = DayOfWeek.values().toSet()

/** 7-bit mask for the persisted [AppSettings.reminderDays]: bit (day.value − 1), Monday = bit 0 …
 *  Sunday = bit 6. Storing a single Int keeps DataStore simple; the Set is the in-memory shape. */
private const val ALL_DAYS_MASK = 0b111_1111  // 127 — every day (the default)

private fun daysToMask(days: Set<DayOfWeek>): Int =
    days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) }

private fun maskToDays(mask: Int): Set<DayOfWeek> =
    DayOfWeek.values().filterTo(LinkedHashSet()) { (mask and (1 shl (it.value - 1))) != 0 }

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
            // Absent key (existing installs / fresh) → all days, so behaviour is unchanged until the
            // user narrows it. An explicitly-emptied set persists as mask 0 → decodes back to empty.
            reminderDays = maskToDays(p[KEY_REMINDER_DAYS] ?: ALL_DAYS_MASK),
            lastCaptureMode = runCatching { CaptureMode.valueOf(p[KEY_LAST_MODE] ?: "SPEAK") }
                .getOrDefault(CaptureMode.SPEAK),
            defaultCaptureMethod = runCatching { DefaultCaptureMethod.valueOf(p[KEY_DEFAULT_CAPTURE] ?: "SPEAK") }
                .getOrDefault(DefaultCaptureMethod.SPEAK),
            groqApiKey = p[KEY_GROQ_KEY] ?: "",
            jobRole = p[KEY_JOB_ROLE] ?: "",
            rolePromptDismissed = p[KEY_ROLE_PROMPT_DISMISSED] ?: false,
            reviewYearStartMonth = (p[KEY_REVIEW_YEAR_START] ?: 1).coerceIn(1, 12),
            driveAutoBackup = p[KEY_DRIVE_AUTO_BACKUP] ?: true,
            driveLastBackupAt = p[KEY_DRIVE_LAST_BACKUP_AT] ?: 0L,
            weeklyRecapEnabled = p[KEY_WEEKLY_RECAP_ENABLED] ?: true,
            dailyNudgeDismissedDay = p[KEY_NUDGE_DISMISSED_DAY] ?: "",
            previewBannerDismissed = p[KEY_PREVIEW_DISMISSED] ?: false,
            oemAutostartDone = p[KEY_OEM_AUTOSTART_DONE] ?: false,
            reliabilityDismissedRisks = p[KEY_RELIABILITY_DISMISSED_RISKS] ?: "",
            onboardingComplete = p[KEY_ONBOARDING_COMPLETE] ?: false,
            acceptedPrivacyVersion = p[KEY_ACCEPTED_PRIVACY_VERSION] ?: 0,
            notifPrimerHandled = p[KEY_NOTIF_PRIMER_HANDLED] ?: false,
            themeMode = runCatching { ThemeMode.valueOf(p[KEY_THEME_MODE] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            autoDarkHour = (p[KEY_THEME_DARK_HOUR] ?: 20).coerceIn(0, 23),
            autoDarkMinute = (p[KEY_THEME_DARK_MINUTE] ?: 0).coerceIn(0, 59),
            autoLightHour = (p[KEY_THEME_LIGHT_HOUR] ?: 7).coerceIn(0, 23),
            autoLightMinute = (p[KEY_THEME_LIGHT_MINUTE] ?: 0).coerceIn(0, 59),
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

    /** Persist which weekdays the reminder fires on (an empty set is allowed → reminder paused). */
    suspend fun setReminderDays(days: Set<DayOfWeek>) =
        store.edit { it[KEY_REMINDER_DAYS] = daysToMask(days) }

    /** Flip one weekday on/off **atomically** — the read-modify-write happens inside a single DataStore
     *  edit (which serialises writers), so several quick chip taps can't lose an update by racing the
     *  flow round-trip that refreshes the UI state. */
    suspend fun toggleReminderDay(day: DayOfWeek) =
        store.edit { p ->
            val mask = p[KEY_REMINDER_DAYS] ?: ALL_DAYS_MASK
            p[KEY_REMINDER_DAYS] = mask xor (1 shl (day.value - 1))
        }

    suspend fun setLastCaptureMode(mode: CaptureMode) =
        store.edit { it[KEY_LAST_MODE] = mode.name }

    suspend fun setDefaultCaptureMethod(method: DefaultCaptureMethod) =
        store.edit { it[KEY_DEFAULT_CAPTURE] = method.name }

    suspend fun setGroqApiKey(key: String) =
        store.edit { it[KEY_GROQ_KEY] = key.trim() }

    /**
     * Phase M1 · the per-install anonymous token for the managed proxy — a random UUID minted once and
     * persisted, used only to key per-device quotas at the relay (never tied to identity). Deliberately
     * NOT part of [AppSettings] / [com.bragbuddy.app.data.backup.BackupSettings], so it is never backed
     * up or restored — a restored device mints its own. Read fast-path first; only writes (under the
     * atomic DataStore edit) if still unset, so concurrent callers converge on one value.
     */
    suspend fun installId(): String {
        store.data.map { it[KEY_INSTALL_ID] }.first()?.takeIf { it.isNotBlank() }?.let { return it }
        var result = ""
        store.edit { prefs ->
            val current = prefs[KEY_INSTALL_ID]
            result = if (current.isNullOrBlank()) {
                java.util.UUID.randomUUID().toString().also { prefs[KEY_INSTALL_ID] = it }
            } else {
                current
            }
        }
        return result
    }

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

    suspend fun setWeeklyRecapEnabled(enabled: Boolean) =
        store.edit { it[KEY_WEEKLY_RECAP_ENABLED] = enabled }

    suspend fun setDailyNudgeDismissedDay(dayKey: String) =
        store.edit { it[KEY_NUDGE_DISMISSED_DAY] = dayKey }

    suspend fun setPreviewBannerDismissed(dismissed: Boolean) =
        store.edit { it[KEY_PREVIEW_DISMISSED] = dismissed }

    suspend fun setOemAutostartDone(done: Boolean) =
        store.edit { it[KEY_OEM_AUTOSTART_DONE] = done }

    suspend fun setReliabilityDismissedRisks(signature: String) =
        store.edit { it[KEY_RELIABILITY_DISMISSED_RISKS] = signature }

    suspend fun setAcceptedPrivacyVersion(version: Int) =
        store.edit { it[KEY_ACCEPTED_PRIVACY_VERSION] = version }

    suspend fun setNotifPrimerHandled(handled: Boolean) =
        store.edit { it[KEY_NOTIF_PRIMER_HANDLED] = handled }

    /** The primer was declined: mark it handled AND acknowledge the current reminder-risk signature in
     *  ONE atomic write, so the Home reliability-card gate can't observe an intermediate state (handled
     *  but risks not yet acknowledged) and flash the card for a frame. */
    suspend fun setNotifPrimerDeclined(acknowledgedRisks: String) =
        store.edit {
            it[KEY_NOTIF_PRIMER_HANDLED] = true
            it[KEY_RELIABILITY_DISMISSED_RISKS] = acknowledgedRisks
        }

    suspend fun setThemeMode(mode: ThemeMode) =
        store.edit { it[KEY_THEME_MODE] = mode.name }

    suspend fun setAutoDarkTime(hour: Int, minute: Int) {
        store.edit {
            it[KEY_THEME_DARK_HOUR] = hour.coerceIn(0, 23)
            it[KEY_THEME_DARK_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    suspend fun setAutoLightTime(hour: Int, minute: Int) {
        store.edit {
            it[KEY_THEME_LIGHT_HOUR] = hour.coerceIn(0, 23)
            it[KEY_THEME_LIGHT_MINUTE] = minute.coerceIn(0, 59)
        }
    }

    /** Finish onboarding in ONE atomic write: mark it complete AND stamp the accepted privacy version
     *  together, so a caller can await this single edit before navigating away (the nav pop cancels the
     *  caller's scope — two sequential writes would risk dropping the second). */
    suspend fun completeOnboarding(privacyVersion: Int) =
        store.edit {
            it[KEY_ONBOARDING_COMPLETE] = true
            it[KEY_ACCEPTED_PRIVACY_VERSION] = privacyVersion
        }

    private companion object {
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val KEY_REMINDER_DAYS = intPreferencesKey("reminder_days")
        val KEY_LAST_MODE = stringPreferencesKey("last_capture_mode")
        val KEY_DEFAULT_CAPTURE = stringPreferencesKey("default_capture_method")
        val KEY_GROQ_KEY = stringPreferencesKey("groq_api_key")
        val KEY_INSTALL_ID = stringPreferencesKey("install_id")
        val KEY_JOB_ROLE = stringPreferencesKey("job_role")
        val KEY_ROLE_PROMPT_DISMISSED = booleanPreferencesKey("role_prompt_dismissed")
        val KEY_REVIEW_YEAR_START = intPreferencesKey("review_year_start_month")
        val KEY_DRIVE_AUTO_BACKUP = booleanPreferencesKey("drive_auto_backup")
        val KEY_DRIVE_LAST_BACKUP_AT = longPreferencesKey("drive_last_backup_at")
        // Persisted under the legacy "catchup_enabled" key so an existing opt-out carries over to the recap.
        val KEY_WEEKLY_RECAP_ENABLED = booleanPreferencesKey("catchup_enabled")
        val KEY_NUDGE_DISMISSED_DAY = stringPreferencesKey("daily_nudge_dismissed_day")
        val KEY_PREVIEW_DISMISSED = booleanPreferencesKey("preview_banner_dismissed")
        val KEY_OEM_AUTOSTART_DONE = booleanPreferencesKey("oem_autostart_done")
        val KEY_RELIABILITY_DISMISSED_RISKS = stringPreferencesKey("reliability_dismissed_risks")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_ACCEPTED_PRIVACY_VERSION = intPreferencesKey("accepted_privacy_version")
        val KEY_NOTIF_PRIMER_HANDLED = booleanPreferencesKey("notif_primer_handled")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_THEME_DARK_HOUR = intPreferencesKey("theme_auto_dark_hour")
        val KEY_THEME_DARK_MINUTE = intPreferencesKey("theme_auto_dark_minute")
        val KEY_THEME_LIGHT_HOUR = intPreferencesKey("theme_auto_light_hour")
        val KEY_THEME_LIGHT_MINUTE = intPreferencesKey("theme_auto_light_minute")
    }
}
