package com.bragbuddy.app

import com.bragbuddy.app.data.ai.CategorizeResult
import com.bragbuddy.app.data.ai.CategorizedEntry
import com.bragbuddy.app.data.entry.CategorizedNormalizer
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/** AI-1 · 1c output validator — snapping, phantom→Inbox, unknown-goal passthrough, ghost-behaviour
 *  drop, date bounds. Pure, so it runs without a device. */
class CategorizedNormalizerTest {

    private val pillars = listOf(
        Pillar("perf", "Performance Goals", PillarKind.GOAL_AREA, "delivery"),
        Pillar("lead", "Leadership & Behaviours", PillarKind.BEHAVIOUR, "how you worked"),
        Pillar("grow", "Learning & Growth", PillarKind.DEVELOPMENT, "skills"),
    )
    private val placement = listOf("Raven Migration", "SharePoint Request System")
    private val today = LocalDate.of(2026, 7, 11)

    private fun norm(vararg entries: CategorizedEntry) =
        CategorizedNormalizer.normalize(CategorizeResult(entries.toList()), placement, pillars, today).entries

    @Test
    fun `snaps a near-match project and goal to canonical casing`() {
        val e = norm(
            CategorizedEntry(
                bullet = "x", project = "raven  migration", goalCategory = "performance goals", confidence = 0.9,
            ),
        ).single()
        assertThat(e.project).isEqualTo("Raven Migration")
        assertThat(e.goalCategory).isEqualTo("Performance Goals")
        assertThat(e.confidence).isEqualTo(0.9)
    }

    @Test
    fun `a phantom project is parked in Inbox with the guess kept as a suggestion and confidence capped`() {
        val e = norm(
            CategorizedEntry(
                bullet = "x", project = "Atlas Redesign", goalCategory = "Performance Goals", confidence = 0.95,
            ),
        ).single()
        assertThat(e.project).isEqualTo("Inbox")
        assertThat(e.suggestedProjects).contains("Atlas Redesign")
        assertThat(e.confidence).isAtMost(0.5)
        // The goal it named is real, so it is preserved (only the phantom project is corrected).
        assertThat(e.goalCategory).isEqualTo("Performance Goals")
    }

    @Test
    fun `Outside-project and Inbox are preserved (not treated as phantom)`() {
        val outside = norm(CategorizedEntry(bullet = "x", project = "outside-project", confidence = 0.7)).single()
        assertThat(outside.project).isEqualTo("Outside-project")
        assertThat(outside.confidence).isEqualTo(0.7)

        val inbox = norm(CategorizedEntry(bullet = "x", project = "INBOX", confidence = 0.4)).single()
        assertThat(inbox.project).isEqualTo("Inbox")
    }

    @Test
    fun `an unknown goal category is left verbatim (Uncategorized guarantee)`() {
        val e = norm(
            CategorizedEntry(bullet = "x", project = "Raven Migration", goalCategory = "Some Retired Area", confidence = 0.8),
        ).single()
        assertThat(e.goalCategory).isEqualTo("Some Retired Area")
    }

    @Test
    fun `ghost behaviours are dropped and real ones snapped to canonical`() {
        val e = norm(
            CategorizedEntry(
                bullet = "x", project = "Raven Migration", goalCategory = "Performance Goals",
                demonstrates = listOf("leadership & behaviours", "Set the Agenda", "Learning & Growth"),
                confidence = 0.8,
            ),
        ).single()
        // "Set the Agenda" is not a BEHAVIOUR pillar → dropped; "Learning & Growth" is DEVELOPMENT, not a
        // behaviour → dropped; only the canonical behaviour survives.
        assertThat(e.demonstrates).containsExactly("Leadership & Behaviours")
    }

    @Test
    fun `implausible dates are rejected, plausible ones kept`() {
        val future = norm(
            CategorizedEntry(bullet = "x", project = "Raven Migration", goalCategory = "Performance Goals", dateMentioned = "2026-07-20", confidence = 0.8),
        ).single()
        assertThat(future.dateMentioned).isNull() // after today → rejected

        val ancient = norm(
            CategorizedEntry(bullet = "x", project = "Raven Migration", goalCategory = "Performance Goals", dateMentioned = "2024-01-01", confidence = 0.8),
        ).single()
        assertThat(ancient.dateMentioned).isNull() // > 370 days past → rejected

        val ok = norm(
            CategorizedEntry(bullet = "x", project = "Raven Migration", goalCategory = "Performance Goals", dateMentioned = "2026-07-10", confidence = 0.8),
        ).single()
        assertThat(ok.dateMentioned).isEqualTo("2026-07-10") // yesterday → kept
    }
}
