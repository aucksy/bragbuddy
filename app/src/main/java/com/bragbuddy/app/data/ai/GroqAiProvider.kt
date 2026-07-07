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
 * The AI brain: **Groq** (OpenAI-compatible LLM inference), reached through the swappable
 * [AiProvider] seam. Reuses the single **Groq key** ([SettingsStore.groqApiKey]) that also powers
 * cloud Whisper transcription — one key for both, read fresh on each call and stored only on-device
 * (never committed, never in the APK).
 *
 * TWO MODELS BY TASK + FALLBACK (Build Brief § model routing): the categorizer runs the fast
 * [AiConfig.categorizerModel]; on a rate-limit / transient failure — or if the primary returns
 * unparseable JSON — it retries with [AiConfig.categorizerFallback]. Slugs live in [AiConfig] so a
 * retired model is a one-value change.
 *
 * FAIL SAFE (firm invariant): any network error, non-2xx, or unparseable output surfaces as a
 * failed [Result]. The caller (EntryProcessor) then keeps the raw transcript and routes the entry
 * to the Inbox — nothing the user said is ever lost.
 */
@Singleton
class GroqAiProvider @Inject constructor(
    private val settings: SettingsStore,
    private val client: OkHttpClient,
) : AiProvider {

    override val label: String = AiConfig.categorizerLabel()

    override suspend fun categorize(request: CategorizeRequest): Result<CategorizeResult> {
        val system = AiPrompts.categorizer(
            request.today, request.framework, request.projects, request.role, request.projectAnchor,
            request.combineSingle,
        )
        val user = request.transcript.trim()
        if (user.isEmpty()) return Result.success(CategorizeResult(emptyList()))
        return completeAndParse(
            models = listOf(AiConfig.categorizerModel, AiConfig.categorizerFallback),
            system = system,
            user = user,
        ) { AiJson.parse(it, CategorizeResult.serializer()) }
    }

    override suspend fun refineFramework(request: FrameworkRefineRequest): Result<FrameworkRefineResult> {
        val system = AiPrompts.framework(request.currentFramework, request.description, request.role)
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
            request.period, request.lengthCap, request.framework, request.pinned, request.rollup, request.role,
        )
        return completeAndParse(
            models = listOf(AiConfig.summaryModel, AiConfig.summaryFallback),
            system = system,
            user = "Generate the summary now.",
        ) { AiJson.parse(it, SummaryResult.serializer()) }
    }

    override suspend fun extractFromImage(request: ImageExtractRequest): Result<ImageExtractResult> {
        if (request.imageDataUrl.isBlank()) return Result.failure(IllegalStateException("No image"))
        val prompt = AiPrompts.imageExtract(request.role)
        // Try the production vision model, then the fallback slug (in case the primary was retired) —
        // mirrors the categorizer's model routing. Any error/unparseable output drops to the next;
        // exhausting both fails safe so the capture stays on the sheet to retry / type.
        var last: Throwable = IllegalStateException("No vision model responded")
        for (model in listOf(AiConfig.visionModel, AiConfig.visionFallback)) {
            val parsed = callVision(model, prompt, request.imageDataUrl)
                .mapCatching { AiJson.parse(it, ImageExtractResult.serializer()) }
            if (parsed.isSuccess) return parsed
            parsed.exceptionOrNull()?.let { last = it }
        }
        return Result.failure(last)
    }

    override suspend fun readDocumentText(request: ImageExtractRequest): Result<ImageExtractResult> {
        if (request.imageDataUrl.isBlank()) return Result.failure(IllegalStateException("No image"))
        // Same vision pipeline + model routing as extractFromImage, but the doc-scan prompt — the
        // scanned image is reference material (a job description / review criteria), not an achievement.
        val prompt = AiPrompts.documentScan(request.role)
        var last: Throwable = IllegalStateException("No vision model responded")
        for (model in listOf(AiConfig.visionModel, AiConfig.visionFallback)) {
            val parsed = callVision(model, prompt, request.imageDataUrl)
                .mapCatching { AiJson.parse(it, ImageExtractResult.serializer()) }
            if (parsed.isSuccess) return parsed
            parsed.exceptionOrNull()?.let { last = it }
        }
        return Result.failure(last)
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
        val key = settings.settings.first().groqApiKey.trim()
        if (key.isBlank()) return Result.failure(IllegalStateException("No Groq key set"))

        var last: Throwable = IllegalStateException("No model responded")
        for (model in models) {
            val parsed = callChat(model, system, user, key).mapCatching(parse)
            if (parsed.isSuccess) return parsed
            parsed.exceptionOrNull()?.let { last = it }
        }
        return Result.failure(last)
    }

    /** One Groq chat completion → the assistant message content string. */
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
                // Groq honours JSON mode; our prompts all contain the word "JSON" (its precondition).
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
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Groq ${resp.code}${if (raw.isNotBlank()) ": ${raw.take(160)}" else ""}")
                }
                val content = AiJson.json.decodeFromString(ChatResponse.serializer(), raw)
                    .choices.firstOrNull()?.message?.content
                content?.takeIf { it.isNotBlank() } ?: error("Empty completion")
            }
        }
    }

    /**
     * One Groq **multimodal** chat completion: a single user turn carrying the instruction text and
     * one image (a base64 `data:` URL), per Groq's vision API. Same fail-safe envelope as [callChat]
     * — everything (incl. request build) is inside `runCatching`, so nothing throws to the caller.
     */
    private suspend fun callVision(
        model: String,
        prompt: String,
        imageDataUrl: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = settings.settings.first().groqApiKey.trim()
        if (key.isBlank()) return@withContext Result.failure(IllegalStateException("No Groq key set"))
        runCatching {
            val payload = buildJsonObject {
                put("model", model)
                put("temperature", 0.2)
                // JSON mode — the prompt contains the word "JSON" (Groq's precondition).
                putJsonObject("response_format") { put("type", "json_object") }
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", prompt) }
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") { put("url", imageDataUrl) }
                            }
                        }
                    }
                }
            }
            val body = AiJson.json.encodeToString(JsonObject.serializer(), payload)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(AiConfig.BASE_URL)
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()

            client.newCall(request).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    error("Groq ${resp.code}${if (raw.isNotBlank()) ": ${raw.take(160)}" else ""}")
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
