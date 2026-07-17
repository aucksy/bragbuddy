package com.bragbuddy.app

import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.rollup.AggDeliverable
import com.bragbuddy.app.ui.summary.IndexedAchievement
import com.bragbuddy.app.ui.summary.SUMMARY_OUTSIDE_LABEL
import com.bragbuddy.app.ui.summary.groupAchievementsByProject
import com.bragbuddy.app.ui.summary.groupFolderByDeliverable
import com.bragbuddy.app.ui.summary.resolveAchievementDeliverables
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the Summary tab's project-folder grouping (item 5) + its deliverable sub-grouping
 *  (v0.34.0 — the third level of the record: Category → Project → Deliverable). */
class SummaryGroupingTest {

    private fun ach(bullet: String, project: String?) = SummaryAchievement(bullet = bullet, project = project)

    @Test
    fun `multiple named projects group into folders in first-appearance order`() {
        val list = listOf(
            ach("a", "Checkout Redesign"),
            ach("b", "Payments Reliability"),
            ach("c", "Checkout Redesign"),
        )
        val folders = groupAchievementsByProject(list)!!
        assertThat(folders.map { it.name }).containsExactly("Checkout Redesign", "Payments Reliability").inOrder()
        // Flat indices are preserved so a folder-scoped reorder addresses the right slot.
        assertThat(folders[0].items.map { it.flatIndex }).containsExactly(0, 2).inOrder()
        assertThat(folders[1].items.single().flatIndex).isEqualTo(1)
    }

    @Test
    fun `loose achievements collapse into a single Outside bucket shown last`() {
        val list = listOf(
            ach("a", null),
            ach("b", "Atlas"),
            ach("c", "Outside-project"),
            ach("d", "Inbox"),
        )
        val folders = groupAchievementsByProject(list)!!
        assertThat(folders.map { it.name }).containsExactly("Atlas", SUMMARY_OUTSIDE_LABEL).inOrder()
        assertThat(folders.last().isOutside).isTrue()
        // null / "Outside-project" / "Inbox" all fold into the one Outside bucket.
        assertThat(folders.last().items.map { it.flatIndex }).containsExactly(0, 2, 3).inOrder()
    }

    @Test
    fun `a single-project area returns null so the caller renders flat`() {
        val list = listOf(ach("a", "Atlas"), ach("b", "Atlas"))
        assertThat(groupAchievementsByProject(list)).isNull()
    }

    @Test
    fun `an all-loose area returns null so the caller renders flat`() {
        val list = listOf(ach("a", null), ach("b", "Outside-project"))
        assertThat(groupAchievementsByProject(list)).isNull()
    }

    @Test
    fun `grouping is case-insensitive but keeps the first-seen display name`() {
        val list = listOf(ach("a", "Checkout Redesign"), ach("b", "checkout redesign"), ach("c", "Payments"))
        val folders = groupAchievementsByProject(list)!!
        assertThat(folders.map { it.name }).containsExactly("Checkout Redesign", "Payments").inOrder()
        assertThat(folders[0].items.map { it.flatIndex }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `an empty area returns null`() {
        assertThat(groupAchievementsByProject(emptyList())).isNull()
    }

    // ---- v0.34.0 · deliverable sub-grouping inside a project folder ----

    private fun idx(vararg items: SummaryAchievement) = items.mapIndexed { i, a -> IndexedAchievement(i, a) }

    private fun del(bullet: String, deliverable: String?) =
        SummaryAchievement(bullet = bullet, project = "Raven Migration", deliverable = deliverable)

    @Test
    fun `deliverables group in first-appearance order with loose work last`() {
        val groups = groupFolderByDeliverable(
            idx(
                del("a", "Market rollout"),
                del("b", null),
                del("c", "Defect triage"),
                del("d", "Market rollout"),
            ),
        )!!
        assertThat(groups.map { it.name }).containsExactly("Market rollout", "Defect triage", null).inOrder()
        // Flat indices survive, so a group-scoped reorder still addresses the right slot.
        assertThat(groups[0].items.map { it.flatIndex }).containsExactly(0, 3).inOrder()
        assertThat(groups[2].items.single().flatIndex).isEqualTo(1)
    }

    @Test
    fun `grouping is case-insensitive and keeps the first-seen display name`() {
        val groups = groupFolderByDeliverable(idx(del("a", "Market rollout"), del("b", "market  rollout"), del("c", null)))!!
        assertThat(groups.map { it.name }).containsExactly("Market rollout", null).inOrder()
        // Both spellings are the same thread, so they land in ONE group under the first-seen casing.
        assertThat(groups[0].items.map { it.flatIndex }).containsExactly(0, 1).inOrder()
    }

    @Test
    fun `no structure worth drawing returns null — the folder renders exactly as before`() {
        // All one deliverable...
        assertThat(groupFolderByDeliverable(idx(del("a", "Market rollout"), del("b", "Market rollout")))).isNull()
        // ...all loose...
        assertThat(groupFolderByDeliverable(idx(del("a", null), del("b", null)))).isNull()
        // ...and nothing at all.
        assertThat(groupFolderByDeliverable(emptyList())).isNull()
    }

    @Test
    fun `a blank deliverable counts as loose, never as its own heading`() {
        val groups = groupFolderByDeliverable(idx(del("a", "  "), del("b", "Market rollout")))!!
        assertThat(groups.map { it.name }).containsExactly("Market rollout", null).inOrder()
    }

    // ---- v0.34.0 · resolving PART B's echoed deliverable against the rollup ----

    private fun aggDel(name: String, project: String) =
        AggDeliverable(name = name, project = project, done = false, entryCount = 1, firstMillis = 0, lastMillis = 0)

    private val agg = listOf(aggDel("Market rollout", "Raven Migration"), aggDel("Phase 1", "Payments"))

    @Test
    fun `an echoed deliverable the rollup named survives, in the STORED casing`() {
        val out = resolveAchievementDeliverables(
            listOf(SummaryAchievement(bullet = "a", project = "Raven Migration", deliverable = "market  rollout")),
            agg,
        )
        assertThat(out.single().deliverable).isEqualTo("Market rollout")
    }

    @Test
    fun `a paraphrased or invented deliverable is dropped, not rendered`() {
        // Ordinary LLM behaviour, not a fault — but an unresolved name would mint a sub-header for a
        // deliverable that doesn't exist and print it into the doc the user hands their manager.
        val out = resolveAchievementDeliverables(
            listOf(SummaryAchievement(bullet = "a", project = "Raven Migration", deliverable = "Market rollout phase")),
            agg,
        )
        assertThat(out.single().deliverable).isNull()
    }

    @Test
    fun `a real deliverable of ANOTHER project is dropped — a name is not an identity`() {
        val out = resolveAchievementDeliverables(
            listOf(SummaryAchievement(bullet = "a", project = "Raven Migration", deliverable = "Phase 1")),
            agg,
        )
        assertThat(out.single().deliverable).isNull()
    }

    @Test
    fun `achievements with no deliverable pass through untouched`() {
        val input = listOf(SummaryAchievement(bullet = "a", project = "Raven Migration"))
        assertThat(resolveAchievementDeliverables(input, agg)).isEqualTo(input)
        assertThat(resolveAchievementDeliverables(input, emptyList())).isEqualTo(input)
    }
}
