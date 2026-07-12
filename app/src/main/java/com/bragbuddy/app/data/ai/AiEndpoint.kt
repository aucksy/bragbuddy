package com.bragbuddy.app.data.ai

import com.bragbuddy.app.BuildConfig
import com.bragbuddy.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase M1 · the injectable transport seam. "Which URL + which auth" for every LLM / Whisper call is
 * resolved here instead of being hardcoded, so the app can reach Groq in two ways with no change at
 * the call sites:
 *  - **BYOK / direct-Groq** — the user pasted their own Groq key (Settings → AI engine). Requests go
 *    straight to `api.groq.com` with `Authorization: Bearer <their key>`. This is the test-mode setup
 *    and the power-user escape hatch; it is always honoured when a key is present.
 *  - **Managed proxy** — no key. Requests go to BragBuddy's own relay ([AiEndpointConfig.proxyBaseUrl],
 *    baked in at build time from the `PROXY_BASE_URL` secret) which forwards them to Groq using the
 *    server-side key the user never sees. Auth is a per-install anonymous token + the baked app-gate
 *    secret; the relay stores nothing and logs no request bodies (the privacy claim depends on it).
 *
 * **Fail-safe / zero-regression:** until the owner deploys the relay and adds the build secrets,
 * [AiEndpointConfig.proxyConfigured] is false, so a keyless install resolves to [AiRoute.Mode.UNCONFIGURED]
 * — exactly today's "add a key / type instead" behaviour. The moment the proxy URL is baked, keyless
 * installs light up on the managed path with nothing else changed.
 */
data class AiRoute(
    val mode: Mode,
    /** OpenAI-compatible base (…/v1), no trailing slash. "" only when [Mode.UNCONFIGURED]. */
    val baseUrl: String,
    /** Headers to attach to the request. Direct: `Authorization`. Proxy: install token + app-gate key. */
    val authHeaders: Map<String, String>,
) {
    enum class Mode { DIRECT_GROQ, PROXY, UNCONFIGURED }

    /** False when neither a BYOK key nor the managed proxy is available — the caller fails safe. */
    val usable: Boolean get() = mode != Mode.UNCONFIGURED

    fun chatUrl(): String = "$baseUrl/chat/completions"
    fun transcriptionUrl(): String = "$baseUrl/audio/transcriptions"
}

/**
 * Pure routing logic (no Android / DataStore deps → fully unit-tested in `AiEndpointResolverTest`).
 * Precedence is deliberate: **a BYOK key always wins** (direct-Groq), then the managed proxy, then
 * nothing. Header names are shared with the Cloudflare Worker in `proxy/` — keep them in step.
 */
object AiEndpointResolver {
    /** The direct-Groq OpenAI-compatible base. `chatUrl()` off this == `AiConfig.BASE_URL` verbatim. */
    const val GROQ_BASE = "https://api.groq.com/openai/v1"

    /** Per-install anonymous quota key (a random UUID; never tied to identity). */
    const val HEADER_INSTALL_ID = "X-Install-Id"

    /** The baked app-gate secret — keeps random internet traffic off the owner's Groq key until
     *  Play Integrity attestation lands in M3. Extractable from the APK (soft gate by design). */
    const val HEADER_APP_KEY = "X-App-Key"

    fun resolve(groqApiKey: String, proxyBaseUrl: String, appSecret: String, installId: String): AiRoute {
        val key = groqApiKey.trim()
        if (key.isNotEmpty()) {
            return AiRoute(AiRoute.Mode.DIRECT_GROQ, GROQ_BASE, mapOf("Authorization" to "Bearer $key"))
        }
        val base = proxyBaseUrl.trim().trimEnd('/')
        if (base.isNotEmpty()) {
            val headers = buildMap {
                if (installId.isNotBlank()) put(HEADER_INSTALL_ID, installId)
                if (appSecret.trim().isNotEmpty()) put(HEADER_APP_KEY, appSecret.trim())
            }
            return AiRoute(AiRoute.Mode.PROXY, base, headers)
        }
        return AiRoute(AiRoute.Mode.UNCONFIGURED, "", emptyMap())
    }
}

/**
 * The managed-proxy configuration, baked in at build time (empty in local/debug builds and until the
 * owner adds the `PROXY_BASE_URL` / `PROXY_APP_SECRET` CI secrets → [proxyConfigured] false → BYOK-only,
 * i.e. today's behaviour). Read from generated [BuildConfig] so it's a one-secret change, never a code
 * change. Also the single truth behind `AppSettings.aiEnabled` / `.cloudTranscription`.
 */
object AiEndpointConfig {
    /** Normalised exactly as [AiEndpointResolver.resolve] normalises it (trim + drop trailing slashes),
     *  so [proxyConfigured] can never disagree with [AiRoute.usable] — e.g. a stray `"/"` value reads
     *  as unconfigured on both sides. */
    val proxyBaseUrl: String get() = BuildConfig.PROXY_BASE_URL.trim().trimEnd('/')
    val appSecret: String get() = BuildConfig.PROXY_APP_SECRET.trim()

    /** True once the owner has deployed the relay and baked its URL — managed AI is then available to
     *  everyone, no key needed. Matches `AppSettings.aiEnabled`'s managed-mode check. */
    val proxyConfigured: Boolean get() = proxyBaseUrl.isNotEmpty()
}

/**
 * The Hilt-injected front door the providers call. Reads the live BYOK key from [SettingsStore] and
 * the baked proxy config, then hands back a concrete [AiRoute]. The per-install token is minted /
 * read **only** in proxy mode (a BYOK user never gets one), so nothing identifies a direct-Groq user.
 */
@Singleton
class AiEndpoint @Inject constructor(
    private val settings: SettingsStore,
) {
    suspend fun route(): AiRoute {
        val key = settings.settings.first().groqApiKey.trim()
        if (key.isNotEmpty()) return AiEndpointResolver.resolve(key, "", "", "")
        if (!AiEndpointConfig.proxyConfigured) {
            return AiRoute(AiRoute.Mode.UNCONFIGURED, "", emptyMap())
        }
        // Never let a rare DataStore write failure crash the capture path (firm invariant "never
        // interrupt capture"): fall back to an ephemeral id so the call still proceeds — quota keying
        // just won't persist for it.
        val installId = runCatching { settings.installId() }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: java.util.UUID.randomUUID().toString()
        return AiEndpointResolver.resolve(
            groqApiKey = "",
            proxyBaseUrl = AiEndpointConfig.proxyBaseUrl,
            appSecret = AiEndpointConfig.appSecret,
            installId = installId,
        )
    }
}
