package com.bragbuddy.app

import com.bragbuddy.app.data.entry.TextCaps
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** AI-1 · project/category detail caps — bounds the text that rides on every AI call. */
class TextCapsTest {

    @Test
    fun `short text is returned trimmed and unchanged`() {
        assertThat(TextCaps.cap("  A short detail.  ")).isEqualTo("A short detail.")
    }

    @Test
    fun `text at the limit is unchanged`() {
        val exact = "x".repeat(TextCaps.DESCRIPTION_MAX)
        assertThat(TextCaps.cap(exact)).isEqualTo(exact)
    }

    @Test
    fun `long text is truncated on a word boundary with an ellipsis`() {
        val word = "alpha " // 6 chars
        val long = word.repeat(120) // 720 chars, well over 300
        val capped = TextCaps.cap(long)
        assertThat(capped.length).isAtMost(TextCaps.DESCRIPTION_MAX + 1) // + the ellipsis char
        assertThat(capped).endsWith("…")
        // No partial word before the ellipsis: the char before "…" completes a word (here "alpha").
        assertThat(capped).endsWith("alpha…")
        assertThat(capped).doesNotContain("alph…") // never cuts mid-word
    }

    @Test
    fun `a single very long word still truncates (no word boundary to honour)`() {
        val capped = TextCaps.cap("y".repeat(500))
        assertThat(capped).endsWith("…")
        assertThat(capped.length).isAtMost(TextCaps.DESCRIPTION_MAX + 1)
    }

    @Test
    fun `respects a custom max`() {
        val capped = TextCaps.cap("one two three four five", max = 7)
        assertThat(capped).endsWith("…")
        assertThat(capped).doesNotContain("thr") // cut cleanly at a word boundary
    }
}
