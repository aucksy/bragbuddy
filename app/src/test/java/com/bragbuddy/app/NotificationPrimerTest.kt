package com.bragbuddy.app

import com.bragbuddy.app.notification.NotificationPrimer
import com.bragbuddy.app.notification.NotificationPrimer.Decision
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Unit tests for the Phase 3 first-run notification-primer decision (pure, Android-free). Covers the
 * full SDK × granted × handled matrix so the "when do we show the rationale popup" rule can't regress.
 */
class NotificationPrimerTest {

    private val TIRAMISU = NotificationPrimer.MIN_RUNTIME_PERMISSION_SDK // 33
    private val PRE_13 = 32
    private val LATEST = 35

    // ---------------- Already handled → always NONE ----------------

    @Test
    fun `handled means never show, regardless of sdk or grant`() {
        assertThat(NotificationPrimer.decide(LATEST, alreadyGranted = false, handled = true)).isEqualTo(Decision.NONE)
        assertThat(NotificationPrimer.decide(LATEST, alreadyGranted = true, handled = true)).isEqualTo(Decision.NONE)
        assertThat(NotificationPrimer.decide(PRE_13, alreadyGranted = false, handled = true)).isEqualTo(Decision.NONE)
    }

    // ---------------- Fresh, Android 13+, not granted → SHOW ----------------

    @Test
    fun `fresh install on android 13+ without the grant shows the popup`() {
        assertThat(NotificationPrimer.decide(TIRAMISU, alreadyGranted = false, handled = false)).isEqualTo(Decision.SHOW)
        assertThat(NotificationPrimer.decide(LATEST, alreadyGranted = false, handled = false)).isEqualTo(Decision.SHOW)
    }

    // ---------------- Nothing to ask (pre-13 OR already granted) → MARK_HANDLED silently ----------------

    @Test
    fun `below android 13 there is nothing to ask so it self-marks handled`() {
        assertThat(NotificationPrimer.decide(PRE_13, alreadyGranted = false, handled = false)).isEqualTo(Decision.MARK_HANDLED)
        // 26 (minSdk) — the oldest device we ship to.
        assertThat(NotificationPrimer.decide(26, alreadyGranted = false, handled = false)).isEqualTo(Decision.MARK_HANDLED)
    }

    @Test
    fun `an upgrader who already granted is auto-satisfied without a popup`() {
        assertThat(NotificationPrimer.decide(TIRAMISU, alreadyGranted = true, handled = false)).isEqualTo(Decision.MARK_HANDLED)
        assertThat(NotificationPrimer.decide(LATEST, alreadyGranted = true, handled = false)).isEqualTo(Decision.MARK_HANDLED)
    }

    // ---------------- The exact boundary ----------------

    @Test
    fun `sdk 32 self-marks but 33 shows (the tiramisu boundary)`() {
        assertThat(NotificationPrimer.decide(32, alreadyGranted = false, handled = false)).isEqualTo(Decision.MARK_HANDLED)
        assertThat(NotificationPrimer.decide(33, alreadyGranted = false, handled = false)).isEqualTo(Decision.SHOW)
    }
}
