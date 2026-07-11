package com.bragbuddy.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton

/**
 * A no-network placeholder used until a real provider is wired (Phase 2). It performs no
 * categorisation: it echoes the transcript back as a single entry routed to the **Inbox** with
 * zero confidence — exactly the fail-safe behaviour the real provider must also honour, so the
 * capture loop and Inbox flow can be built against a stable contract first.
 */
@Singleton
class StubAiProvider @Inject constructor() : AiProvider {

    override val label: String = "Stub (no AI yet)"

    override suspend fun categorize(request: CategorizeRequest): Result<CategorizeResult> {
        val text = request.transcript.trim()
        if (text.isEmpty()) return Result.success(CategorizeResult(emptyList()))
        val entry = CategorizedEntry(
            bullet = text,
            project = "Inbox",
            goalCategory = "Inbox",
            confidence = 0.0,
        )
        return Result.success(CategorizeResult(listOf(entry)))
    }

    override suspend fun generateSummary(request: SummaryRequest): Result<SummaryResult> =
        Result.success(SummaryResult(summary = SummaryBody()))

    override suspend fun refineFramework(request: FrameworkRefineRequest): Result<FrameworkRefineResult> =
        Result.failure(IllegalStateException("No AI provider wired"))

    // No vision without a real provider — return "no work content" so the caller shows its gentle
    // hint rather than an error (fail-safe: nothing is filed either way).
    override suspend fun extractFromImage(request: ImageExtractRequest): Result<ImageExtractResult> =
        Result.success(ImageExtractResult(""))

    // No vision without a real provider — "no readable text" leaves the field unchanged (fail-safe).
    override suspend fun readDocumentText(request: ImageExtractRequest): Result<ImageExtractResult> =
        Result.success(ImageExtractResult(""))

    // No AI to tailor the nudge — return the generic coaching question the caller also uses on failure.
    override suspend fun suggestImpact(request: ImpactSuggestRequest): Result<ImpactSuggestion> =
        Result.success(ImpactSuggestion("What changed or improved — can you put a number on it?"))
}
