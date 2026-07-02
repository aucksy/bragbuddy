package com.bragbuddy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Provides the extended [BragPalette] (tokens beyond Material's ColorScheme) down the tree.
 * Defaults to light; [BragBuddyTheme] always overrides it.
 */
val LocalBragPalette = staticCompositionLocalOf { LightPalette }

/**
 * BragBuddy's Material 3 theme. Dynamic colour is intentionally OFF so the design-system palette
 * is identical on every device. Follows the system dark/light setting.
 */
@Composable
fun BragBuddyTheme(
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalBragPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BragTypography,
            shapes = BragShapes,
            content = content,
        )
    }
}

/** Accessor object, e.g. `BragBuddyTheme.palette.text2`. */
object BragBuddyTheme {
    val palette: BragPalette
        @Composable @ReadOnlyComposable get() = LocalBragPalette.current
}

/** Tinted elevation shadow colour (Design System §4: rgba(20,22,50,…), never pure black). */
val ShadowColor = Color(0xFF141632)
