package com.bragbuddy.app.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BricolageGrotesque
import com.bragbuddy.app.ui.theme.Radii

/**
 * The gentle weekly catch-up (Design System §7 · "Weekly catch-up") — one soft question on the
 * first open in the Friday-evening→Sunday window, an easy "Not this week", and it's gone. A custom
 * scrim + Column sheet (never a Material ModalBottomSheet, per the veto-freeze rule). Built to the
 * §7 mockup: calendar mark, centred title/sub, a full-width mic CTA, a quiet skip.
 */
@Composable
fun CatchupSheet(
    onAdd: () -> Unit,
    onSkip: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val noRipple = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize()) {
        // Scrim — tapping outside is the same easy "Not this week".
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0F1A).copy(alpha = 0.35f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onSkip),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.width(42.dp).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(palette.text3.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(palette.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, tint = palette.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Anything bigger this week you didn't log?",
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                color = palette.text1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "A launch, a tricky fix, a win you helped someone else get. No pressure — just checking.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primary)
                    .clickable(onClick = onAdd)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Add something",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Not this week",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(10.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}
