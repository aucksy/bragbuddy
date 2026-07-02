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
}
