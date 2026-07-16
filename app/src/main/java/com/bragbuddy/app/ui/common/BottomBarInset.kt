package com.bragbuddy.app.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Height of [com.bragbuddy.app.ui.main.MainScaffold]'s bottom tab bar **plus** the raised "+" FAB that
 * straddles its top edge — EXCLUDING the system navigation-bar inset (the bar pads that itself).
 */
val BottomBarHeight = 74.dp

/**
 * How much of the screen bottom is covered by the app's OWN bottom bar + FAB.
 *
 * `MainScaffold` lays the tab content out **full-screen** and draws the bar and FAB **on top of it**
 * (later siblings in the same `Box`). So anything a tab screen anchors to the bottom — every
 * custom-scrim bottom sheet, and any full-screen editor's scroll tail — sits BEHIND the bar and is
 * unreachable unless it reserves this much space **on top of** the system navigation-bar inset.
 * (That's the bug where the Generate-summary sheet's primary button was clipped by the tab bar.)
 *
 * Provided as [BottomBarHeight] around the tab content ONLY. It stays **0.dp** everywhere else — the
 * capture activity, onboarding, and pushed routes have no bottom bar — so a sheet shared between a
 * tab and a pushed route (e.g. [com.bragbuddy.app.ui.entry.EntryDetailSheet]) renders correctly in
 * both without threading a parameter through every call site.
 *
 * Rule of thumb: a bottom-anchored surface's trailing spacer is
 * `<gap> + <systemNavInset> + LocalBottomBarInset.current`.
 */
val LocalBottomBarInset = staticCompositionLocalOf { 0.dp }
