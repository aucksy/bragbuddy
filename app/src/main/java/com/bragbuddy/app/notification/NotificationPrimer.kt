package com.bragbuddy.app.notification

/**
 * The first-run notification-rationale decision (Phase 3). Pure — no Android deps — so the "when do we
 * ask for POST_NOTIFICATIONS" rule is unit-testable and lives in ONE place.
 *
 * Replaces the old naked OS permission dialog fired from `MainActivity.onCreate` (which raced the
 * Welcome screen on a fresh install). Now a custom-scrim popup on first Home explains WHY first, then
 * the popup's "Allow" button is what actually launches the OS request.
 *
 * The rule:
 *  - **Below Android 13** there is no runtime notification permission → nothing to ask; record it
 *    handled so the popup never appears and the Home reliability card is un-gated immediately.
 *  - **Already granted** (e.g. an upgrader who granted via the old launch-time request) → auto-satisfied,
 *    recorded handled silently — no popup.
 *  - **Not yet handled, Android 13+, not granted** → SHOW the rationale popup once.
 *  - **Handled** → do nothing (the flag persists across launches).
 */
object NotificationPrimer {

    /** Android 13 (Tiramisu) — the first version that gates notifications behind a runtime permission. */
    const val MIN_RUNTIME_PERMISSION_SDK = 33

    enum class Decision {
        /** Show the one-time rationale popup. */
        SHOW,

        /** Nothing to ask (pre-13 or already granted) — persist "handled" so we don't re-check. */
        MARK_HANDLED,

        /** Already handled — do nothing. */
        NONE,
    }

    fun decide(sdkInt: Int, alreadyGranted: Boolean, handled: Boolean): Decision = when {
        handled -> Decision.NONE
        sdkInt < MIN_RUNTIME_PERMISSION_SDK || alreadyGranted -> Decision.MARK_HANDLED
        else -> Decision.SHOW
    }
}
