package com.bragbuddy.app

import com.bragbuddy.app.data.ai.AiPrompts
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Guards the runtime injection into the baked prompts (a placeholder typo would silently break AI). */
class AiPromptsTest {

    @Test
    fun `categorizer SYSTEM injects framework, projects and routine labels, leaving no placeholders`() {
        val prompt = AiPrompts.categorizerSystem(
            framework = "GOAL AREAS:\n- Performance Goals: delivery",
            projects = listOf("- Atlas [Performance Goals] — redesign"),
            role = "Product Owner",
            routineTypes = listOf("access requests", "support tickets"),
        )
        assertThat(prompt).contains("Product Owner")
        assertThat(prompt).contains("Performance Goals")
        assertThat(prompt).contains("- Atlas [Performance Goals] — redesign")
        assertThat(prompt).contains("- access requests")
        assertThat(prompt).contains("- support tickets")
        assertThat(prompt).doesNotContain("{{")
        assertThat(prompt).contains("JSON") // Groq JSON mode requires the word to appear
    }

    @Test
    fun `categorizer SYSTEM handles empty framework, projects and routine labels gracefully`() {
        val prompt = AiPrompts.categorizerSystem(framework = "", projects = emptyList())
        assertThat(prompt).contains("(none set)") // empty framework
        assertThat(prompt).contains("(none yet)") // empty projects AND empty routine labels
        assertThat(prompt).contains("(not set)")  // role unset
        assertThat(prompt).doesNotContain("{{")
    }

    @Test
    fun `categorizer USER carries date, anchor and transcript, leaving no placeholders`() {
        val prompt = AiPrompts.categorizerUser(
            today = "2026-07-03",
            projectAnchor = "Raven Migration",
            transcript = "  signed off two more markets  ",
        )
        assertThat(prompt).contains("2026-07-03")
        assertThat(prompt).contains("Raven Migration")
        assertThat(prompt).contains("signed off two more markets")
        assertThat(prompt).doesNotContain("{{")
    }

    @Test
    fun `categorizer USER falls back to none when there is no anchor`() {
        val prompt = AiPrompts.categorizerUser(today = "2026-07-03", projectAnchor = null, transcript = "did a thing")
        assertThat(prompt).contains("Project anchor for this note: none")
    }

    @Test
    fun `combine mode adds the single-entry merge directive to the system prompt, default does not`() {
        val plain = AiPrompts.categorizerSystem(framework = "", projects = emptyList())
        assertThat(plain).doesNotContain("COMBINE MODE")

        val combined = AiPrompts.categorizerSystem(framework = "", projects = emptyList(), combineSingle = true)
        assertThat(combined).contains("COMBINE MODE")
        assertThat(combined).contains("EXACTLY ONE entry")
        assertThat(combined).doesNotContain("{{")
    }

    @Test
    fun `summary injects the role`() {
        val prompt = AiPrompts.summary(
            period = "full-year", lengthCap = "", framework = "", pinned = emptyList(), rollup = "",
            role = "Backend Engineer",
        )
        assertThat(prompt).contains("Backend Engineer")
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
