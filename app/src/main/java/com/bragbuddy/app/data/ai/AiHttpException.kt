package com.bragbuddy.app.data.ai

/**
 * A non-2xx reply from the AI service, kept **typed** so a user-facing surface can say what
 * actually happened — key rejected (401/403), request too large (413), rate limit (429), server
 * trouble (5xx) — instead of collapsing every failure into "check your connection".
 * [bodySnippet] carries the first bytes of the error body (Groq's reason codes, e.g.
 * `json_validate_failed`), enough to tell failures apart without retaining a full payload.
 */
class AiHttpException(
    val code: Int,
    val bodySnippet: String,
) : Exception("Groq $code${if (bodySnippet.isNotBlank()) ": $bodySnippet" else ""}")
