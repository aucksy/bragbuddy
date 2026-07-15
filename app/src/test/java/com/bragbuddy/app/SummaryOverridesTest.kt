package com.bragbuddy.app

import com.bragbuddy.app.data.ai.SetAsideNote
import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryCompetency
import com.bragbuddy.app.data.ai.SummaryBody
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.ai.SummaryResult
import com.bragbuddy.app.data.ai.SummaryRolledUp
import com.bragbuddy.app.data.summary.BulletEdit
import com.bragbuddy.app.data.summary.RestoredNote
import com.bragbuddy.app.data.summary.SummaryOverrides
import com.bragbuddy.app.data.summary.applyOverrides
import com.bragbuddy.app.data.summary.summaryKey
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the persistent summary-edit overrides (Phase 1, features #1 + #5). */
class SummaryOverridesTest {

    private fun result(
        achievements: List<String> = emptyList(),
        area: String = "Performance Goals",
        setAside: List<String> = emptyList(),
    ) = SummaryResult(
        summary = SummaryBody(
            goalAreas = listOf(
                SummaryGoalArea(name = area, achievements = achievements.map { SummaryAchievement(bullet = it) }),
            ),
        ),
        setAside = setAside.map { SetAsideNote(what = it, why = "condensed") },
    )

    private fun bullets(r: SummaryResult, area: String = "Performance Goals"): List<String> =
        r.summary.goalAreas.single { it.name == area }.achievements.map { it.bullet }

    @Test
    fun `empty overrides is a no-op`() {
        val r = result(listOf("A", "B"))
        assertThat(applyOverrides(r, SummaryOverrides())).isEqualTo(r)
    }

    @Test
    fun `summaryKey normalizes case, whitespace and trailing punctuation`() {
        assertThat(summaryKey("  Shipped the  Thing. ")).isEqualTo(summaryKey("shipped the thing"))
        assertThat(summaryKey("Won the deal!")).isEqualTo(summaryKey("won the deal"))
    }

    @Test
    fun `delete suppresses a pointer even when the model re-emits the same text`() {
        val ov = SummaryOverrides(deleted = listOf(summaryKey("Fixed the bug")))
        val out = applyOverrides(result(listOf("Fixed the bug.", "Shipped X")), ov)
        assertThat(bullets(out)).containsExactly("Shipped X")
    }

    @Test
    fun `edit replaces a pointer's wording`() {
        val ov = SummaryOverrides(edits = listOf(BulletEdit("Shipped X", "Shipped X, cut load 40%")))
        assertThat(bullets(applyOverrides(result(listOf("Shipped X")), ov)))
            .containsExactly("Shipped X, cut load 40%")
    }

    @Test
    fun `chained edits resolve to the final text`() {
        val ov = SummaryOverrides(edits = listOf(BulletEdit("A", "B"), BulletEdit("B", "C")))
        assertThat(bullets(applyOverrides(result(listOf("A")), ov))).containsExactly("C")
    }

    @Test
    fun `restore injects the note into the chosen area and drops it from set-aside`() {
        val ov = SummaryOverrides(restored = listOf(RestoredNote("Mentored two juniors", "Performance Goals")))
        val out = applyOverrides(result(achievements = listOf("Shipped X"), setAside = listOf("Mentored two juniors")), ov)
        assertThat(bullets(out)).containsExactly("Shipped X", "Mentored two juniors")
        assertThat(out.setAside).isEmpty()
    }

    @Test
    fun `restore into a missing area creates it`() {
        val ov = SummaryOverrides(restored = listOf(RestoredNote("Led a workshop", "Learning & Growth")))
        val out = applyOverrides(result(achievements = listOf("Shipped X")), ov)
        assertThat(out.summary.goalAreas.map { it.name }).contains("Learning & Growth")
        assertThat(bullets(out, "Learning & Growth")).containsExactly("Led a workshop")
    }

    @Test
    fun `restore then edit the restored line does not duplicate it`() {
        val ov = SummaryOverrides(
            restored = listOf(RestoredNote("Mentored juniors", "Performance Goals")),
            edits = listOf(BulletEdit("Mentored juniors", "Mentored 2 juniors weekly")),
        )
        val out = applyOverrides(result(achievements = listOf("Shipped X"), setAside = listOf("Mentored juniors")), ov)
        assertThat(bullets(out)).containsExactly("Shipped X", "Mentored 2 juniors weekly")
    }

    @Test
    fun `applying twice is idempotent`() {
        val ov = SummaryOverrides(
            deleted = listOf(summaryKey("B")),
            edits = listOf(BulletEdit("A", "A2")),
            restored = listOf(RestoredNote("R", "Performance Goals")),
        )
        val r = result(achievements = listOf("A", "B", "C"), setAside = listOf("R"))
        val once = applyOverrides(r, ov)
        val twice = applyOverrides(once, ov)
        assertThat(twice).isEqualTo(once)
    }

    @Test
    fun `a non-matching edit leaves bullets unchanged`() {
        val ov = SummaryOverrides(edits = listOf(BulletEdit("nonexistent", "x")))
        assertThat(bullets(applyOverrides(result(listOf("A")), ov))).containsExactly("A")
    }

    @Test
    fun `delete removes a rolled-up routine line by its type when the bullet is blank`() {
        val r = SummaryResult(
            summary = SummaryBody(
                goalAreas = listOf(
                    SummaryGoalArea(
                        name = "Performance Goals",
                        rolledUp = listOf(
                            SummaryRolledUp(bullet = "", routineType = "access requests", count = 12),
                            SummaryRolledUp(bullet = "Closed tickets", routineType = "tickets", count = 5),
                        ),
                    ),
                ),
            ),
        )
        val ov = SummaryOverrides(deleted = listOf(summaryKey("access requests"), summaryKey("Closed tickets")))
        val out = applyOverrides(r, ov).summary.goalAreas.single()
        assertThat(out.rolledUp).isEmpty()
    }

    @Test
    fun `edit rewrites a rolled-up routine line`() {
        val r = SummaryResult(
            summary = SummaryBody(
                goalAreas = listOf(
                    SummaryGoalArea(
                        name = "Performance Goals",
                        rolledUp = listOf(SummaryRolledUp(bullet = "", routineType = "access requests", count = 12)),
                    ),
                ),
            ),
        )
        val ov = SummaryOverrides(edits = listOf(BulletEdit("access requests", "Access provisioning")))
        val out = applyOverrides(r, ov).summary.goalAreas.single().rolledUp.single()
        assertThat(out.bullet).isEqualTo("Access provisioning")
        assertThat(out.count).isEqualTo(12)
    }

    @Test
    fun `edit and delete apply to behaviour evidence and development lines`() {
        val r = SummaryResult(
            summary = SummaryBody(
                behaviours = listOf(SummaryBehaviour(name = "Leadership & Behaviours", evidence = listOf("Ran standup", "Coached peers"))),
                development = listOf("Learned Kotlin", "Read a book"),
            ),
        )
        val ov = SummaryOverrides(
            deleted = listOf(summaryKey("Ran standup"), summaryKey("Read a book")),
            edits = listOf(BulletEdit("Coached peers", "Coached 3 peers"), BulletEdit("Learned Kotlin", "Learned Kotlin & coroutines")),
        )
        val out = applyOverrides(r, ov)
        assertThat(out.summary.behaviours.single().evidence).containsExactly("Coached 3 peers")
        assertThat(out.summary.development).containsExactly("Learned Kotlin & coroutines")
    }

    @Test
    fun `edit and delete apply to nested competency evidence and drop an emptied competency`() {
        val r = SummaryResult(
            summary = SummaryBody(
                behaviours = listOf(
                    SummaryBehaviour(
                        name = "Leadership",
                        competencies = listOf(
                            SummaryCompetency("Set the Agenda", listOf("Defined the roadmap", "Set priorities")),
                            SummaryCompetency("Do It The Right Way", listOf("Fixed a compliance gap")),
                        ),
                    ),
                ),
            ),
        )
        val ov = SummaryOverrides(
            deleted = listOf(summaryKey("Fixed a compliance gap")),
            edits = listOf(BulletEdit("Set priorities", "Set clear quarterly priorities")),
        )
        val out = applyOverrides(r, ov).summary.behaviours.single()
        // "Do It The Right Way" lost its only evidence → the competency is dropped entirely.
        assertThat(out.competencies.map { it.name }).containsExactly("Set the Agenda")
        assertThat(out.competencies.single().evidence).containsExactly("Defined the roadmap", "Set clear quarterly priorities")
    }
}
