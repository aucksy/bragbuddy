package com.bragbuddy.app

import com.bragbuddy.app.data.ai.AiPrompts
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Guards the runtime injection into the baked prompts (a placeholder typo would silently break AI). */
class AiPromptsTest {

    @Test
    fun `categorizer injects date, framework and projects, leaving no placeholders`() {
        val prompt = AiPrompts.categorizer(
            today = "2026-07-03",
            framework = "GOAL AREAS:\n- Performance Goals: delivery",
            projects = listOf("- Atlas [Performance Goals] — redesign"),
        )
        assertThat(prompt).contains("2026-07-03")
        assertThat(prompt).contains("Performance Goals")
        assertThat(prompt).contains("- Atlas [Performance Goals] — redesign")
        assertThat(prompt).doesNotContain("{{")
        assertThat(prompt).contains("JSON") // Groq JSON mode requires the word to appear
    }

    @Test
    fun `categorizer handles empty framework and projects gracefully`() {
        val prompt = AiPrompts.categorizer(today = "2026-07-03", framework = "", projects = emptyList())
        assertThat(prompt).contains("(none set)")
        assertThat(prompt).contains("(none yet)")
        assertThat(prompt).doesNotContain("{{")
    }

    @Test
    fun `framework refine is instruction-aware and keeps unmentioned categories`() {
        val prompt = AiPrompts.framework(
            current = "GOAL AREAS:\n- Performance Goals: delivery",
            description = "add a category for customer focus",
        )
        assertThat(prompt).contains("add a category for customer focus")
        assertThat(prompt).contains("Performance Goals")
        // The key behaviour: apply the change but keep everything else.
        assertThat(prompt).contains("KEEP every existing category")
        assertThat(prompt).doesNotContain("{{")
    }
}
