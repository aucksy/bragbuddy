package com.bragbuddy.app.data.ai

/**
 * The one place model slugs and the endpoint live — kept together so a retired model is a
 * **one-value change here**, never an app rewrite (Build Brief § "make the model a remote-config
 * value, not hardcoded"). When a backend/remote-config lands, these defaults become the fallback and
 * the values are fetched instead; nothing else in the app changes.
 *
 * PROVIDER = **Groq** (OpenAI-compatible LLM inference). The creator chose to reuse the single Groq
 * key for BOTH cloud Whisper transcription AND the LLM brain (categorizer + framework refine) — one
 * key, faster, and Groq's API doesn't train on the data (unlike OpenRouter's free tier). The
 * `AiProvider` seam stays swappable, so a public launch can still route the **summary** to a
 * provider-backed paid model (Gemini / Claude / GPT) via OpenRouter or that provider directly.
 *
 * TWO MODELS, ROUTED BY TASK (PRD P0-12):
 *  - [categorizerModel] (+ [categorizerFallback] on a rate-limit / transient error) runs on **every**
 *    entry → a fast, JSON-reliable model.
 *  - [summaryModel] runs rarely (Phase 5) → the strongest available; may repoint to a paid model at
 *    launch.
 *
 * Slugs verified against console.groq.com/docs/models (2026-07) — swapping them is the intended
 * maintenance action if Groq retires one.
 */
object AiConfig {
    const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"

    /** Daily categorizer: Groq's versatile flagship — strong instruction-following + JSON mode. */
    const val categorizerModel = "llama-3.3-70b-versatile"

    /** Tried when the primary rate-limits / errors: the fast, always-on small model. */
    const val categorizerFallback = "llama-3.1-8b-instant"

    /** Framework refine-by-voice → the same reliable categorizer model. */
    const val frameworkModel = categorizerModel
    const val frameworkFallback = categorizerFallback

    /** Summary (Phase 5) — strongest Groq model for now; may switch to a paid provider slug at launch. */
    const val summaryModel = "openai/gpt-oss-120b"
    const val summaryFallback = "llama-3.3-70b-versatile"

    /**
     * Image scanning (Phase A) — Groq's **production** multimodal model, reads a photo / screenshot
     * into work text. `qwen/qwen3.6-27b` is production + JSON mode; `meta-llama/llama-4-scout` is a
     * *preview* model being deprecated, so it is NOT used. Verified live 2026-07 against
     * console.groq.com/docs/vision — re-verify (and repoint) if Groq retires it. Images ride as a
     * base64 `data:` URL capped at 4 MB (see [com.bragbuddy.app.data.image.ImageInput]).
     */
    const val visionModel = "qwen/qwen3.6-27b"

    /** Fallback vision slug tried if [visionModel] errors (e.g. it gets retired) — the previous Groq
     *  multimodal model. Preview/deprecating, so it's the safety net, not the primary; if it's also
     *  gone the call fails safe (the capture stays on the sheet to retry / type). */
    const val visionFallback = "meta-llama/llama-4-scout-17b-16e-instruct"

    /** A short, human label for the About / debug surface. */
    fun categorizerLabel(): String = "Groq · $categorizerModel"
}
