package com.bragbuddy.app.data.speech

import com.bragbuddy.app.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Turns a recorded audio file into text. Swappable — see [GroqTranscriber] (cloud Whisper). */
interface Transcriber {
    val label: String
    suspend fun transcribe(audio: File): Result<String>
}

/**
 * Cloud transcription via **Groq** (free-tier Whisper-large-v3), OpenAI-compatible. The API key is
 * read fresh from [SettingsStore] on each call and lives only on-device — never committed/shipped.
 * Switching to OpenAI later is just the base URL + key.
 */
@Singleton
class GroqTranscriber @Inject constructor(
    private val settings: SettingsStore,
    private val client: OkHttpClient,
) : Transcriber {

    override val label: String = "Groq · Whisper-large-v3"

    override suspend fun transcribe(audio: File): Result<String> = withContext(Dispatchers.IO) {
        val key = settings.settings.first().groqApiKey.trim()
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("No transcription key set"))
        if (!audio.exists() || audio.length() == 0L) return@withContext Result.failure(IllegalStateException("No audio recorded"))

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", MODEL)
            .addFormDataPart("response_format", "text")
            .addFormDataPart("file", audio.name, audio.asRequestBody("audio/m4a".toMediaType()))
            .build()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()

        runCatching {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty().trim()
                if (!resp.isSuccessful) {
                    error("Transcription failed (${resp.code})${if (text.isNotBlank()) ": ${text.take(140)}" else ""}")
                }
                text
            }
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val MODEL = "whisper-large-v3"
    }
}
