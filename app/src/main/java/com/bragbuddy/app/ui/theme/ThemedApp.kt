package com.bragbuddy.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bragbuddy.app.data.prefs.SettingsStore
import com.bragbuddy.app.data.prefs.ThemeMode
import com.bragbuddy.app.data.theme.ThemeSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime
import javax.inject.Inject

/** The theme inputs, distilled from settings (Phase 2 · theme). [darkMin]/[lightMin] are minutes-of-day
 *  for the AUTO switch times. */
data class ThemePrefs(val mode: ThemeMode, val darkMin: Int, val lightMin: Int)

/** Exposes the resolved [ThemePrefs] to the theme composable. Null until the first settings read lands
 *  (so the host can hold the splash and avoid a light/dark flash). Read by BOTH activities. */
@HiltViewModel
class ThemeViewModel @Inject constructor(settingsStore: SettingsStore) : ViewModel() {
    val theme: StateFlow<ThemePrefs?> = settingsStore.settings
        .map {
            ThemePrefs(
                mode = it.themeMode,
                darkMin = ThemeSchedule.minuteOfDay(it.autoDarkHour, it.autoDarkMinute),
                lightMin = ThemeSchedule.minuteOfDay(it.autoLightHour, it.autoLightMinute),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Wraps [content] in [BragBuddyTheme] with the light/dark flag resolved from the user's [ThemeMode]
 * (Phase 2 · theme) — SYSTEM / LIGHT / DARK, or AUTO switching live on the device-local schedule. Used
 * by every activity so a forced theme is consistent app-wide (Home, Capture, pushed screens).
 *
 * [onReady] fires once the theme prefs have loaded — MainActivity uses it to lift the splash screen
 * only after the theme is known, so a forced-dark user never sees a light flash. Also syncs the system
 * bars' icon contrast to the RESOLVED theme (a forced Dark theme on a light device keeps readable icons).
 *
 * [holdUntilLoaded] is for the translucent capture overlay, which can't use a splash to mask the
 * cold-start theme resolution: while prefs are still null it renders nothing (the transparent window
 * simply shows through for the frame or two before the correctly-themed surface appears) — so a forced
 * Light/Dark user never sees a one-frame flash of the system theme on that overlay.
 */
@Composable
fun BragBuddyThemedApp(
    viewModel: ThemeViewModel = hiltViewModel(),
    onReady: () -> Unit = {},
    holdUntilLoaded: Boolean = false,
    content: @Composable () -> Unit,
) {
    val prefs by viewModel.theme.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val p = prefs

    LaunchedEffect(p != null) { if (p != null) onReady() }

    // Overlay host: hold rendering until the theme is known (see [holdUntilLoaded]) — no splash to mask it.
    if (holdUntilLoaded && p == null) return

    // While prefs load (a frame or two), assume the system setting — correct for the default SYSTEM
    // mode, and the splash (MainActivity) or the hold above (overlay) covers any mismatch for a forced mode.
    val dark = if (p == null) systemDark else rememberEffectiveDark(p, systemDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    BragBuddyTheme(darkTheme = dark) { content() }
}

/** Resolve the effective dark flag; for AUTO, recompute at each schedule boundary so the theme flips
 *  live while the app is open (no alarms needed — the theme only matters while a surface is visible). */
@Composable
private fun rememberEffectiveDark(prefs: ThemePrefs, systemDark: Boolean): Boolean {
    if (prefs.mode != ThemeMode.AUTO) {
        return ThemeSchedule.resolveDark(prefs.mode, systemDark, 0, prefs.darkMin, prefs.lightMin)
    }
    var tick by remember { mutableIntStateOf(0) }
    // Fresh device clock each tick / whenever the schedule changes.
    val nowMin = remember(tick, prefs.darkMin, prefs.lightMin) {
        LocalTime.now().let { it.hour * 60 + it.minute }
    }
    LaunchedEffect(tick, prefs.darkMin, prefs.lightMin) {
        val waitMin = ThemeSchedule.minutesUntilNextSwitch(nowMin, prefs.darkMin, prefs.lightMin)
        delay(waitMin * 60_000L + 1_000L) // +1s so we land just past the boundary
        tick++
    }
    return ThemeSchedule.inDarkWindow(nowMin, prefs.darkMin, prefs.lightMin)
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
