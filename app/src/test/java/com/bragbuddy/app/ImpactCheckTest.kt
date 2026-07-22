package com.bragbuddy.app

import com.bragbuddy.app.data.impact.ImpactCheck
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The local, no-AI "does this note have a number?" check that gates the impact nudge. */
class ImpactCheckTest {

    @Test
    fun `detects digits, percentages, currency and time`() {
        assertThat(ImpactCheck.hasMeasurable("Cut import time by 30%")).isTrue()
        assertThat(ImpactCheck.hasMeasurable("Saved ₹5000 in vendor costs")).isTrue()
        assertThat(ImpactCheck.hasMeasurable("Reduced build to 4 minutes")).isTrue()
        assertThat(ImpactCheck.hasMeasurable("Closed \$20k of pipeline")).isTrue()
    }

    @Test
    fun `detects spelled-out number words`() {
        assertThat(ImpactCheck.hasMeasurable("Mentored two juniors to their first ship")).isTrue()
        assertThat(ImpactCheck.hasMeasurable("Cleared a dozen access requests")).isTrue()
        assertThat(ImpactCheck.hasMeasurable("Unblocked a couple of teams")).isTrue()
    }

    @Test
    fun `no number returns false`() {
        assertThat(ImpactCheck.hasMeasurable("Helped the team with the migration")).isFalse()
        assertThat(ImpactCheck.hasMeasurable("Shipped the onboarding redesign")).isFalse()
        assertThat(ImpactCheck.hasMeasurable("")).isFalse()
    }

    @Test
    fun `number words match only on word boundaries (no false positives)`() {
        // "someone" contains "one", "tension" contains "ten" — must NOT count.
        assertThat(ImpactCheck.hasMeasurable("Someone from tension-free ops helped out")).isFalse()
    }

    @Test
    fun `impact angle detects change verbs and result connectives`() {
        assertThat(ImpactCheck.hasImpactAngle("Improved retention across the funnel")).isTrue()
        assertThat(ImpactCheck.hasImpactAngle("Cut vendor spend this quarter")).isTrue()
        assertThat(ImpactCheck.hasImpactAngle("Reduced the build queue")).isTrue()
        assertThat(ImpactCheck.hasImpactAngle("Took onboarding from days to hours")).isTrue()
        assertThat(ImpactCheck.hasImpactAngle("The cleanup led to fewer escalations")).isTrue()
        assertThat(ImpactCheck.hasImpactAngle("Made checkout faster for repeat buyers")).isTrue()
    }

    @Test
    fun `impact angle stays quiet on plain activity notes`() {
        assertThat(ImpactCheck.hasImpactAngle("Shipped the onboarding redesign")).isFalse()
        assertThat(ImpactCheck.hasImpactAngle("Ran a workshop for the team")).isFalse()
        assertThat(ImpactCheck.hasImpactAngle("Attended the platform sync")).isFalse()
        assertThat(ImpactCheck.hasImpactAngle("")).isFalse()
    }

    @Test
    fun `impact angle matches only on word boundaries`() {
        // "executed" contains "cut", "haircut" ends in "cut" mid-word — must NOT count.
        assertThat(ImpactCheck.hasImpactAngle("Executed the haircut booking flow")).isFalse()
    }
}
