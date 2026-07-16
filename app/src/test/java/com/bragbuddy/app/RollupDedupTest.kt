package com.bragbuddy.app

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.rollup.RollupAggregator
import com.bragbuddy.app.data.rollup.RollupItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the summary de-dup pre-merge (Phase 1, feature #9). */
class RollupDedupTest {

    private val fw = Framework.DEFAULT

    private fun item(
        id: Long,
        time: Long,
        bullet: String,
        impact: Double = 0.5,
        project: String? = null,
        metric: String? = null,
    ) = RollupItem(id, time, "Performance Goals", project, bullet, metric, impact, false, null, false, emptyList())

    @Test
    fun `identical bullets merge into one highlight with a count`() {
        val items = listOf(
            item(1, 1100, "Ran the weekly report"),
            item(2, 1200, "Ran the weekly report."),   // trailing punctuation
            item(3, 1300, "ran the WEEKLY report"),     // case + whitespace
        )
        val hi = RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights
        assertThat(hi).hasSize(1)
        assertThat(hi.single().count).isEqualTo(3)
    }

    /**
     * v0.31.0: the aggregate carries every merged row's entry id CLIENT-SIDE, so a summary line can be
     * traced back to the record ([SummaryResolver]). Previously the id died here, in the map to
     * AggHighlight — which is the only reason a Summary-tab correction was impossible.
     */
    @Test
    fun `a merged highlight carries every source entry id, representative first`() {
        val items = listOf(
            item(1, 1100, "Ran the weekly report", impact = 0.4),
            item(2, 1200, "Ran the weekly report.", impact = 0.9), // highest impact = the representative
            item(3, 1300, "ran the WEEKLY report", impact = 0.5),
        )
        val hi = RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights.single()
        assertThat(hi.count).isEqualTo(3)
        assertThat(hi.ids).containsExactly(2L, 1L, 3L).inOrder()
    }

    @Test
    fun `an unmerged highlight carries its single id`() {
        val hi = RollupAggregator.aggregate(listOf(item(42, 1100, "Shipped the thing")), 1000, 2000, fw)
            .goalAreas.single().highlights.single()
        assertThat(hi.ids).containsExactly(42L)
    }

    /** The ids must never reach the model: they'd change the prompt (→ an eval gate) for no gain, and
     *  id-echo is a known LLM failure mode. The signature is taken over serialize(), so this also keeps
     *  every cached summary from being invalidated by the new field. */
    @Test
    fun `serialized rollup never leaks an entry id`() {
        val agg = RollupAggregator.aggregate(
            listOf(item(987654321, 1100, "Shipped the thing", metric = "cut 18%")), 1000, 2000, fw,
        )
        assertThat(RollupAggregator.serialize(agg)).doesNotContain("987654321")
    }

    @Test
    fun `a different project keeps duplicate bullets separate`() {
        val items = listOf(
            item(1, 1100, "Fixed a bug", project = "Alpha"),
            item(2, 1200, "Fixed a bug", project = "Beta"),
        )
        assertThat(RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights).hasSize(2)
    }

    @Test
    fun `a progressive arc is NOT merged`() {
        val items = listOf(
            item(1, 1100, "Started the onboarding redesign"),
            item(2, 1200, "Shipped the onboarding redesign, cut drop-off 18%"),
        )
        assertThat(RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights).hasSize(2)
    }

    @Test
    fun `count stays accurate even past the highlight cap`() {
        val items = (1..5L).map { item(it, 1000 + it, "Same task") }
        val hi = RollupAggregator.aggregate(items, 900, 2000, fw, highlightCap = 2).goalAreas.single().highlights
        assertThat(hi).hasSize(1)
        assertThat(hi.single().count).isEqualTo(5)
    }

    @Test
    fun `serialize notes the merged count`() {
        val items = listOf(item(1, 1100, "Same task"), item(2, 1200, "Same task"))
        val text = RollupAggregator.serialize(RollupAggregator.aggregate(items, 1000, 2000, fw))
        assertThat(text).contains("(logged 2×)")
    }

    @Test
    fun `the highest-impact phrasing wins the representative`() {
        val items = listOf(
            item(1, 1100, "did the thing", impact = 0.3),
            item(2, 1200, "Did The Thing", impact = 0.9),   // normalizes equal → merged
        )
        val hi = RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights.single()
        assertThat(hi.bullet).isEqualTo("Did The Thing")
        assertThat(hi.impact).isEqualTo(0.9)
        assertThat(hi.count).isEqualTo(2)
    }

    @Test
    fun `merge unions metric, isExtra and demonstrates across merged rows`() {
        val items = listOf(
            // rep (higher impact) has NO metric and isExtra=false; the merged row supplies them.
            RollupItem(1, 1100, "Performance Goals", null, "Shipped feature", null, 0.9, false, null, false, listOf("Leadership & Behaviours")),
            RollupItem(2, 1200, "Performance Goals", null, "shipped feature.", "cut latency 30%", 0.4, false, null, true, listOf("Learning & Growth")),
        )
        val hi = RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights.single()
        assertThat(hi.count).isEqualTo(2)
        assertThat(hi.metric).isEqualTo("cut latency 30%")       // rep null → fallback to the other row
        assertThat(hi.isExtra).isTrue()                          // OR-union
        assertThat(hi.demonstrates).containsExactly("Leadership & Behaviours", "Learning & Growth")
    }
}
