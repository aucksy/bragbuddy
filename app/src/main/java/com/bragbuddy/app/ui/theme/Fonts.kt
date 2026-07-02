@file:OptIn(ExperimentalTextApi::class)

package com.bragbuddy.app.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bragbuddy.app.R

/**
 * The three bundled type families from the Design System (§3 · Typography):
 *  - Bricolage Grotesque — display & headings (600–700)
 *  - Plus Jakarta Sans   — UI, body & captions (400–700/800)
 *  - JetBrains Mono       — monospaced (tokens, numeric detail)
 *
 * Each is a single **variable** OFL font (from google/fonts). We register the weights we use as
 * variation settings; variable-weight application requires API 26+, which is our minSdk.
 */
private fun variable(resId: Int, weight: FontWeight): Font =
    Font(
        resId = resId,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

val BricolageGrotesque = FontFamily(
    variable(R.font.bricolage_grotesque, FontWeight.Normal),
    variable(R.font.bricolage_grotesque, FontWeight.Medium),
    variable(R.font.bricolage_grotesque, FontWeight.SemiBold),
    variable(R.font.bricolage_grotesque, FontWeight.Bold),
    variable(R.font.bricolage_grotesque, FontWeight.ExtraBold),
)

val PlusJakartaSans = FontFamily(
    variable(R.font.plus_jakarta_sans, FontWeight.Normal),
    variable(R.font.plus_jakarta_sans, FontWeight.Medium),
    variable(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variable(R.font.plus_jakarta_sans, FontWeight.Bold),
    variable(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

val JetBrainsMono = FontFamily(
    variable(R.font.jetbrains_mono, FontWeight.Normal),
    variable(R.font.jetbrains_mono, FontWeight.Medium),
    variable(R.font.jetbrains_mono, FontWeight.SemiBold),
)
