package com.bragbuddy.app.data.speech

import com.bragbuddy.app.data.local.ProjectDao
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

/** A non-2xx transcription response, carrying the HTTP status so callers can tell a permanent
 *  client-side failure (401 bad key, 413 clip too big, 400 unreadable) from a transient one
 *  (408/429/5xx) — the offline queue keeps retrying only the latter. */
class TranscriptionHttpException(val code: Int, message: String) : Exception(message) {
    /** True when retrying the same clip can never succeed (auth aside, see [isAuth]). */
    val isPermanent: Boolean get() = code in 400..499 && code != 408 && code != 429

    /** Auth failures (bad/revoked key) are "permanent" for this key but fixable in Settings. */
    val isAuth: Boolean get() = code == 401 || code == 403
}

/**
 * Cloud transcription via **Groq** (free-tier Whisper-large-v3), OpenAI-compatible. The API key is
 * read fresh from [SettingsStore] on each call and lives only on-device — never committed/shipped.
 * Switching to OpenAI later is just the base URL + key.
 */
@Singleton
class GroqTranscriber @Inject constructor(
    private val settings: SettingsStore,
    private val projectDao: ProjectDao,
    private val client: OkHttpClient,
) : Transcriber {

    override val label: String = "Groq · Whisper-large-v3"

    override suspend fun transcribe(audio: File): Result<String> = withContext(Dispatchers.IO) {
        val settingsNow = settings.settings.first()
        val key = settingsNow.groqApiKey.trim()
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("No transcription key set"))
        if (!audio.exists() || audio.length() == 0L) return@withContext Result.failure(IllegalStateException("No audio recorded"))

        // AI-1 · Whisper vocabulary: a short priming prompt of the user's role + project names so Whisper
        // spells "Raven Migration", "CommXHub" etc. correctly. Resilient — a DB read failure must never
        // block transcription, so it's runCatching to an empty prompt.
        val vocabPrompt = runCatching { buildVocabPrompt(settingsNow.jobRole) }.getOrDefault("")

        runCatching {
            // Build the request INSIDE runCatching: a malformed key character (a stray control/
            // non-ASCII char that survived trim) makes OkHttp's .header() throw — this must become a
            // failed Result, never a thrown crash on the live capture path (mirrors GroqAiProvider).
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", MODEL)
                .addFormDataPart("response_format", "text")
                .apply { if (vocabPrompt.isNotBlank()) addFormDataPart("prompt", vocabPrompt) }
                .addFormDataPart("file", audio.name, audio.asRequestBody("audio/m4a".toMediaType()))
                .build()
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty().trim()
                if (!resp.isSuccessful) {
                    throw TranscriptionHttpException(
                        resp.code,
                        "Transcription failed (${resp.code})${if (text.isNotBlank()) ": ${text.take(140)}" else ""}",
                    )
                }
                text
            }
        }
    }

    /**
     * "Work log for a {role}. Projects: {names}." — capped to ~200 tokens (≈800 chars) so a long
     * project list can never crowd out the audio; the role is always kept and only the project list is
     * truncated. Empty when there's nothing useful to prime with.
     */
    private suspend fun buildVocabPrompt(role: String): String {
        val roleClause = role.trim().takeIf { it.isNotBlank() }?.let { "Work log for a $it." } ?: "Work log."
        val names = projectDao.observeActive().first().map { it.name.trim() }.filter { it.isNotBlank() }
        if (names.isEmpty()) return roleClause
        val budget = MAX_PROMPT_CHARS - roleClause.length - " Projects: .".length
        val kept = mutableListOf<String>()
        var used = 0
        for (n in names) {
            val add = (if (kept.isEmpty()) n.length else n.length + 2) // ", "
            if (used + add > budget) break
            kept += n
            used += add
        }
        if (kept.isEmpty()) return roleClause
        return "$roleClause Projects: ${kept.joinToString(", ")}."
    }

    private companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val MODEL = "whisper-large-v3"

        /** ~200 tokens for the Whisper priming prompt (≈4 chars/token). */
        const val MAX_PROMPT_CHARS = 800
    }
}
