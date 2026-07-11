package com.bragbuddy.app

import com.bragbuddy.app.data.ai.AiPrompts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Phase AI-0 — the prompt-drift gate. The eval harness (`eval/run.mjs`) measures the canonical
 * prompt texts in `eval/prompts/*.txt`; the app ships the texts baked into [AiPrompts]. This test
 * asserts they are the SAME words, so the eval and the app provably test identical prompts.
 * Editing one without the other = red CI (this runs in both Android workflows' unit-test step).
 *
 * Mechanism: each `.txt` file holds the raw template (placeholders intact). We call the real
 * [AiPrompts] builder with sentinel values equal to the placeholders themselves — `replace()` then
 * maps each placeholder onto itself — so the builder output IS the template, byte-for-byte (after
 * line-ending normalization). A builder-side fallback (e.g. blank role → "(not set)") is exercised
 * where a sentinel can't ride through (the summary's pinned list).
 */
class PromptSyncTest {

    // ---------------- PART A · categorizer ----------------

    @Test
    fun `categorizer prompt matches eval-prompts categorizer_txt`() {
        val expected = promptFile("categorizer.txt")
        val actual = AiPrompts.categorizer(
            today = "{{TODAY}}",
            framework = "{{APPRAISAL_FRAMEWORK}}",
            projects = listOf("{{PROJECTS}}"),
            role = "{{ROLE}}",
            projectAnchor = "{{PROJECT_ANCHOR}}",
            combineSingle = false,
        )
        assertEquals(expected, normalize(actual))
    }

    @Test
    fun `combine mode appendix matches eval-prompts categorizer-combine_txt`() {
        val base = AiPrompts.categorizer(
            today = "{{TODAY}}",
            framework = "{{APPRAISAL_FRAMEWORK}}",
            projects = listOf("{{PROJECTS}}"),
            role = "{{ROLE}}",
            projectAnchor = "{{PROJECT_ANCHOR}}",
            combineSingle = false,
        )
        val combined = AiPrompts.categorizer(
            today = "{{TODAY}}",
            framework = "{{APPRAISAL_FRAMEWORK}}",
            projects = listOf("{{PROJECTS}}"),
            role = "{{ROLE}}",
            projectAnchor = "{{PROJECT_ANCHOR}}",
            combineSingle = true,
        )
        // The file stores the appendix without its leading blank lines; it rides as base + "\n\n" + appendix.
        val expected = normalize(base) + "\n\n" + promptFile("categorizer-combine.txt")
        assertEquals(expected, normalize(combined))
    }

    // ---------------- PART B · summary ----------------

    @Test
    fun `summary prompt matches eval-prompts summary_txt`() {
        // The pinned builder prefixes "- " per item, so a sentinel can't survive verbatim — assert
        // through the empty-list fallback instead (the template file keeps {{PINNED}}).
        val expected = promptFile("summary.txt").replace("{{PINNED}}", "(none)")
        val actual = AiPrompts.summary(
            period = "{{PERIOD}}",
            lengthCap = "{{LENGTH_CAP}}",
            framework = "{{APPRAISAL_FRAMEWORK}}",
            pinned = emptyList(),
            rollup = "{{ROLLUP}}",
            role = "{{ROLE}}",
        )
        assertEquals(expected, normalize(actual))
    }

    // ---------------- Impact coach ----------------

    @Test
    fun `impact coach prompt matches eval-prompts impact-coach_txt`() {
        val expected = promptFile("impact-coach.txt")
        val actual = AiPrompts.impactCoach(
            bullet = "{{BULLET}}",
            project = "{{PROJECT}}",
            projectDetail = "{{PROJECT_DETAIL}}",
            goalArea = "{{GOAL_AREA}}",
            role = "{{ROLE}}",
        )
        assertEquals(expected, normalize(actual))
    }

    // ---------------- plumbing ----------------

    /** Read `eval/prompts/<name>`, normalized. Unit tests run with the app module as the working
     *  directory, so the repo root is one hop up — but walk a few levels to stay robust. */
    private fun promptFile(name: String): String {
        var dir: Path? = Paths.get("").toAbsolutePath()
        var levels = 0
        while (dir != null && levels < 6) {
            val candidate = dir.resolve("eval").resolve("prompts").resolve(name)
            if (Files.isRegularFile(candidate)) {
                return normalize(String(Files.readAllBytes(candidate), StandardCharsets.UTF_8))
            }
            dir = dir.parent
            levels++
        }
        throw AssertionError(
            "eval/prompts/$name not found walking up from ${Paths.get("").toAbsolutePath()} — " +
                "did the eval prompt dump move?",
        )
    }

    /** CRLF→LF plus trailing-newline trim, so git/editor line endings can never fail the sync. */
    private fun normalize(s: String): String = s.replace("\r\n", "\n").trimEnd('\n')
}
