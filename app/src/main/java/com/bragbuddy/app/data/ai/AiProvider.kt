package com.bragbuddy.app.data.ai

/**
 * The one seam the whole AI layer hangs on. The Build Brief is explicit that *where the key lives
 * and who pays* (baked-in → BYOK → backend proxy) must be able to change without rewriting the
 * app — so every caller depends on this interface, never on a concrete provider.
 *
 * Implementations (added later): an OpenRouter-backed provider (Phase 2), and eventually a
 * proxy-backed one for public release. [StubAiProvider] stands in until then so the capture loop
 * (Phase 1) can be built and tested with no network or key.
 *
 * TWO MODELS, ROUTED BY TASK (PRD P0-12 / Build Brief § model routing) — the reason these are two
 * methods, not one: [categorize] runs on every entry and uses a cheap, JSON-reliable **free** model
 * (with a fallback slug on a 429); [generateSummary] runs rarely and uses the **strongest** model
 * (free while testing, a stable paid model at launch). Model slugs are remote-config values so a
 * vanished free model is a one-value change, not an app update — that config is the provider's
 * concern, kept behind this interface. Callers should meter each *fresh* summary generation via
 * [com.bragbuddy.app.data.usage.UsageMeter].
 *
 * Contract:
 *  - Both calls are suspending and may do network I/O; callers run them off the capture path.
 *  - Implementations MUST fail safe: on any error or unparseable output, surface it so the app can
 *    keep the raw transcript and route the entry to the Inbox. Never lose the user's words.
 */
interface AiProvider {

    /** Turn one transcript into one or more structured, appraisal-ready entries. */
    suspend fun categorize(request: CategorizeRequest): Result<CategorizeResult>

    /** Produce a curated, length-capped summary from the pre-aggregated rollup. */
    suspend fun generateSummary(request: SummaryRequest): Result<SummaryResult>

    /** Human-readable id of the active provider/model (for the backup-health / debug surfaces). */
    val label: String
}
