package com.bragbuddy.app

import com.bragbuddy.app.data.entry.OriginalTranscript
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The v0.32.0 invariant: an entry's original words are captured once and never overwritten — the
 * protection against an edit (which is seeded from the AI's *bullet*) writing the AI's wording over
 * what the user actually said.
 */
class OriginalTranscriptTest {

    /** Convenience: what a reader would show — `originalTranscript ?: rawTranscript`. */
    private fun shown(snapshot: String?, raw: String) = snapshot ?: raw

    @Test
    fun `the first edit snapshots the words that are about to be overwritten`() {
        val next = OriginalTranscript.next(
            current = "helped raj unblock the migation thing on tuesday",
            existing = null,
            incoming = "Led the migration kickoff.",
            isRedo = false,
        )
        assertThat(next).isEqualTo("helped raj unblock the migation thing on tuesday")
    }

    @Test
    fun `a second edit keeps the FIRST words, not the first edit's text`() {
        val afterFirst = OriginalTranscript.next(
            current = "helped raj unblock the migation thing", existing = null,
            incoming = "Led the migration kickoff.", isRedo = false,
        )
        val afterSecond = OriginalTranscript.next(
            current = "Led the migration kickoff.", existing = afterFirst,
            incoming = "Led the migration kickoff, cutting handoff time.", isRedo = false,
        )
        assertThat(afterSecond).isEqualTo("helped raj unblock the migation thing")
    }

    @Test
    fun `a redo resets the snapshot so the fresh recording becomes the original`() {
        // Owner's call: a redo is starting over. Resetting is also what keeps the NEW words viewable —
        // a kept snapshot would leave the sheet showing the take the user deliberately scrapped.
        val next = OriginalTranscript.next(
            current = "garbled first take", existing = null,
            incoming = "Led the migration kickoff meeting.", isRedo = true,
        )
        assertThat(next).isNull()
        assertThat(shown(next, "Led the migration kickoff meeting.")).isEqualTo("Led the migration kickoff meeting.")
    }

    @Test
    fun `a redo of an already-edited entry still resets, and a later edit protects the redone words`() {
        val afterRedo = OriginalTranscript.next(
            current = "an old edited bullet", existing = "the very first words", incoming = "a fresh take", isRedo = true,
        )
        assertThat(afterRedo).isNull()

        val afterEdit = OriginalTranscript.next(
            current = "a fresh take", existing = afterRedo, incoming = "A polished bullet.", isRedo = false,
        )
        assertThat(afterEdit).isEqualTo("a fresh take")
    }

    @Test
    fun `an append (add-impact) snapshots NOTHING, so the added words stay visible`() {
        // An append loses nothing — the transcript keeps every word and gains the user's number. A
        // snapshot here would be actively harmful: readers show the snapshot, so it would HIDE the
        // number the user just added behind "this is what you originally said".
        val next = OriginalTranscript.next(
            current = "shipped the dashboard", existing = null,
            incoming = "shipped the dashboard cut load time 40%", isRedo = false,
        )
        assertThat(next).isNull()
        assertThat(shown(next, "shipped the dashboard cut load time 40%"))
            .isEqualTo("shipped the dashboard cut load time 40%")
    }

    @Test
    fun `an append AFTER an edit still carries the earlier original untouched`() {
        val next = OriginalTranscript.next(
            current = "Led the migration kickoff.", existing = "helped raj with the migation thing",
            incoming = "Led the migration kickoff. cut handoff time 40%", isRedo = false,
        )
        assertThat(next).isEqualTo("helped raj with the migation thing")
    }

    @Test
    fun `a rewrite that merely echoes the transcript's opening words is still protected`() {
        // Guard the boundary of the startsWith rule: a real edit is seeded from the AI's BULLET, which
        // does not begin with the transcript, so this leniency never swallows a genuine rewrite.
        assertThat(
            OriginalTranscript.next(
                current = "shipped the dashboard", existing = null,
                incoming = "Shipped the dashboard, cutting load time 40%.", isRedo = false,
            ),
        ).isEqualTo("shipped the dashboard") // capital S → a rewrite → protected
    }

    @Test
    fun `a no-op edit claims no snapshot, so the UI never reports an edit that never happened`() {
        assertThat(
            OriginalTranscript.next(
                current = "shipped the dashboard", existing = null, incoming = "shipped the dashboard", isRedo = false,
            ),
        ).isNull()
        // Whitespace-only difference is still a no-op (the caller trims, but the rule must not depend on it).
        assertThat(
            OriginalTranscript.next(
                current = "shipped the dashboard", existing = null, incoming = "  shipped the dashboard  ", isRedo = false,
            ),
        ).isNull()
    }

    @Test
    fun `an unedited entry shows its live transcript as the original`() {
        assertThat(shown(null, "shipped the dashboard")).isEqualTo("shipped the dashboard")
    }
}
