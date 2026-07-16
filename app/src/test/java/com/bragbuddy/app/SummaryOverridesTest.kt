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
import com.bragbuddy.app.data.summary.PlacementOverride
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

    // ---------------- placement moves (v0.31.0 · the retag) ----------------

    private fun twoAreas() = SummaryResult(
        summary = SummaryBody(
            goalAreas = listOf(
                SummaryGoalArea(
                    name = "Performance Goals",
                    achievements = listOf(SummaryAchievement(bullet = "Shipped the redesign"), SummaryAchievement(bullet = "Fixed the funnel")),
                ),
                SummaryGoalArea(name = "Learning & Growth", achievements = listOf(SummaryAchievement(bullet = "Finished the course"))),
            ),
        ),
    )

    @Test
    fun `a move relocates the line to another area and sets its project`() {
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Shipped the redesign"), "Learning & Growth", "Product Ownership")))
        val out = applyOverrides(twoAreas(), ov)
        assertThat(bullets(out, "Performance Goals")).containsExactly("Fixed the funnel")
        val dest = out.summary.goalAreas.single { it.name == "Learning & Growth" }
        assertThat(dest.achievements.map { it.bullet }).containsExactly("Finished the course", "Shipped the redesign")
        assertThat(dest.achievements.single { it.bullet == "Shipped the redesign" }.project).isEqualTo("Product Ownership")
    }

    @Test
    fun `a move to a brand-new area creates it`() {
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Shipped the redesign"), "Client Impact", null)))
        val out = applyOverrides(twoAreas(), ov)
        val dest = out.summary.goalAreas.single { it.name == "Client Impact" }
        assertThat(dest.achievements.map { it.bullet }).containsExactly("Shipped the redesign")
        assertThat(dest.achievements.single().project).isNull()
    }

    @Test
    fun `a move whose line no longer exists lapses quietly`() {
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("A line the model never wrote"), "Learning & Growth", null)))
        val out = applyOverrides(twoAreas(), ov)
        assertThat(bullets(out, "Performance Goals")).containsExactly("Shipped the redesign", "Fixed the funnel")
        assertThat(bullets(out, "Learning & Growth")).containsExactly("Finished the course")
    }

    /** Emptying an area by moving its last line out must not leave a header with nothing under it. */
    @Test
    fun `moving the last line out of an area drops the empty area`() {
        val r = SummaryResult(
            summary = SummaryBody(
                goalAreas = listOf(
                    SummaryGoalArea(name = "Performance Goals", achievements = listOf(SummaryAchievement(bullet = "Only line"))),
                    SummaryGoalArea(name = "Learning & Growth", achievements = listOf(SummaryAchievement(bullet = "Finished the course"))),
                ),
            ),
        )
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Only line"), "Learning & Growth", null)))
        val out = applyOverrides(r, ov)
        assertThat(out.summary.goalAreas.map { it.name }).containsExactly("Learning & Growth")
        assertThat(bullets(out, "Learning & Growth")).containsExactly("Finished the course", "Only line")
    }

    /** An area emptied of achievements but still holding routine tallies must SURVIVE — those lines are
     *  never dropped for length (prompt rule 2) and must not vanish because a win moved away. */
    @Test
    fun `an area keeping routine tallies survives losing its last achievement`() {
        val r = SummaryResult(
            summary = SummaryBody(
                goalAreas = listOf(
                    SummaryGoalArea(
                        name = "Performance Goals",
                        achievements = listOf(SummaryAchievement(bullet = "Only line")),
                        rolledUp = listOf(SummaryRolledUp(bullet = "", routineType = "code reviews", count = 84)),
                    ),
                    SummaryGoalArea(name = "Learning & Growth", achievements = listOf(SummaryAchievement(bullet = "Finished the course"))),
                ),
            ),
        )
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Only line"), "Learning & Growth", null)))
        val out = applyOverrides(r, ov)
        assertThat(out.summary.goalAreas.map { it.name }).containsExactly("Performance Goals", "Learning & Growth")
        assertThat(out.summary.goalAreas.single { it.name == "Performance Goals" }.rolledUp).hasSize(1)
    }

    @Test
    fun `a move is idempotent`() {
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Shipped the redesign"), "Learning & Growth", "Product Ownership")))
        val once = applyOverrides(twoAreas(), ov)
        assertThat(applyOverrides(once, ov)).isEqualTo(once)
    }

    @Test
    fun `a deleted line is not resurrected by a move`() {
        val ov = SummaryOverrides(
            deleted = listOf(summaryKey("Shipped the redesign")),
            moved = listOf(PlacementOverride(summaryKey("Shipped the redesign"), "Learning & Growth", null)),
        )
        val out = applyOverrides(twoAreas(), ov)
        assertThat(bullets(out, "Learning & Growth")).containsExactly("Finished the course")
        assertThat(out.summary.goalAreas.none { it.achievements.any { a -> a.bullet == "Shipped the redesign" } }).isTrue()
    }

    // ---------------- development-area moves (v0.31.0 · the retag-into/out-of Learning & Growth fix) ----

    private val devAreas = setOf("learning & growth")

    private fun withDevelopment() = SummaryResult(
        summary = SummaryBody(
            goalAreas = listOf(
                // Two achievements so a move of one doesn't empty (and drop) the area — that's a separate
                // behaviour, covered by `moving the last line out of an area drops the empty area`.
                SummaryGoalArea(
                    name = "Performance Goals",
                    achievements = listOf(SummaryAchievement(bullet = "Shipped the redesign"), SummaryAchievement(bullet = "Fixed the funnel")),
                ),
            ),
            development = listOf("Completed the AWS certification"),
        ),
    )

    /** Moving a win INTO a development area must land in `development` (a plain-string list), NOT create
     *  a second goal-area header duplicating the development section. */
    @Test
    fun `a move into a development area lands in development, not a duplicate goal-area header`() {
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Shipped the redesign"), "Learning & Growth", null)))
        val out = applyOverrides(withDevelopment(), ov, devAreas)
        // No goal area named after the development area (no duplicate header); the source survives.
        assertThat(out.summary.goalAreas.map { it.name }).containsExactly("Performance Goals")
        assertThat(bullets(out, "Performance Goals")).containsExactly("Fixed the funnel")
        assertThat(out.summary.development).containsExactly("Completed the AWS certification", "Shipped the redesign")
    }

    /** Moving a development line OUT to a goal area must find it in `development` and relocate it. */
    @Test
    fun `a move out of a development area relocates the development line`() {
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Completed the AWS certification"), "Performance Goals", "Certs")))
        val out = applyOverrides(withDevelopment(), ov, devAreas)
        assertThat(out.summary.development).isEmpty()
        val dest = out.summary.goalAreas.single { it.name == "Performance Goals" }
        assertThat(dest.achievements.map { it.bullet }).contains("Completed the AWS certification")
        assertThat(dest.achievements.single { it.bullet == "Completed the AWS certification" }.project).isEqualTo("Certs")
    }

    @Test
    fun `a development move is idempotent`() {
        val ov = SummaryOverrides(moved = listOf(PlacementOverride(summaryKey("Shipped the redesign"), "Learning & Growth", null)))
        val once = applyOverrides(withDevelopment(), ov, devAreas)
        assertThat(applyOverrides(once, ov, devAreas)).isEqualTo(once)
    }

    // ---------------- restore-all fuzzy de-dup (v0.31.0 · finding 4) ----

    /** Restoring several genuinely-distinct dropped items at once must NOT collapse two that merely
     *  resemble each other — the fuzzy de-dup only guards against the model's OWN rewording, never
     *  against a line injected earlier in the same restore pass. */
    @Test
    fun `restore all keeps distinct-but-similar items separate`() {
        val r = result(area = "Delivery", achievements = listOf("Existing win"))
        val ov = SummaryOverrides(
            restored = listOf(
                RestoredNote("Migrated the promo service to platform A", "Delivery"),
                RestoredNote("Migrated the billing service to platform B", "Delivery"),
            ),
        )
        val out = applyOverrides(r, ov)
        assertThat(bullets(out, "Delivery")).containsExactly(
            "Existing win",
            "Migrated the promo service to platform A",
            "Migrated the billing service to platform B",
        )
    }

    /** A restored line that the MODEL already emitted (in its own wording) is NOT injected twice. */
    @Test
    fun `restore does not duplicate the model's own rewording of the same win`() {
        val r = result(area = "Delivery", achievements = listOf("Shipped the checkout redesign to 100% of traffic"))
        val ov = SummaryOverrides(restored = listOf(RestoredNote("Shipped checkout redesign to 100% traffic", "Delivery")))
        val out = applyOverrides(r, ov)
        assertThat(bullets(out, "Delivery")).containsExactly("Shipped the checkout redesign to 100% of traffic")
    }
}
