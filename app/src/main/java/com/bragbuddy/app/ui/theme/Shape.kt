package com.bragbuddy.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Radius scale from the Design System (§4): 8 · 12 · 16 · 28 · full. */
object Radii {
    val sm = 8.dp    // chips, tags
    val md = 12.dp   // inputs, tiles
    val lg = 16.dp   // entry cards
    val xl = 28.dp   // sheets, modals
    // pill = RoundedCornerShape(percent = 50) — buttons, FAB
}

val BragShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
