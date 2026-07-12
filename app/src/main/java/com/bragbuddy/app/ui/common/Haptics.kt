package com.bragbuddy.app.ui.common

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView

/**
 * Fire a crisp `KEYBOARD_TAP` haptic on finger-**DOWN** — the house cross-app rule (a haptic on
 * release feels laggy; Gboard-style crispness comes from firing the instant the finger lands). Uses
 * [android.view.View.performHapticFeedback] (never a raw Vibrator), so it honours the system's haptic
 * setting for free.
 *
 * Layer this **alongside** the element's own `.clickable { }` (order doesn't matter): it observes the
 * down via [awaitFirstDown] with `requireUnconsumed = false` and never consumes it, so the click still
 * lands normally. Apply to capture / save / toggle affordances — the primary interaction surfaces.
 */
fun Modifier.tapHaptic(enabled: Boolean = true): Modifier =
    if (!enabled) this else composed {
        val view = LocalView.current
        pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
