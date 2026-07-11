package com.bragbuddy.app

import com.bragbuddy.app.data.entry.FrameworkPrompt
import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.ProjectEntity
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The daily categorizer block: GOAL AREAS render **names only** (their detail feeds the summary, not
 * daily filing — Phase B2b), while BEHAVIOUR / DEVELOPMENT pillars render **name + blurb** (AI-1) so
 * the model can tag work to the behaviour it genuinely evidences. Sub-folder names ride along as
 * context under any category.
 */
class FrameworkPromptTest {

    private val framework = Framework(
        listOf(
            Pillar("perf", "Performance Goals", PillarKind.GOAL_AREA, blurb = "SECRET_GOAL_BLURB delivery objectives"),
            Pillar("lead", "Leadership & Behaviours", PillarKind.BEHAVIOUR, blurb = "SECRET_BEHAVIOUR_BLURB how you worked"),
            Pillar("grow", "Learning & Growth", PillarKind.DEVELOPMENT, blurb = "SECRET_DEV_BLURB skills"),
        ),
    )

    private fun project(name: String, area: String) =
        ProjectEntity(name = name, goalArea = area, description = "desc", createdAt = 0L)

    @Test
    fun `categorizer block keeps names, drops the goal-area blurb, keeps behaviour and development blurbs`() {
        val block = FrameworkPrompt.categorizerBlock(
            framework,
            listOf(project("Raven Migration", "Performance Goals")),
        )
        // Names present for all three axes (behaviours must stay so work can be tagged to them).
        assertThat(block).contains("Performance Goals")
        assertThat(block).contains("Leadership & Behaviours")
        assertThat(block).contains("Learning & Growth")
        // GOAL-AREA blurb stays summary-only (never in daily filing).
        assertThat(block).doesNotContain("SECRET_GOAL_BLURB")
        // AI-1: behaviour + development blurbs ARE injected so the model tags behaviours accurately.
        assertThat(block).contains("SECRET_BEHAVIOUR_BLURB")
        assertThat(block).contains("SECRET_DEV_BLURB")
    }

    @Test
    fun `sub-folder names ride along as context under their category`() {
        val block = FrameworkPrompt.categorizerBlock(
            framework,
            listOf(
                project("Raven Migration", "Performance Goals"),
                project("SharePoint System", "Performance Goals"),
            ),
        )
        assertThat(block).contains("Raven Migration")
        assertThat(block).contains("SharePoint System")
        assertThat(block).contains("projects:")
    }

    @Test
    fun `development axis is omitted when the framework has none`() {
        val block = FrameworkPrompt.categorizerBlock(
            Framework(
                listOf(
                    Pillar("perf", "Performance Goals", PillarKind.GOAL_AREA, blurb = "x"),
                    Pillar("lead", "Leadership", PillarKind.BEHAVIOUR, blurb = "y"),
                ),
            ),
            emptyList(),
        )
        assertThat(block).doesNotContain("DEVELOPMENT")
        assertThat(block).contains("GOAL AREAS")
        assertThat(block).contains("BEHAVIOURS")
    }
}
