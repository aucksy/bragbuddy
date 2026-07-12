package com.bragbuddy.app

import com.bragbuddy.app.data.ai.AiEndpointResolver
import com.bragbuddy.app.data.ai.AiRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Phase M1 · the pure transport-routing logic behind [com.bragbuddy.app.data.ai.AiEndpoint].
 * BYOK key wins → direct-Groq; else the managed proxy if configured; else nothing (fail-safe).
 */
class AiEndpointResolverTest {

    private val proxy = "https://relay.bragbuddy.workers.dev/v1"

    @Test
    fun `a BYOK key always routes direct to Groq, ignoring the proxy`() {
        val route = AiEndpointResolver.resolve(
            groqApiKey = "gsk_abc123",
            proxyBaseUrl = proxy,
            appSecret = "s3cret",
            installId = "install-1",
        )
        assertThat(route.mode).isEqualTo(AiRoute.Mode.DIRECT_GROQ)
        assertThat(route.baseUrl).isEqualTo(AiEndpointResolver.GROQ_BASE)
        assertThat(route.usable).isTrue()
        assertThat(route.authHeaders).containsExactly("Authorization", "Bearer gsk_abc123")
        assertThat(route.chatUrl()).isEqualTo("https://api.groq.com/openai/v1/chat/completions")
        assertThat(route.transcriptionUrl()).isEqualTo("https://api.groq.com/openai/v1/audio/transcriptions")
    }

    @Test
    fun `no key plus a configured proxy routes through the relay with token and app-key`() {
        val route = AiEndpointResolver.resolve(
            groqApiKey = "",
            proxyBaseUrl = proxy,
            appSecret = "s3cret",
            installId = "install-1",
        )
        assertThat(route.mode).isEqualTo(AiRoute.Mode.PROXY)
        assertThat(route.baseUrl).isEqualTo(proxy)
        assertThat(route.usable).isTrue()
        assertThat(route.authHeaders).containsExactly(
            AiEndpointResolver.HEADER_INSTALL_ID, "install-1",
            AiEndpointResolver.HEADER_APP_KEY, "s3cret",
        )
        // The relay must NOT carry a Groq Authorization header — the server injects it.
        assertThat(route.authHeaders).doesNotContainKey("Authorization")
        assertThat(route.chatUrl()).isEqualTo("$proxy/chat/completions")
        assertThat(route.transcriptionUrl()).isEqualTo("$proxy/audio/transcriptions")
    }

    @Test
    fun `proxy base url is trimmed of trailing slashes`() {
        val route = AiEndpointResolver.resolve("", "$proxy///", "s", "id")
        assertThat(route.baseUrl).isEqualTo(proxy)
        assertThat(route.chatUrl()).isEqualTo("$proxy/chat/completions")
    }

    @Test
    fun `a blank app secret is simply omitted (still proxy, still usable)`() {
        val route = AiEndpointResolver.resolve("", proxy, "  ", "install-1")
        assertThat(route.mode).isEqualTo(AiRoute.Mode.PROXY)
        assertThat(route.authHeaders).containsExactly(AiEndpointResolver.HEADER_INSTALL_ID, "install-1")
    }

    @Test
    fun `a blank install id is simply omitted (defensive)`() {
        val route = AiEndpointResolver.resolve("", proxy, "s3cret", "")
        assertThat(route.mode).isEqualTo(AiRoute.Mode.PROXY)
        assertThat(route.authHeaders).containsExactly(AiEndpointResolver.HEADER_APP_KEY, "s3cret")
    }

    @Test
    fun `no key and no proxy is unconfigured and not usable`() {
        val route = AiEndpointResolver.resolve("", "", "", "")
        assertThat(route.mode).isEqualTo(AiRoute.Mode.UNCONFIGURED)
        assertThat(route.usable).isFalse()
        assertThat(route.baseUrl).isEmpty()
        assertThat(route.authHeaders).isEmpty()
    }

    @Test
    fun `a whitespace-only key is treated as no key`() {
        // Whitespace key + no proxy → unconfigured (not a bogus direct route with a blank bearer).
        assertThat(AiEndpointResolver.resolve("   ", "", "", "").mode)
            .isEqualTo(AiRoute.Mode.UNCONFIGURED)
        // Whitespace key + a proxy → falls through to the managed relay.
        assertThat(AiEndpointResolver.resolve("   ", proxy, "s", "id").mode)
            .isEqualTo(AiRoute.Mode.PROXY)
    }

    @Test
    fun `a BYOK key is trimmed before use`() {
        val route = AiEndpointResolver.resolve("  gsk_xyz  ", "", "", "")
        assertThat(route.authHeaders["Authorization"]).isEqualTo("Bearer gsk_xyz")
    }
}
