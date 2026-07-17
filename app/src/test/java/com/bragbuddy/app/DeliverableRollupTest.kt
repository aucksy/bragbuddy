package com.bragbuddy.app

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.rollup.DeliverableFact
import com.bragbuddy.app.data.rollup.RollupAggregator
import com.bragbuddy.app.data.rollup.RollupItem
import com.bragbuddy.app.data.rollup.toRollupItem
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the v0.34.0 deliverable-aware rollup — the phase's real payoff: the summary's
 * arc-merge stops being the model guessing from wording and gets a deterministic anchor.
 */
class DeliverableRollupTest {

    private val fw = Framework.DEFAULT
    private val DAY = 86_400_000L

    private fun item(
        id: Long,
        time: Long,
        bullet: String,
        project: String? = "Raven Migration",
        deliverable: String? = null,
        impact: Double = 0.5,
        routine: Boolean = false,
    ) = RollupItem(
        id = id, timeMillis = time, goalArea = "Performance Goals", project = project,
        deliverable = deliverable, bullet = bullet, impact = impact, routine = routine,
        routineType = if (routine) "tickets" else null,
    )

    // ---- projection ----

    @Test
    fun `toRollupItem carries the deliverable`() {
        val e = EntryEntity(
            id = 1, createdAt = 100, source = EntrySource.TEXT, rawTranscript = "t", status = EntryStatus.PROCESSED,
            bullet = "Took two markets live", goalCategory = "Performance Goals",
            project = "Raven Migration", deliverable = "Market rollout",
        )
        assertThat(e.toRollupItem()?.deliverable).isEqualTo("Market rollout")
    }

    @Test
    fun `a deliverable with no project is dropped — a name alone identifies nothing`() {
        val e = EntryEntity(
            id = 1, createdAt = 100, source = EntrySource.TEXT, rawTranscript = "t", status = EntryStatus.PROCESSED,
            bullet = "Did a thing", goalCategory = "Performance Goals",
            project = "Outside-project", deliverable = "Market rollout",
        )
        val r = e.toRollupItem()
        assertThat(r?.project).isNull()
        assertThat(r?.deliverable).isNull()
    }

    // ---- de-dup ----

    @Test
    fun `identical bullets in DIFFERENT deliverables do not merge`() {
        val items = listOf(
            item(1, 1100, "Signed off testing", deliverable = "Market rollout"),
            item(2, 1200, "Signed off testing", deliverable = "Defect triage"),
        )
        val hi = RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights
        assertThat(hi).hasSize(2)
    }

    @Test
    fun `identical bullets in the SAME deliverable still merge with a count`() {
        val items = listOf(
            item(1, 1100, "Signed off testing", deliverable = "Market rollout"),
            item(2, 1200, "signed off testing.", deliverable = "Market rollout"),
        )
        val hi = RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().highlights.single()
        assertThat(hi.count).isEqualTo(2)
        assertThat(hi.deliverable).isEqualTo("Market rollout")
    }

    // ---- aggregate ----

    @Test
    fun `deliverables are indexed with their FULL windowed count, not just the highlights`() {
        val items = listOf(
            item(1, 1_000_000, "Started rollout", deliverable = "Market rollout"),
            item(2, 2_000_000, "Two more markets", deliverable = "Market rollout"),
            // Routine work counts toward the thread's real size — it is what the user sees under it.
            item(3, 3_000_000, "Chased a ticket", deliverable = "Market rollout", routine = true),
            item(4, 4_000_000, "Loose work", deliverable = null),
        )
        val d = RollupAggregator.aggregate(items, 0, 9_000_000, fw).goalAreas.single().deliverables.single()
        assertThat(d.name).isEqualTo("Market rollout")
        assertThat(d.project).isEqualTo("Raven Migration")
        assertThat(d.entryCount).isEqualTo(3)
        assertThat(d.done).isFalse() // no fact supplied → reports as active, never lost
    }

    @Test
    fun `done comes from the live table, matched on the full identity`() {
        val items = listOf(item(1, 1100, "Shipped it", deliverable = "Market rollout"))
        val facts = listOf(
            DeliverableFact("Market rollout", "Raven Migration", "Performance Goals", done = true),
            // A same-named deliverable elsewhere must NOT bleed its state across.
            DeliverableFact("Market rollout", "Other Project", "Performance Goals", done = false),
        )
        val d = RollupAggregator.aggregate(items, 1000, 2000, fw, deliverables = facts)
            .goalAreas.single().deliverables.single()
        assertThat(d.done).isTrue()
    }

    @Test
    fun `the same deliverable name under two projects stays two threads`() {
        val items = listOf(
            item(1, 1100, "A", project = "Payments", deliverable = "Phase 1"),
            item(2, 1200, "B", project = "Billing", deliverable = "Phase 1"),
        )
        val ds = RollupAggregator.aggregate(items, 1000, 2000, fw).goalAreas.single().deliverables
        assertThat(ds).hasSize(2)
        assertThat(ds.map { it.project }).containsExactly("Payments", "Billing")
    }

    // ---- serialize ----

    @Test
    fun `serialize emits the deliverable index and tags each highlight`() {
        val items = listOf(
            item(1, 0, "Completed the rollout", deliverable = "Market rollout", impact = 0.9),
            item(2, 200 * DAY, "Loose work", deliverable = null),
        )
        val facts = listOf(DeliverableFact("Market rollout", "Raven Migration", "Performance Goals", done = true))
        val text = RollupAggregator.serialize(
            RollupAggregator.aggregate(items, 0, 400 * DAY, fw, deliverables = facts),
        )
        assertThat(text).contains("Deliverables (the user's own grouping")
        assertThat(text).contains("\"Market rollout\" (project: Raven Migration) — 1 entry, DONE")
        assertThat(text).contains("(deliverable: Market rollout)")
        // Loose work carries no tag at all — that absence is what tells the model rule 1 governs it.
        // Asserted on the LINE, not on the whole blob with a trailing "\n": serialize() ends in
        // .trim(), so the final line has no newline to match and the assertion would test the
        // serializer's padding rather than the thing it means to.
        val looseLine = text.lines().single { it.contains("Loose work") }
        assertThat(looseLine).doesNotContain("(deliverable:")
    }

    @Test
    fun `an area with no deliverables serializes exactly as before`() {
        val text = RollupAggregator.serialize(
            RollupAggregator.aggregate(listOf(item(1, 1100, "Did a thing")), 1000, 2000, fw),
        )
        assertThat(text).doesNotContain("Deliverables (")
        assertThat(text).doesNotContain("(deliverable:")
    }

    // ---- span label ----

    @Test
    fun `spanLabel reads in human units and stays quiet for short threads`() {
        assertThat(RollupAggregator.spanLabel(0, 3 * DAY)).isNull()
        assertThat(RollupAggregator.spanLabel(0, 9 * DAY)).isEqualTo("over 1 week")
        assertThat(RollupAggregator.spanLabel(0, 21 * DAY)).isEqualTo("over 3 weeks")
        assertThat(RollupAggregator.spanLabel(0, 210 * DAY)).isEqualTo("over 7 months")
        // Never negative, whatever the clock did.
        assertThat(RollupAggregator.spanLabel(5 * DAY, 0)).isNull()
    }
}
