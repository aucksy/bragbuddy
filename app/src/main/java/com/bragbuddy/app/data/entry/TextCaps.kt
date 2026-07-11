package com.bragbuddy.app.data.entry

/**
 * Bounds the free-text details that ride along every AI call (AI-1 · description caps). A project's
 * description is injected into the categorizer on **every** entry, and into the impact coach on every
 * nudge — an unbounded scanned job-description would bloat each call (and the cached prefix) for no
 * gain. Pure so it can be unit-tested away from the pipeline.
 */
object TextCaps {
    /** The cap for a project/category detail injected into a prompt (AI-1). */
    const val DESCRIPTION_MAX = 300

    /**
     * Truncate [text] to about [max] characters (plus a trailing ellipsis when anything was dropped),
     * cutting on a **word boundary**. Returns the input unchanged (trimmed) when it already fits.
     */
    fun cap(text: String, max: Int = DESCRIPTION_MAX): String {
        val t = text.trim()
        if (t.length <= max) return t
        // Reserve room for the ellipsis, then back up to the last whitespace so we never cut mid-word.
        val hard = t.take(max).trimEnd()
        val lastSpace = hard.lastIndexOf(' ')
        val body = if (lastSpace >= max / 2) hard.substring(0, lastSpace) else hard
        return body.trimEnd().trimEnd(',', ';', '.', ':', '-') + "…"
    }
}
