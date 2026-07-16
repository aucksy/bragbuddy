package com.bragbuddy.app

import com.bragbuddy.app.data.rollup.AggHighlight
import com.bragbuddy.app.data.summary.SummaryResolver
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The link from an AI-written summary line back to the record rows behind it (v0.31.0) — what makes a
 * Summary-tab retag correct the actual entries, and what makes the derived Set-aside list real.
 *
 * The bias under test is **conservative**: a wrong match silently re-files the wrong entry while the
 * user believes they fixed something, which is worse than not matching at all. So an ambiguous field
 * must resolve to nothing and let the caller fall back to a summary-only move.
 */
class SummaryResolverTest {

    private fun hl(bullet: String, ids: List<Long>, project: String? = null) =
        AggHighlight(
            bullet = bullet,
            project = project,
            metric = null,
            impact = 0.5,
            isExtra = false,
            demonstrates = emptyList(),
            count = ids.size,
            ids = ids,
        )

    // ---------------- resolve ----------------

    @Test
    fun `matches the model's reworded line back to its candidate`() {
        val candidates = listOf(
            hl("Shipped the checkout redesign to 100% of traffic, cutting drop-off 18%", listOf(7L)),
            hl("Cut payment failures 40% week-over-week after the incident fix", listOf(9L)),
        )
        // The model tightened the wording — the usual case, and exactly why exact matching won't do.
        val m = SummaryResolver.resolve("Shipped checkout redesign to 100% traffic, cutting drop-off 18%", candidates)
        assertThat(m).isNotNull()
        assertThat(m!!.entryIds).containsExactly(7L)
    }

    @Test
    fun `a merged card resolves to every entry behind it`() {
        val candidates = listOf(hl("Wrote the missing API documentation for the checkout endpoints", listOf(3L, 4L, 5L)))
        val m = SummaryResolver.resolve("Wrote the missing API documentation for the checkout endpoints", candidates)
        assertThat(m!!.entryIds).containsExactly(3L, 4L, 5L).inOrder()
    }

    @Test
    fun `an unrelated line resolves to nothing rather than the nearest candidate`() {
        val candidates = listOf(hl("Shipped the checkout redesign to 100% of traffic", listOf(1L)))
        assertThat(SummaryResolver.resolve("Completed the AWS solutions architect certification", candidates)).isNull()
    }

    @Test
    fun `two near-identical candidates are ambiguous, so nothing is written back`() {
        // The margin rule. Both candidates describe near-identical work; picking either would be a coin
        // flip that silently re-files a row the user never looked at.
        val candidates = listOf(
            hl("Migrated the promo-code service to the new platform", listOf(1L)),
            hl("Migrated the promo code service to the new platform", listOf(2L)),
        )
        assertThat(SummaryResolver.resolve("Migrated the promo-code service to the new platform", candidates)).isNull()
    }

    @Test
    fun `a candidate with no ids is never matched`() {
        // Nothing to write back to — must fall through to the summary-only path.
        val candidates = listOf(hl("Shipped the checkout redesign to 100% of traffic", emptyList()))
        assertThat(SummaryResolver.resolve("Shipped the checkout redesign to 100% of traffic", candidates)).isNull()
    }

    @Test
    fun `empty inputs resolve to nothing`() {
        assertThat(SummaryResolver.resolve("", listOf(hl("anything at all here", listOf(1L))))).isNull()
        assertThat(SummaryResolver.resolve("Shipped the redesign", emptyList())).isNull()
    }

    @Test
    fun `stop-words alone never carry a match`() {
        // "the and for with that this" share every token with any sentence; if stop-words counted, a
        // generic line would match the highest-overlap candidate and re-file it.
        val candidates = listOf(hl("Shipped the checkout redesign for the team with that plan", listOf(1L)))
        assertThat(SummaryResolver.resolve("The and for with that this", candidates)).isNull()
    }

    @Test
    fun `numbers are kept as content words`() {
        val candidates = listOf(
            hl("Cut drop-off 18% on the funnel", listOf(1L)),
            hl("Cut drop-off 40% on the funnel", listOf(2L)),
        )
        // Identical but for the metric — the number must be what separates them.
        val m = SummaryResolver.resolve("Cut drop-off 40% on the funnel", candidates)
        assertThat(m).isNotNull()
        assertThat(m!!.entryIds).containsExactly(2L)
    }

    // ---------------- dropped ----------------

    @Test
    fun `dropped returns the candidates no rendered line represents`() {
        val candidates = listOf(
            hl("Shipped the checkout redesign to 100% of traffic", listOf(1L)),
            hl("Launched the saved-cards feature ahead of the holiday freeze", listOf(2L)),
            hl("Built the A-B experiment harness for the funnel tests", listOf(3L)),
        )
        val used = listOf("Shipped checkout redesign to 100% traffic")
        val dropped = SummaryResolver.dropped(candidates, used)
        assertThat(dropped.map { it.ids.first() }).containsExactly(2L, 3L).inOrder()
    }

    @Test
    fun `dropped is empty when every candidate was used`() {
        val candidates = listOf(
            hl("Shipped the checkout redesign to 100% of traffic", listOf(1L)),
            hl("Launched the saved-cards feature ahead of the holiday freeze", listOf(2L)),
        )
        val used = candidates.map { it.bullet }
        assertThat(SummaryResolver.dropped(candidates, used)).isEmpty()
    }

    @Test
    fun `dropped counts a candidate as used even when the model reworded it heavily`() {
        // Leniency here is the correct bias: over-matching merely hides an item from the panel, while
        // under-matching would offer to "restore" a line already on screen — a visible duplicate.
        val candidates = listOf(hl("Launched the saved-cards feature ahead of the holiday freeze", listOf(2L)))
        val used = listOf("Launched saved-cards feature ahead of holiday freeze")
        assertThat(SummaryResolver.dropped(candidates, used)).isEmpty()
    }

    @Test
    fun `dropped returns everything when the summary rendered nothing for the area`() {
        val candidates = listOf(hl("Shipped the checkout redesign to 100% of traffic", listOf(1L)))
        assertThat(SummaryResolver.dropped(candidates, emptyList())).hasSize(1)
    }
}
