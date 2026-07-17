package com.bragbuddy.app.data.entry

/**
 * The one rule deciding what an entry's [com.bragbuddy.app.data.local.EntryEntity.originalTranscript]
 * becomes when its text is about to change (v0.32.0). Pure, so the rule is unit-testable on its own —
 * [EntryProcessor] needs the DAO/AI/mutex, and this is the part that must never quietly regress.
 *
 * The invariant: **the user's own words are captured once and never overwritten.** An edit seeds its
 * editor from the AI's polished *bullet*, so without this the edit would write the AI's wording over
 * what the user actually said — losing exactly the detail the record exists to hold months later.
 */
object OriginalTranscript {

    /**
     * The value to store, given the row's [current] transcript, its [existing] snapshot (null = never
     * mutated yet), the [incoming] replacement text, and whether this is a [isRedo].
     *
     * Null means "no snapshot" — the row's transcript IS the original, so readers use
     * `originalTranscript ?: rawTranscript`.
     */
    fun next(current: String, existing: String?, incoming: String, isRedo: Boolean): String? = when {
        // A redo is starting over: the fresh recording becomes the original and the scrapped attempt
        // is let go (owner's call, 2026-07-17). Resetting to null — rather than keeping the discarded
        // take — is also what keeps the NEW words viewable at all: the sheet shows the snapshot when
        // one exists, so a stale snapshot here would hide the words the user just spoke.
        isRedo -> null
        // Already captured → never overwrite. The second edit must still show what was FIRST said,
        // not the first edit's text.
        existing != null -> existing
        // A no-op "edit" (same text) mutates nothing, so there is nothing to preserve — and claiming a
        // snapshot would make the UI report "Edited since" for an edit that never happened.
        incoming.trim() == current.trim() -> null
        // The first real mutation: this is the original.
        else -> current
    }
}
