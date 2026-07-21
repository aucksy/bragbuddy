package com.bragbuddy.app

import com.bragbuddy.app.data.ai.SetAsideNote
import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryBody
import com.bragbuddy.app.data.ai.SummaryCompetency
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.ai.SummaryResult
import com.bragbuddy.app.data.ai.SummaryRolledUp
import com.bragbuddy.app.data.rollup.DeliverableFact
import com.bragbuddy.app.ui.summary.exportBehaviour
import com.bragbuddy.app.ui.summary.exportGoalArea
import com.bragbuddy.app.ui.summary.exportSummary
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the summary copy-out serialiser — since S1, the user's own hierarchy
 *  (`AREA → project → deliverable → pointers`) as real indented headers. */
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
    fun `whole summary exports headings, a project header, loose bullets, metrics and counts`() {
        val text = exportSummary(result, "Year-end summary")
        assertThat(text).contains("YEAR-END SUMMARY")
        assertThat(text).contains("PERFORMANCE GOALS")
        // The named project is a REAL indented header (S1), not a [tag] on the line...
        assertThat(text).contains("\n  Atlas\n")
        assertThat(text).contains("    • Led the Atlas redesign — drop-off down 18%")
        assertThat(text).doesNotContain("[Atlas]")
        // ...and the no-project win lists plainly under the goal area, after the named project.
        assertThat(text).contains("\n  • Migrated billing to the new API")
        assertThat(text.indexOf("Atlas")).isLessThan(text.indexOf("Migrated billing"))
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

    // ---- S1 · the full hierarchy: AREA → project → deliverable → pointers ----

    private val hierarchyArea = SummaryGoalArea(
        name = "Performance Goals",
        achievements = listOf(
            SummaryAchievement(
                "Resolved 145+ tech questions across the phase", project = "Intake Hub",
                deliverable = "Tech Questions", count = 4,
            ),
            SummaryAchievement("Translated the vision into a build-ready product", project = "Intake Hub"),
            SummaryAchievement(
                "Owned the ORE end-to-end", project = "Raven Migration",
                deliverable = "Truncated forms ORE", count = 7,
            ),
        ),
    )

    private val facts = listOf(
        DeliverableFact("Tech Questions", "Intake Hub", "Performance Goals", done = false),
        DeliverableFact("Truncated forms ORE", "Raven Migration", "Performance Goals", done = true),
    )

    @Test
    fun `deliverables export as indented sub-headings with Done state and from-N-logs counts`() {
        val text = exportGoalArea(hierarchyArea, facts)
        assertThat(text).isEqualTo(
            """
            PERFORMANCE GOALS
              Intake Hub
                Tech Questions
                  • Resolved 145+ tech questions across the phase  (from 4 logs)
                • Translated the vision into a build-ready product
              Raven Migration
                Truncated forms ORE (Done)
                  • Owned the ORE end-to-end  (from 7 logs)
            """.trimIndent(),
        )
    }

    @Test
    fun `a Done mark is scoped by the full identity — a same-named deliverable elsewhere stays active`() {
        val sameNameElsewhere = listOf(
            // Done under ANOTHER project: must not mark Intake Hub's group.
            DeliverableFact("Tech Questions", "Raven Migration", "Performance Goals", done = true),
        )
        val text = exportGoalArea(hierarchyArea, sameNameElsewhere)
        assertThat(text).contains("    Tech Questions\n")
        assertThat(text).doesNotContain("Tech Questions (Done)")
    }

    @Test
    fun `a single-project area still draws the project and deliverable headers (S1)`() {
        // The most common record shape: one project per area, one condensed story per deliverable.
        val area = SummaryGoalArea(
            name = "Performance Goals",
            achievements = listOf(
                SummaryAchievement("Owned the ORE end-to-end", project = "Raven Migration", deliverable = "Truncated forms ORE"),
            ),
        )
        val text = exportGoalArea(area, facts)
        assertThat(text).contains("\n  Raven Migration\n")
        assertThat(text).contains("    Truncated forms ORE (Done)")
        assertThat(text).contains("      • Owned the ORE end-to-end")
        assertThat(text).doesNotContain("[")
    }

    @Test
    fun `an all-loose area exports plainly with no headers`() {
        val area = SummaryGoalArea(
            name = "Learning & Growth",
            achievements = listOf(
                SummaryAchievement("Selected for the AI Studio cohort"),
                SummaryAchievement("Learned the platform-health process", project = "Outside-project"),
            ),
        )
        val text = exportGoalArea(area)
        assertThat(text).isEqualTo(
            """
            LEARNING & GROWTH
              • Selected for the AI Studio cohort
              • Learned the platform-health process
            """.trimIndent(),
        )
    }

    @Test
    fun `from-N-logs appears only when a pointer really merged several logs`() {
        val area = SummaryGoalArea(
            name = "Performance Goals",
            achievements = listOf(SummaryAchievement("Shipped the checkout redesign", count = 1)),
        )
        assertThat(exportGoalArea(area)).doesNotContain("from 1 log")
        assertThat(exportGoalArea(area)).doesNotContain("(from")
    }

    @Test
    fun `every pointer survives the grouping — nothing is dropped by the hierarchy`() {
        val text = exportGoalArea(hierarchyArea, facts)
        assertThat(text.lines().count { it.contains("•") }).isEqualTo(3)
    }

    @Test
    fun `a deliverable whose pointers are all blank is not exported as a bare heading`() {
        val area = SummaryGoalArea(
            name = "Performance Goals",
            achievements = listOf(
                SummaryAchievement("  ", project = "Intake Hub", deliverable = "Empty Thread"),
                SummaryAchievement("A real win", project = "Intake Hub"),
            ),
        )
        val text = exportGoalArea(area)
        assertThat(text).doesNotContain("Empty Thread")
        assertThat(text).contains("    • A real win")
    }

    // ---- behaviours (unchanged in S1 — tags on evidence arrive with S3) ----

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
