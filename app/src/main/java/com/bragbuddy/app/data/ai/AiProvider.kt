package com.bragbuddy.app.data.ai

/**
 * The one seam the whole AI layer hangs on. The Build Brief is explicit that *where the key lives
 * and who pays* (baked-in → BYOK → backend proxy) must be able to change without rewriting the
 * app — so every caller depends on this interface, never on a concrete provider.
 *
 * Active implementation: [GroqAiProvider] (Phase 2 — reuses the on-device Groq key). A proxy-backed
 * provider comes later for public release; [StubAiProvider] remains as the no-network reference.
 *
 * TWO MODELS, ROUTED BY TASK (PRD P0-12 / Build Brief § model routing) — the reason these are two
 * methods, not one: [categorize] runs on every entry and uses a fast, JSON-reliable model (with a
 * fallback slug on a rate-limit/transient error); [generateSummary] runs rarely and uses the
 * **strongest** model (may switch to a paid provider slug at launch). Model slugs are remote-config
 * values so a retired model is a one-value change, not an app update — that config is the provider's
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

    /**
     * Turn a plain-language description of how the user is judged into structured pillars
     * (BragBuddy-System-Prompt PART C). One-time setup / refine-by-voice; never asks the company
     * name. Failure is safe — the caller keeps the existing framework unchanged.
     */
    suspend fun refineFramework(request: FrameworkRefineRequest): Result<FrameworkRefineResult>

    /**
     * Read a captured image (a base64 `data:` URL) into a first-person work note the user can edit
     * before it's filed. Runs on the multimodal [AiConfig.visionModel]. Failure is safe — the caller
     * keeps the user on the capture sheet to retry or type instead; nothing is filed. A successful
     * call with an empty [ImageExtractResult.text] means "no work content in this image" (the caller
     * shows a gentle hint), which is distinct from a failure.
     */
    suspend fun extractFromImage(request: ImageExtractRequest): Result<ImageExtractResult>

    /**
     * Read a scanned REFERENCE DOCUMENT (a job description / appraisal form / review criteria — a
     * base64 `data:` URL) into clean text the user edits before it fills a framework or project
     * description field (Phase B2 · framework editing). Runs on the multimodal [AiConfig.visionModel]
     * with the doc-scan prompt (distinct from [extractFromImage], which reads "the work you did").
     * Failure is safe — the caller keeps the field unchanged so the user types instead. A successful
     * call with an empty [ImageExtractResult.text] means "no readable text in this image".
     */
    suspend fun readDocumentText(request: ImageExtractRequest): Result<ImageExtractResult>

    /** Human-readable id of the active provider/model (for the backup-health / debug surfaces). */
    val label: String
}
