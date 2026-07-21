package com.bragbuddy.app

import com.bragbuddy.app.data.ai.AiHttpException
import com.bragbuddy.app.ui.summary.GenerateFailureCopy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The generate-failure message must name what ACTUALLY happened. The regression this guards:
 * every failure used to read "check your connection", so a mid-generation timeout on a perfectly
 * good network (the v0.40.1 Detailed-summary bug) was reported as a connectivity problem.
 */
class GenerateFailureCopyTest {

    @Test
    fun `rejected key names Settings, not the connection`() {
        val msg = GenerateFailureCopy.messageFor(AiHttpException(401, "invalid_api_key"))
        assertThat(msg).contains("key")
        assertThat(msg).contains("Settings")
        assertThat(msg).contains("(Groq 401)")
        assertThat(msg).doesNotContain("connection")
    }

    @Test
    fun `403 maps like a key problem`() {
        assertThat(GenerateFailureCopy.messageFor(AiHttpException(403, ""))).contains("(Groq 403)")
    }

    @Test
    fun `413 suggests a shorter length`() {
        val msg = GenerateFailureCopy.messageFor(AiHttpException(413, "Request too large"))
        assertThat(msg).contains("One page")
        assertThat(msg).contains("(Groq 413)")
    }

    @Test
    fun `429 says wait, mentions the daily cap`() {
        val msg = GenerateFailureCopy.messageFor(AiHttpException(429, "rate_limit_exceeded"))
        assertThat(msg).contains("wait")
        assertThat(msg).contains("daily")
        assertThat(msg).contains("(Groq 429)")
    }

    @Test
    fun `server trouble is Groq's problem, not the user's`() {
        val msg = GenerateFailureCopy.messageFor(AiHttpException(503, ""))
        assertThat(msg).contains("Groq is having trouble")
        assertThat(msg).contains("(Groq 503)")
    }

    @Test
    fun `Groq's JSON-validation failure reads as a retryable garbled reply`() {
        // Groq's REAL body shape, run through the provider's own 300-char truncation — the reason
        // code sits past char 150, so a shorter snippet cap silently kills this branch (the review
        // catch on the first cut of this fix, which took 160).
        val realBody = """{"error":{"message":"Failed to generate JSON. Please adjust your prompt. """ +
            """See 'failed_generation' for more details.","type":"invalid_request_error",""" +
            """"code":"json_validate_failed","failed_generation":"{\"summary\": {\"goalAreas\""" +
            """: [{\"name\": \"Performance Goals\", \"achievements\": [..."}}"""
        val msg = GenerateFailureCopy.messageFor(AiHttpException(400, realBody.take(300)))
        assertThat(msg).contains("try again")
        assertThat(msg).contains("(Groq 400)")
        assertThat(msg).doesNotContain("connection")
        assertThat(msg).doesNotContain("Groq returned an error")
    }

    @Test
    fun `an unrecognised HTTP code still shows its number`() {
        assertThat(GenerateFailureCopy.messageFor(AiHttpException(418, ""))).contains("(Groq 418)")
    }

    @Test
    fun `a read timeout says the AI took too long — never blames the connection`() {
        val msg = GenerateFailureCopy.messageFor(SocketTimeoutException("timeout"))
        assertThat(msg).contains("took too long")
        assertThat(msg).doesNotContain("connection")
    }

    @Test
    fun `OkHttp's call timeout (the parent exception type) maps the same way`() {
        assertThat(GenerateFailureCopy.messageFor(InterruptedIOException("timeout")))
            .contains("took too long")
    }

    @Test
    fun `a genuine network failure is the ONLY case that blames the connection`() {
        assertThat(GenerateFailureCopy.messageFor(UnknownHostException("api.groq.com")))
            .contains("connection")
    }

    @Test
    fun `an unparseable reply asks for a retry`() {
        val msg = GenerateFailureCopy.messageFor(IllegalStateException("Empty completion"))
        assertThat(msg).contains("try again")
        assertThat(msg).doesNotContain("connection")
    }

    @Test
    fun `null stays generic and retryable`() {
        assertThat(GenerateFailureCopy.messageFor(null)).contains("try again")
    }

    @Test
    fun `plain IOException that is not a timeout maps to the connection message`() {
        assertThat(GenerateFailureCopy.messageFor(IOException("unexpected end of stream")))
            .contains("connection")
    }
}
