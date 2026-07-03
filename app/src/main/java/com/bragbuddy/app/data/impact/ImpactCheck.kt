package com.bragbuddy.app.data.impact

/**
 * A **local, no-AI** check for whether a work note already mentions a measurable value. Used only to
 * decide whether to show the (free, instant) "add a number" coaching nudge — it makes **no network
 * or LLM call**, never blocks anything, and is available to every user/tier.
 *
 * Signals: any digit, a currency symbol (% ₹ $ € £), or a spelled-out number word.
 */
object ImpactCheck {
    private val DIGIT = Regex("\\d")
    private val SYMBOL = Regex("[%₹\$€£]")
    private val NUMBER_WORDS = Regex(
        "(?i)\\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|" +
            "dozen|dozens|couple|hundred|thousand|million|billion)\\b",
    )

    /** True if the text already contains a number / measurable value. Cheap, allocation-light, offline. */
    fun hasMeasurable(text: String): Boolean =
        DIGIT.containsMatchIn(text) || SYMBOL.containsMatchIn(text) || NUMBER_WORDS.containsMatchIn(text)
}
