package com.bragbuddy.app

import com.bragbuddy.app.data.ai.SetAsideNote
import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryBody
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.ai.SummaryResult
import com.bragbuddy.app.data.ai.SummaryRolledUp
import com.bragbuddy.app.ui.summary.exportGoalArea
import com.bragbuddy.app.ui.summary.exportSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the summary copy-out serialiser. */
class SummaryExportTest {

    private val result = SummaryResult(
        summary = SummaryBody(
            goalAreas = listOf(
                SummaryGoalArea(
                    name = "Performance Goals",
                    achievements = listOf(
                        SummaryAchievement("Led the Atlas redesign", project = "Atlas", metric = "drop-off down 18%"),
                        SummaryAchievement("Migrated billing to the new API"),
                    ),
                    rolledUp = listOf(SummaryRolledUp("Reviewed & merged pull requests", "pull requests", 42)),
                ),
            ),
            behaviours = listOf(SummaryBehaviour("Leadership & Behaviours", listOf("Mentored two interns"))),
            development = listOf("Completed the staff-engineer track"),
        ),
        setAside = listOf(SetAsideNote("Routine check-ins", "condensed to keep to one page")),
    )

    @Test
    fun `whole summary exports headings, bullets, metrics, project and counts`() {
        val text = exportSummary(result, "Year-end summary")
        assertThat(text).contains("YEAR-END SUMMARY")
        assertThat(text).contains("PERFORMANCE GOALS")
        assertThat(text).contains("  • Led the Atlas redesign — drop-off down 18%  [Atlas]")
        assertThat(text).contains("  • Migrated billing to the new API")
        assertThat(text).contains("×42")
        assertThat(text).contains("LEADERSHIP & BEHAVIOURS")
        assertThat(text).contains("  • Mentored two interns")
        assertThat(text).contains("LEARNING & GROWTH")
    }

    @Test
    fun `the set-aside reassurance notes are NOT part of the pasted document`() {
        assertThat(exportSummary(result, "")).doesNotContain("Routine check-ins")
    }

    @Test
    fun `a goal-area section exports just that section`() {
        val block = exportGoalArea(result.summary.goalAreas.first())
        assertThat(block).startsWith("PERFORMANCE GOALS")
        assertThat(block).contains("×42")
    }
}
