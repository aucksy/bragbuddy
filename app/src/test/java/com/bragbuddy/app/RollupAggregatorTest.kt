package com.bragbuddy.app

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.rollup.RollupAggregator
import com.bragbuddy.app.data.rollup.RollupItem
import com.bragbuddy.app.data.rollup.toRollupItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the running-rollup projection + aggregation (Phase 5). */
class RollupAggregatorTest {

    private val fw = Framework.DEFAULT

    private fun item(
        id: Long,
        time: Long,
        area: String = "Performance Goals",
        bullet: String = "did a thing",
        impact: Double = 0.5,
        routine: Boolean = false,
        routineType: String? = null,
        metric: String? = null,
        demonstrates: List<String> = emptyList(),
        project: String? = null,
    ) = RollupItem(id, time, area, project, bullet, metric, impact, routine, routineType, false, demonstrates)

    // ---- toRollupItem projection ----

    private fun entry(
        id: Long,
        status: EntryStatus,
        bullet: String?,
        goal: String?,
    ) = EntryEntity(
        id = id, createdAt = 1000, source = EntrySource.TEXT, status = status,
        rawTranscript = "raw", bullet = bullet, goalCategory = goal,
    )

    @Test
    fun `only a placed, processed entry projects into the rollup`() {
        assertThat(entry(1, EntryStatus.PROCESSED, "clean bullet", "Performance Goals").toRollupItem()).isNotNull()
        assertThat(entry(2, EntryStatus.INBOX, "clean bullet", "Performance Goals").toRollupItem()).isNull()
        assertThat(entry(3, EntryStatus.PROCESSED, "  ", "Performance Goals").toRollupItem()).isNull()
        assertThat(entry(4, EntryStatus.PROCESSED, "clean bullet", "Inbox").toRollupItem()).isNull()
        assertThat(entry(5, EntryStatus.PROCESSED, "clean bullet", null).toRollupItem()).isNull()
    }

    // ---- windowing ----

    @Test
    fun `aggregate keeps only items inside the window`() {
        val items = listOf(
            item(1, time = 1500, bullet = "in window"),
            item(2, time = 500, bullet = "before"),
            item(3, time = 2500, bullet = "after"),
        )
        val agg = RollupAggregator.aggregate(items, startMillis = 1000, endMillisExclusive = 2000, framework = fw)
        assertThat(agg.entryCount).isEqualTo(1)
        assertThat(agg.goalAreas.first().highlights.map { it.bullet }).containsExactly("in window")
    }

    // ---- ranking + routine tally + behaviour evidence ----

    @Test
    fun `highlights rank by impact, routine rolls into a counted tally, behaviours gather evidence`() {
        val items = listOf(
            item(1, 1500, bullet = "Big win", impact = 0.9, demonstrates = listOf("Leadership & Behaviours")),
            item(2, 1400, bullet = "Small win", impact = 0.2),
            item(3, 1300, bullet = "ticket a", routine = true, routineType = "tickets", metric = "98% SLA"),
            item(4, 1200, bullet = "ticket b", routine = true, routineType = "tickets"),
        )
        val agg = RollupAggregator.aggregate(items, 1000, 2000, fw)
        val pg = agg.goalAreas.single { it.name == "Performance Goals" }

        assertThat(pg.highlights.map { it.bullet }).containsExactly("Big win", "Small win").inOrder()
        assertThat(pg.routine.single { it.routineType == "tickets" }.count).isEqualTo(2)
        assertThat(pg.metrics).contains("98% SLA")

        val leadership = agg.behaviours.single { it.name == "Leadership & Behaviours" }
        assertThat(leadership.evidence).containsExactly("Big win")
    }

    @Test
    fun `highlight cap bounds the model input`() {
        val items = (1..5L).map { item(it, 1000 + it, bullet = "win $it", impact = it / 10.0) }
        val agg = RollupAggregator.aggregate(items, 900, 2000, fw, highlightCap = 2)
        assertThat(agg.goalAreas.single().highlights).hasSize(2)
        // Top-2 by impact = win 5 (0.5) then win 4 (0.4).
        assertThat(agg.goalAreas.single().highlights.map { it.bullet }).containsExactly("win 5", "win 4").inOrder()
    }

    @Test
    fun `serialize renders the prompt block and signature is stable`() {
        val items = listOf(
            item(1, 1500, bullet = "Big win", impact = 0.9, demonstrates = listOf("Leadership & Behaviours")),
            item(2, 1400, bullet = "ticket", routine = true, routineType = "tickets"),
        )
        val agg = RollupAggregator.aggregate(items, 1000, 2000, fw)
        val text = RollupAggregator.serialize(agg)
        assertThat(text).contains("GOAL AREA: Performance Goals")
        assertThat(text).contains("tickets: 1×")
        assertThat(text).contains("BEHAVIOUR EVIDENCE")

        assertThat(RollupAggregator.signature("a", "b")).isEqualTo(RollupAggregator.signature("a", "b"))
        assertThat(RollupAggregator.signature("a", "b")).isNotEqualTo(RollupAggregator.signature("a", "c"))
    }

    @Test
    fun `development pillars serialize under a DEVELOPMENT AREA header, others stay goal areas`() {
        // Framework.DEFAULT: Performance Goals = GOAL_AREA, Learning & Growth = DEVELOPMENT.
        val items = listOf(
            item(1, 1500, area = "Performance Goals", bullet = "Shipped the thing", impact = 0.7),
            item(2, 1400, area = "learning & growth", bullet = "Completed the cert", impact = 0.5), // case-insensitive match
            item(3, 1300, area = "Some Legacy Area", bullet = "Old filed work", impact = 0.4),
        )
        val agg = RollupAggregator.aggregate(items, 1000, 2000, fw)
        assertThat(agg.goalAreas.single { it.name.equals("learning & growth", ignoreCase = true) }.isDevelopment).isTrue()
        assertThat(agg.goalAreas.single { it.name == "Performance Goals" }.isDevelopment).isFalse()

        val text = RollupAggregator.serialize(agg)
        assertThat(text).contains("DEVELOPMENT AREA: learning & growth")
        assertThat(text).contains("GOAL AREA: Performance Goals")
        // An area the framework no longer names (the catch-all guarantee) still reads as a goal area.
        assertThat(text).contains("GOAL AREA: Some Legacy Area")
        assertThat(text).doesNotContain("DEVELOPMENT AREA: Performance Goals")
    }

    @Test
    fun `an empty window aggregates to nothing`() {
        val agg = RollupAggregator.aggregate(emptyList(), 0, 1000, fw)
        assertThat(agg.isEmpty).isTrue()
        assertThat(RollupAggregator.serialize(agg)).isEmpty()
    }
}
