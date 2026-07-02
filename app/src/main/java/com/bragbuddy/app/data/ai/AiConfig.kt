package com.bragbuddy.app.data.ai

/**
 * The one place model slugs and endpoints live — kept deliberately together so a vanished free
 * model is a **one-value change here**, never an app rewrite (Build Brief § "make the model a
 * remote-config value, not hardcoded"). When a backend/remote-config lands, these defaults become
 * the fallback and the values are fetched instead; nothing else in the app changes.
 *
 * TWO MODELS, ROUTED BY TASK (PRD P0-12):
 *  - [categorizerModel] (+ [categorizerFallback] on a 429/transient error) runs on **every** entry
 *    → a free, JSON-reliable model.
 *  - [summaryModel] runs rarely (Phase 5) → the strongest model; a free one while testing, a stable
 *    paid slug at launch.
 *
 * Free slugs on OpenRouter change often — verify at https://openrouter.ai/models (filter: free).
 * These are the current picks; swapping them is the intended maintenance action.
 */
object AiConfig {
    const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"

    /** Daily categorizer: free + strong at structured JSON. */
    const val categorizerModel = "deepseek/deepseek-chat-v3-0324:free"

    /** Tried when the primary returns a rate-limit / transient error. A different free family. */
    const val categorizerFallback = "meta-llama/llama-3.3-70b-instruct:free"

    /** Framework refine-by-voice → the same reliable free categorizer model. */
    const val frameworkModel = categorizerModel
    const val frameworkFallback = categorizerFallback

    /** Summary (Phase 5) — strongest free model for now; swaps to a stable paid slug at launch. */
    const val summaryModel = "deepseek/deepseek-r1-0528:free"
    const val summaryFallback = "meta-llama/llama-3.3-70b-instruct:free"

    /** OpenRouter attribution headers (optional but recommended). */
    const val REFERER = "https://github.com/aucksy/bragbuddy"
    const val TITLE = "BragBuddy"

    /** A short, human label for the About / debug surface. */
    fun categorizerLabel(): String = "OpenRouter · ${categorizerModel.substringAfterLast('/')}"
}
