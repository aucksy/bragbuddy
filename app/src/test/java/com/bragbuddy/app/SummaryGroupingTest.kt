package com.bragbuddy.app

import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.ui.summary.SUMMARY_OUTSIDE_LABEL
import com.bragbuddy.app.ui.summary.groupAchievementsByProject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the Summary tab's project-folder grouping (item 5). */
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
}
