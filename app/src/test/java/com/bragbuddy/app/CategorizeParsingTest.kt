package com.bragbuddy.app

import com.bragbuddy.app.data.ai.AiJson
import com.bragbuddy.app.data.ai.CategorizeResult
import com.bragbuddy.app.data.ai.FrameworkRefineResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Guards the fragile part of Phase 2: turning a free model's (often messy) reply into typed entries.
 * The compiler is the CI gate, but these also run in CI (`testDebugUnitTest`) and lock the
 * extraction + lenient-decode behaviour the "never lose an entry" rule leans on.
 */
class CategorizeParsingTest {

    @Test
    fun `extracts the JSON object out of prose and code fences`() {
        val reply = """
            Sure! Here is the JSON:
            ```json
            { "entries": [] }
            ```
            Hope that helps.
        """.trimIndent()
        assertThat(AiJson.extractObject(reply)).isEqualTo("""{ "entries": [] }""")
    }

    @Test
    fun `parses a two-entry reply with a routine and an extra item`() {
        val reply = """
            { "entries": [
              { "bullet": "Cleared a dozen SharePoint access requests.",
                "project": "SharePoint Request System", "goalCategory": "Performance Goals",
                "demonstrates": [], "isExtra": false, "impact": 0.2,
                "routine": true, "routineType": "access requests", "confidence": 0.9 },
              { "bullet": "Led a cross-team session that unblocked three teams on the Raven migration.",
                "project": "Raven Migration", "goalCategory": "Performance Goals",
                "demonstrates": ["Leadership Behaviours"], "isExtra": true, "impact": 0.85,
                "routine": false, "confidence": 0.85 }
            ] }
        """.trimIndent()

        val result = AiJson.parse(reply, CategorizeResult.serializer())
        assertThat(result.entries).hasSize(2)
        val routine = result.entries[0]
        assertThat(routine.routine).isTrue()
        assertThat(routine.routineType).isEqualTo("access requests")
        val extra = result.entries[1]
        assertThat(extra.isExtra).isTrue()
        assertThat(extra.demonstrates).containsExactly("Leadership Behaviours")
        assertThat(extra.confidence).isWithin(1e-6).of(0.85)
    }

    @Test
    fun `a missing placement field defaults to Inbox rather than failing the parse`() {
        // Model omitted "project" and "goalCategory" — should still decode, defaulted to Inbox.
        val reply = """{ "entries": [ { "bullet": "Did a thing.", "confidence": 0.9 } ] }"""
        val entry = AiJson.parse(reply, CategorizeResult.serializer()).entries.single()
        assertThat(entry.project).isEqualTo("Inbox")
        assertThat(entry.goalCategory).isEqualTo("Inbox")
    }

    @Test
    fun `empty result parses to no entries`() {
        val entries = AiJson.parse("""{ "entries": [] }""", CategorizeResult.serializer()).entries
        assertThat(entries).isEmpty()
    }

    @Test
    fun `parses refined framework pillars`() {
        val reply = """
            Here you go:
            { "pillars": [
              { "name": "Delivery Goals", "kind": "GOAL_AREA", "blurb": "What you shipped." },
              { "name": "Collaboration", "kind": "BEHAVIOUR", "blurb": "How you work with others." }
            ] }
        """.trimIndent()
        val pillars = AiJson.parse(reply, FrameworkRefineResult.serializer()).pillars
        assertThat(pillars.map { it.name }).containsExactly("Delivery Goals", "Collaboration").inOrder()
        assertThat(pillars[0].kind).isEqualTo("GOAL_AREA")
    }

    @Test
    fun `no JSON object in the reply throws`() {
        try {
            AiJson.extractObject("the model said something unhelpful")
            assertThat(false).isTrue() // should not reach
        } catch (e: IllegalArgumentException) {
            assertThat(e).hasMessageThat().contains("No JSON object")
        }
    }
}
