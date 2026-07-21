package com.bragbuddy.app

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.FrameworkPresets
import com.bragbuddy.app.data.framework.PillarKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Unit tests for the static framework templates (VISION-FIT §4 B2). */
class FrameworkPresetsTest {

    @Test
    fun `preset ids are unique`() {
        val ids = FrameworkPresets.ALL.map { it.id }
        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `every preset is internally valid`() {
        FrameworkPresets.ALL.forEach { preset ->
            assertThat(preset.title).isNotEmpty()
            assertThat(preset.tagline).isNotEmpty()
            assertThat(preset.pillars).isNotEmpty()
            // Pillar ids and names must be unique within a preset (the store keys on id; the
            // categorizer + folder namespace key on name).
            assertThat(preset.pillars.map { it.id }).containsNoDuplicates()
            assertThat(preset.pillars.map { it.name.lowercase() }).containsNoDuplicates()
            preset.pillars.forEach { p ->
                assertThat(p.name).isNotEmpty()
                assertThat(p.blurb).isNotEmpty()
            }
            // Every preset needs at least one goal area — the categorizer's placement universe —
            // and at least one behaviour so the "how" axis is never absent.
            assertThat(preset.pillars.count { it.kind == PillarKind.GOAL_AREA }).isAtLeast(1)
            assertThat(preset.pillars.count { it.kind == PillarKind.BEHAVIOUR }).isAtLeast(1)
        }
    }

    @Test
    fun `the balanced preset IS the shipped default`() {
        val balanced = FrameworkPresets.ALL.first { it.id == "balanced" }
        assertThat(balanced.pillars).isEqualTo(Framework.DEFAULT.pillars)
    }
}
