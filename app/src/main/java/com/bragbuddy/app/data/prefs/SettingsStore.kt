package com.bragbuddy.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore("settings")

/** Which capture mode the sheet opens to (last used). */
enum class CaptureMode { SPEAK, TYPE }

/** How voice is transcribed. ON_DEVICE = free/offline Android STT; CLOUD = Groq Whisper (needs a key). */
enum class TranscriptionEngine { ON_DEVICE, CLOUD }

/** User settings for the daily reminder + capture preferences. Device-local. */
data class AppSettings(
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 18,      // 24h
    val reminderMinute: Int = 0,
    val lastCaptureMode: CaptureMode = CaptureMode.SPEAK,
    val transcriptionEngine: TranscriptionEngine = TranscriptionEngine.ON_DEVICE,
    /** Groq API key (gsk_…). Powers BOTH cloud Whisper transcription and the AI brain (categorizer +
     *  framework refine). Stored on-device only — never committed or shipped. */
    val groqApiKey: String = "",
) {
    /** Cloud transcription only actually runs when the engine is CLOUD *and* a key is set. */
    val cloudTranscription: Boolean get() = transcriptionEngine == TranscriptionEngine.CLOUD && groqApiKey.isNotBlank()

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
            transcriptionEngine = runCatching { TranscriptionEngine.valueOf(p[KEY_STT_ENGINE] ?: "ON_DEVICE") }
                .getOrDefault(TranscriptionEngine.ON_DEVICE),
            groqApiKey = p[KEY_GROQ_KEY] ?: "",
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

    suspend fun setTranscriptionEngine(engine: TranscriptionEngine) =
        store.edit { it[KEY_STT_ENGINE] = engine.name }

    suspend fun setGroqApiKey(key: String) =
        store.edit { it[KEY_GROQ_KEY] = key.trim() }

    private companion object {
        val KEY_REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val KEY_LAST_MODE = stringPreferencesKey("last_capture_mode")
        val KEY_STT_ENGINE = stringPreferencesKey("transcription_engine")
        val KEY_GROQ_KEY = stringPreferencesKey("groq_api_key")
    }
}
