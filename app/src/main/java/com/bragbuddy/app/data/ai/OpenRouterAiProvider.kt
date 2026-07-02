package com.bragbuddy.app.data.ai

import com.bragbuddy.app.data.prefs.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The real AI brain: **OpenRouter** (OpenAI-compatible), reached through the swappable [AiProvider]
 * seam. The key is read fresh from [SettingsStore] on each call and lives only on-device — never
 * committed, never in the APK (same rule as the Groq transcription key).
 *
 * TWO MODELS BY TASK + FALLBACK (Build Brief § model routing): the categorizer runs the free
 * [AiConfig.categorizerModel]; on a rate-limit / transient failure — or if the primary returns
 * unparseable JSON — it retries with [AiConfig.categorizerFallback]. Slugs live in [AiConfig] so a
 * vanished free model is a one-value change.
 *
 * FAIL SAFE (firm invariant): any network error, non-2xx, or unparseable output surfaces as a
 * failed [Result]. The caller (EntryProcessor) then keeps the raw transcript and routes the entry
 * to the Inbox — nothing the user said is ever lost.
 */
@Singleton
class OpenRouterAiProvider @Inject constructor(
    private val settings: SettingsStore,
    private val client: OkHttpClient,
) : AiProvider {

    override val label: String = AiConfig.categorizerLabel()

    override suspend fun categorize(request: CategorizeRequest): Result<CategorizeResult> {
        val system = AiPrompts.categorizer(request.today, request.framework, request.projects)
        val user = request.transcript.trim()
        if (user.isEmpty()) return Result.success(CategorizeResult(emptyList()))
        return completeAndParse(
            models = listOf(AiConfig.categorizerModel, AiConfig.categorizerFallback),
            system = system,
            user = user,
        ) { AiJson.parse(it, CategorizeResult.serializer()) }
    }

    override suspend fun refineFramework(request: FrameworkRefineRequest): Result<FrameworkRefineResult> {
        val system = AiPrompts.framework(request.currentFramework, request.description)
        val user = request.description.trim()
        if (user.isEmpty()) return Result.failure(IllegalStateException("Nothing to build from"))
        return completeAndParse(
            models = listOf(AiConfig.frameworkModel, AiConfig.frameworkFallback),
            system = system,
            user = user,
        ) { AiJson.parse(it, FrameworkRefineResult.serializer()) }
    }

    override suspend fun generateSummary(request: SummaryRequest): Result<SummaryResult> {
        val system = AiPrompts.summary(
            request.period, request.lengthCap, request.framework, request.pinned, request.rollup,
        )
        return completeAndParse(
            models = listOf(AiConfig.summaryModel, AiConfig.summaryFallback),
            system = system,
            user = "Generate the summary now.",
        ) { AiJson.parse(it, SummaryResult.serializer()) }
    }

    // ---------------- HTTP + parsing plumbing ----------------

    /**
     * Try each model in order. A model that errors (network / non-2xx) OR returns output the
     * [parse] lambda can't decode drops through to the next model. Returns the first parsed result,
     * or a failure carrying the last error if every model is exhausted.
     */
    private suspend fun <T> completeAndParse(
        models: List<String>,
        system: String,
        user: String,
        parse: (String) -> T,
    ): Result<T> {
        val key = settings.settings.first().openRouterApiKey.trim()
        if (key.isBlank()) return Result.failure(IllegalStateException("No OpenRouter key set"))

        var last: Throwable = IllegalStateException("No model responded")
        for (model in models) {
            val parsed = callChat(model, system, user, key).mapCatching(parse)
            if (parsed.isSuccess) return parsed
            parsed.exceptionOrNull()?.let { last = it }
        }
        return Result.failure(last)
    }

    /** One OpenRouter chat completion → the assistant message content string. */
    private suspend fun callChat(
        model: String,
        system: String,
        user: String,
        key: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        // Everything is inside runCatching — including request/header building — so even a malformed
        // key character (OkHttp validates header values) becomes a failed Result, never a thrown crash.
        runCatching {
            val payload = buildJsonObject {
                put("model", model)
                put("temperature", 0.2)
                putJsonObject("response_format") { put("type", "json_object") }
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("content", system) }
                    addJsonObject { put("role", "user"); put("content", user) }
                }
            }
            val body = AiJson.json.encodeToString(JsonObject.serializer(), payload)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(AiConfig.BASE_URL)
                .header("Authorization", "Bearer $key")
                .header("HTTP-Referer", AiConfig.REFERER)
                .header("X-Title", AiConfig.TITLE)
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("OpenRouter ${resp.code}${if (raw.isNotBlank()) ": ${raw.take(160)}" else ""}")
                }
                val content = AiJson.json.decodeFromString(ChatResponse.serializer(), raw)
                    .choices.firstOrNull()?.message?.content
                content?.takeIf { it.isNotBlank() } ?: error("Empty completion")
            }
        }
    }

    @Serializable private data class ChatResponse(val choices: List<Choice> = emptyList())
    @Serializable private data class Choice(val message: Message? = null)
    @Serializable private data class Message(val content: String? = null)
}
