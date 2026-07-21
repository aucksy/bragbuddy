package com.bragbuddy.app.ui.summary

import com.bragbuddy.app.data.ai.AiHttpException
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Turns a summary-generation failure into an honest, plain-English message. Before this existed,
 * EVERY failure — a rejected key, a rate limit, a timeout mid-generation — showed "check your
 * connection", which misdiagnosed the problem to the user on a perfectly good network and left
 * nothing to act on. Pure (no Android deps) so the mapping is unit-tested.
 *
 * The short "(Groq NNN)" tail on HTTP cases is deliberate: it costs three words and turns any
 * future bug report into an instant diagnosis.
 */
object GenerateFailureCopy {

    fun messageFor(error: Throwable?): String = when {
        error is AiHttpException -> when {
            error.code == 401 || error.code == 403 ->
                "Groq didn't accept your AI key — check it in Settings → AI engine. (Groq ${error.code})"
            error.code == 413 ->
                "That's too much to generate in one go on your Groq plan — try One page or Brief. (Groq 413)"
            error.code == 429 ->
                "You've hit your Groq plan's speed limit — wait a minute and try again. Free plans also have a daily cap. (Groq 429)"
            error.code in 500..599 ->
                "Groq is having trouble right now — try again in a few minutes. (Groq ${error.code})"
            // Groq's JSON mode reports a reply that failed its own JSON check as a 400 with this
            // reason code — the model wrote something unreadable; a retry usually succeeds.
            error.bodySnippet.contains("json_validate_failed") ->
                "The AI wrote a reply that couldn't be read — tap Generate to try again. (Groq ${error.code})"
            else ->
                "Groq returned an error — try again in a moment. (Groq ${error.code})"
        }
        // SocketTimeoutException extends InterruptedIOException; OkHttp's call timeout throws the
        // parent. Both mean the same thing here: the model was still writing when we hung up.
        error is InterruptedIOException ->
            "The AI took too long to reply — try again. If it keeps happening, pick a shorter length."
        error is IOException ->
            "Couldn't reach the AI service — check your connection and try again."
        else ->
            "The AI's reply couldn't be read — tap Generate to try again."
    }
}
