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
}
