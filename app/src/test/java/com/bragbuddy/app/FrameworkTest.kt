package com.bragbuddy.app

import com.bragbuddy.app.data.framework.Framework
import com.bragbuddy.app.data.framework.PillarKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrameworkTest {

    @Test
    fun `default framework has the three shipped pillars, one per axis`() {
        val f = Framework.DEFAULT
        assertThat(f.goalAreas.map { it.name }).containsExactly("Performance Goals")
        assertThat(f.behaviours.map { it.name }).containsExactly("Leadership & Behaviours")
        assertThat(f.development.map { it.name }).containsExactly("Learning & Growth")
    }

    @Test
    fun `prompt block lists goal areas, behaviours and development in order`() {
        val block = Framework.DEFAULT.toPromptBlock()
        val goalIdx = block.indexOf("GOAL AREAS")
        val behaviourIdx = block.indexOf("BEHAVIOURS")
        val devIdx = block.indexOf("DEVELOPMENT")

        assertThat(goalIdx).isAtLeast(0)
        assertThat(behaviourIdx).isGreaterThan(goalIdx)
        assertThat(devIdx).isGreaterThan(behaviourIdx)
        assertThat(block).contains("Performance Goals")
    }

    @Test
    fun `development axis is optional and omitted from the block when empty`() {
        val noDev = Framework(Framework.DEFAULT.pillars.filter { it.kind != PillarKind.DEVELOPMENT })
        assertThat(noDev.toPromptBlock()).doesNotContain("DEVELOPMENT")
    }
}
