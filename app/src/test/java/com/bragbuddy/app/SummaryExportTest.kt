package com.bragbuddy.app

import com.bragbuddy.app.data.ai.SetAsideNote
import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryBody
import com.bragbuddy.app.data.ai.SummaryCompetency
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.ai.SummaryResult
import com.bragbuddy.app.data.ai.SummaryRolledUp
import com.bragbuddy.app.ui.summary.exportBehaviour
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

    @Test
    fun `a behaviour with nested competencies exports the category header then each competency sub-head with its bullets`() {
        val leadership = SummaryBehaviour(
            name = "Leadership",
            evidence = listOf("Owned the incident retro end to end"),
            competencies = listOf(
                SummaryCompetency("Set the Agenda", listOf("Reframed the roadmap around customer outcomes")),
                SummaryCompetency("Bring Others With You", listOf("Mentored two engineers into tech-lead roles")),
            ),
        )
        val text = exportBehaviour(leadership)
        // Category is the single header; category-level evidence rides directly under it.
        assertThat(text).startsWith("LEADERSHIP")
        assertThat(text).contains("  • Owned the incident retro end to end")
        // Each competency is an indented sub-head with deeper-indented bullets.
        assertThat(text).contains("  Set the Agenda")
        assertThat(text).contains("    • Reframed the roadmap around customer outcomes")
        assertThat(text).contains("  Bring Others With You")
        assertThat(text).contains("    • Mentored two engineers into tech-lead roles")
        // The pillars are NOT surfaced as their own top-level (uppercase) headers.
        assertThat(text).doesNotContain("SET THE AGENDA")
    }

    @Test
    fun `the whole-summary export keeps a behaviour whose evidence lives only in nested competencies`() {
        // The item-4 happy path: a Leadership category with EMPTY top-level evidence, everything nested.
        val res = SummaryResult(
            summary = SummaryBody(
                behaviours = listOf(
                    SummaryBehaviour(
                        name = "Leadership",
                        evidence = emptyList(),
                        competencies = listOf(SummaryCompetency("Set the Agenda", listOf("Reframed the roadmap"))),
                    ),
                ),
            ),
        )
        val text = exportSummary(res, "Year-end summary")
        assertThat(text).contains("LEADERSHIP")
        assertThat(text).contains("  Set the Agenda")
        assertThat(text).contains("    • Reframed the roadmap")
    }

    @Test
    fun `an unnamed competency's evidence folds up to the category level instead of being lost`() {
        val res = SummaryResult(
            summary = SummaryBody(
                behaviours = listOf(
                    SummaryBehaviour(
                        name = "Leadership",
                        competencies = listOf(SummaryCompetency(name = "", evidence = listOf("Drove the incident retro"))),
                    ),
                ),
            ),
        )
        val block = exportBehaviour(res.summary.behaviours.single())
        // Folded up as a normal category bullet, not under an empty sub-heading.
        assertThat(block).contains("  • Drove the incident retro")
    }
}
