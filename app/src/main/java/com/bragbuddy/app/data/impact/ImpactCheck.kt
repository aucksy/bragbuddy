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

    /**
     * Outcome/change wording — the "impact angle" half of the deliverable-level check (the number is
     * the other half). Verbs of change ("improved", "reduced", "saved"…), comparative results
     * ("faster", "halved", "doubled"), and result connectives ("from X to Y", "up/down by",
     * "led to", "resulting in"). Deliberately modest: this only decides whether to *stop nagging*
     * a deliverable whose story already reads as impact, so a miss shows one extra quiet hint —
     * it never blocks or invents anything.
     */
    private val IMPACT_ANGLE = Regex(
        "(?i)\\b(improv(?:ed|es|ing|ement)|reduc(?:ed|es|ing|tion)|increas(?:ed|es|ing)|" +
            "decreas(?:ed|es|ing)|sav(?:ed|es|ing|ings)|cut|grew|grow(?:n|th)|" +
            "boost(?:ed|s|ing)|accelerat(?:ed|es|ing)|faster|quicker|halv(?:ed|ing)|" +
            "doubl(?:ed|ing)|tripl(?:ed|ing)|eliminat(?:ed|es|ing)|" +
            "from\\s+\\S+\\s+to|up\\s+by|down\\s+by|led\\s+to|result(?:ed|ing|s)\\s+in|" +
            "deliver(?:ed|ing)|unlock(?:ed|ing)|enabl(?:ed|es|ing))\\b",
    )

    /** True if the text already contains a number / measurable value. Cheap, allocation-light, offline. */
    fun hasMeasurable(text: String): Boolean =
        DIGIT.containsMatchIn(text) || SYMBOL.containsMatchIn(text) || NUMBER_WORDS.containsMatchIn(text)

    /** True if the text carries outcome/change ("impact angle") wording. Same cost profile as
     *  [hasMeasurable]: local regex, no AI, offline. */
    fun hasImpactAngle(text: String): Boolean = IMPACT_ANGLE.containsMatchIn(text)
}
