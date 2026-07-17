package com.bragbuddy.app

import com.bragbuddy.app.data.entry.DeliverableGuess
import com.bragbuddy.app.data.entry.DeliverableRef
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the v0.34.0 categorizer deliverable guess.
 *
 * ⭐ These pin the rule the deliverables work kept re-learning the hard way: **an anchor is made of
 * NAMES, and a name is not an identity.** A deliverable is unique by `(name, project, goalArea)`, so a
 * guess is only ever accepted with all three — and the (project, goalArea) used must be the ones the
 * row is actually FILED under.
 */
class DeliverableGuessTest {

    private val universe = listOf(
        DeliverableRef("Market rollout", "Raven Migration", "Performance Goals"),
        DeliverableRef("Defect triage", "Raven Migration", "Performance Goals"),
        // The SAME deliverable name, under a same-named project in a DIFFERENT category. This pair is
        // the whole reason identity is a triple: "Phase 1" alone cannot say which of these is meant.
        DeliverableRef("Phase 1", "Payments", "Performance Goals"),
        DeliverableRef("Phase 1", "Payments", "Learning & Growth"),
    )

    private fun resolve(guess: String?, project: String?, area: String? = "Performance Goals") =
        DeliverableGuess.resolve(guess, project, area, universe)

    @Test
    fun `a real deliverable of the filed project resolves`() {
        assertThat(resolve("Market rollout", "Raven Migration")).isEqualTo("Market rollout")
    }

    @Test
    fun `matching is case- and whitespace-insensitive and returns the STORED casing`() {
        // The model echoing "market  rollout" must land on the user's row, not create a near-twin tag
        // that every editor (all of which scope by exact name) would then be blind to.
        assertThat(resolve("  market  rollout ", "raven migration")).isEqualTo("Market rollout")
    }

    @Test
    fun `a deliverable of ANOTHER project is never borrowed`() {
        // "Defect triage" is real — but not under this project. A name is not an identity.
        assertThat(resolve("Defect triage", "SharePoint Request System")).isNull()
    }

    @Test
    fun `the goal area disambiguates two same-named deliverables under same-named projects`() {
        assertThat(resolve("Phase 1", "Payments", "Performance Goals")).isEqualTo("Phase 1")
        assertThat(resolve("Phase 1", "Payments", "Learning & Growth")).isEqualTo("Phase 1")
        // ...and a category that has no such (project, deliverable) pair resolves to nothing rather
        // than falling back to "some Phase 1" — the exact guess this whole design exists to remove.
        assertThat(resolve("Phase 1", "Payments", "Leadership & Behaviours")).isNull()
    }

    @Test
    fun `an invented deliverable resolves to nothing`() {
        assertThat(resolve("Legacy decommission", "Raven Migration")).isNull()
    }

    @Test
    fun `omitted, blank or partial input resolves to nothing`() {
        assertThat(resolve(null, "Raven Migration")).isNull()
        assertThat(resolve("   ", "Raven Migration")).isNull()
        assertThat(resolve("Market rollout", null)).isNull()
        assertThat(resolve("Market rollout", "Raven Migration", null)).isNull()
        assertThat(resolve("Market rollout", "Raven Migration", "  ")).isNull()
    }

    @Test
    fun `the placement sentinels are not projects, so nothing nests under them`() {
        assertThat(resolve("Market rollout", "Outside-project")).isNull()
        assertThat(resolve("Market rollout", "Inbox")).isNull()
    }

    @Test
    fun `an empty universe resolves to nothing`() {
        assertThat(DeliverableGuess.resolve("Market rollout", "Raven Migration", "Performance Goals", emptyList()))
            .isNull()
    }
}
