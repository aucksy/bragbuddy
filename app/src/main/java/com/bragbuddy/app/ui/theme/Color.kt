package com.bragbuddy.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens transcribed verbatim from the BragBuddy Design System (§2 · Colour tokens).
 * Names mirror the `--ab-*` CSS variables so the mapping is one-to-one.
 *
 * Material 3's [ColorScheme] only carries the handful of roles Material components need; the
 * fuller palette (secondary text, hairlines, the dynamic pillar ramp, and the reserved Extra /
 * Inbox fills) lives in [BragPalette], provided through [LocalBragPalette].
 */
object Tokens {
    // Brand
    val Primary = Color(0xFF4C56D6)
    val PrimaryPress = Color(0xFF3A43BE)
    val PrimarySoft = Color(0xFFEAEBFD)

    // Surfaces — light
    val BgLight = Color(0xFFF7F7FB)
    val SurfaceLight = Color(0xFFFFFFFF)
    val Surface2Light = Color(0xFFF1F2F7)
    val BorderLight = Color(0xFFE7E8F0)

    // Text — light
    val Text1Light = Color(0xFF181A2E)
    val Text2Light = Color(0xFF5C5F76)
    val Text3Light = Color(0xFF8B8DA3)

    // Brand — dark
    val PrimaryDark = Color(0xFF8E96F2)
    val PrimaryPressDark = Color(0xFF7A82E6)
    val PrimarySoftDark = Color(0xFF23264A)

    // Surfaces — dark
    val BgDark = Color(0xFF0F1020)
    val SurfaceDark = Color(0xFF191B2E)
    val Surface2Dark = Color(0xFF21243A)
    val BorderDark = Color(0xFF2B2E47)

    // Text — dark
    val Text1Dark = Color(0xFFECEDF5)
    val Text2Dark = Color(0xFFA2A5BE)
    val Text3Dark = Color(0xFF6E7190)

    // Reserved — never assigned to a pillar
    val Extra = Color(0xFFC98A2E)
    val ExtraSoft = Color(0xFFFBEFD7)
    val ExtraInk = Color(0xFF8A5E12)
    val Inbox = Color(0xFF8A6FD1)
    val InboxSoft = Color(0xFFEEE8FB)
    val InboxInk = Color(0xFF5E3FA8)

    // Positive (saved toast / copied confirm)
    val Positive = Color(0xFF0E7A4B)
    val PositiveSoft = Color(0xFFE7F6EE)
}

/** A single pillar's on-brand hue trio (solid / soft fill / ink text). */
@Immutable
data class PillarColor(val solid: Color, val soft: Color, val ink: Color)

/**
 * The framework is variable, so pillar hues are drawn in order from this on-brand ramp
 * (Design System §2 · Pillar colours). Amber & violet are deliberately absent — they are
 * reserved for the Extra flag and the Inbox. Use [pillarColor] to pick by index.
 */
val PillarRamp: List<PillarColor> = listOf(
    PillarColor(Color(0xFF4C56D6), Color(0xFFE7E9FF), Color(0xFF2D34A6)), // 1 · indigo (primary)
    PillarColor(Color(0xFF1B9C8C), Color(0xFFDCF4F0), Color(0xFF0E6B5F)), // 2 · teal
    PillarColor(Color(0xFFC65C86), Color(0xFFFAE4EC), Color(0xFF8E3159)), // 3 · rose
    PillarColor(Color(0xFF3E86C9), Color(0xFFDFEEFA), Color(0xFF1E5C93)), // 4 · blue
    PillarColor(Color(0xFF4E9E5F), Color(0xFFE1F3E5), Color(0xFF2C6E3B)), // 5 · green
)

/** Stable per-pillar colour — wraps the ramp so an index beyond its length cycles safely. */
fun pillarColor(index: Int): PillarColor = PillarRamp[((index % PillarRamp.size) + PillarRamp.size) % PillarRamp.size]

/**
 * The full design palette for a theme, beyond Material's [ColorScheme]. Access via
 * `BragBuddyTheme.palette` inside composables.
 */
@Immutable
data class BragPalette(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val border: Color,
    val primary: Color,
    val primaryPress: Color,
    val primarySoft: Color,
    val text1: Color,
    val text2: Color,
    val text3: Color,
    val extra: Color,
    val extraSoft: Color,
    val extraInk: Color,
    val inbox: Color,
    val inboxSoft: Color,
    val inboxInk: Color,
    val positive: Color,
    val positiveSoft: Color,
    val isDark: Boolean,
)

val LightPalette = BragPalette(
    bg = Tokens.BgLight,
    surface = Tokens.SurfaceLight,
    surface2 = Tokens.Surface2Light,
    border = Tokens.BorderLight,
    primary = Tokens.Primary,
    primaryPress = Tokens.PrimaryPress,
    primarySoft = Tokens.PrimarySoft,
    text1 = Tokens.Text1Light,
    text2 = Tokens.Text2Light,
    text3 = Tokens.Text3Light,
    extra = Tokens.Extra,
    extraSoft = Tokens.ExtraSoft,
    extraInk = Tokens.ExtraInk,
    inbox = Tokens.Inbox,
    inboxSoft = Tokens.InboxSoft,
    inboxInk = Tokens.InboxInk,
    positive = Tokens.Positive,
    positiveSoft = Tokens.PositiveSoft,
    isDark = false,
)

val DarkPalette = BragPalette(
    bg = Tokens.BgDark,
    surface = Tokens.SurfaceDark,
    surface2 = Tokens.Surface2Dark,
    border = Tokens.BorderDark,
    primary = Tokens.PrimaryDark,
    primaryPress = Tokens.PrimaryPressDark,
    primarySoft = Tokens.PrimarySoftDark,
    text1 = Tokens.Text1Dark,
    text2 = Tokens.Text2Dark,
    text3 = Tokens.Text3Dark,
    // Reserved & pillar hues keep their identity in dark; softs use the dark-surface tints
    // pragmatically until per-token dark values are specified in the design files.
    extra = Tokens.Extra,
    extraSoft = Color(0xFF3A2E17),
    extraInk = Color(0xFFE9C588),
    inbox = Tokens.Inbox,
    inboxSoft = Color(0xFF2A2440),
    inboxInk = Color(0xFFC9B6F0),
    positive = Color(0xFF4FD1A0),
    positiveSoft = Color(0xFF16352A),
    isDark = true,
)

val LightColors: ColorScheme = lightColorScheme(
    primary = Tokens.Primary,
    onPrimary = Color.White,
    primaryContainer = Tokens.PrimarySoft,
    onPrimaryContainer = Tokens.Text1Light,
    background = Tokens.BgLight,
    onBackground = Tokens.Text1Light,
    surface = Tokens.SurfaceLight,
    onSurface = Tokens.Text1Light,
    surfaceVariant = Tokens.Surface2Light,
    onSurfaceVariant = Tokens.Text2Light,
    outline = Tokens.BorderLight,
    outlineVariant = Tokens.BorderLight,
    error = Color(0xFFB4453C),
    onError = Color.White,
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Tokens.PrimaryDark,
    onPrimary = Color(0xFF0F1020),
    primaryContainer = Tokens.PrimarySoftDark,
    onPrimaryContainer = Tokens.Text1Dark,
    background = Tokens.BgDark,
    onBackground = Tokens.Text1Dark,
    surface = Tokens.SurfaceDark,
    onSurface = Tokens.Text1Dark,
    surfaceVariant = Tokens.Surface2Dark,
    onSurfaceVariant = Tokens.Text2Dark,
    outline = Tokens.BorderDark,
    outlineVariant = Tokens.BorderDark,
    error = Color(0xFFE9756B),
    onError = Color(0xFF0F1020),
)
