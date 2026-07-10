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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
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
 * The first-run notification-rationale popup (Phase 3). Shown once on first Home, it explains WHY
 * BragBuddy wants to post a notification BEFORE the OS permission dialog appears — replacing the old
 * naked system dialog that used to race the Welcome screen on a fresh install. [onAllow] launches the
 * real POST_NOTIFICATIONS request; [onMaybeLater] (and the scrim) is the gentle decline.
 *
 * A custom scrim + Column sheet (never a Material ModalBottomSheet — the veto-freeze rule), matching
 * the capture / catch-up / entry-detail sheets. Not in the Design System — built from tokens, in the
 * §7 catch-up sheet's style.
 */
@Composable
fun NotificationPrimerSheet(
    onAllow: () -> Unit,
    onMaybeLater: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val noRipple = remember { MutableInteractionSource() }

    Box(Modifier.fillMaxSize()) {
        // Scrim — tapping outside is the same gentle "Maybe later".
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0F1A).copy(alpha = 0.35f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onMaybeLater),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                // Cap at the screen and scroll if a large font scale grows the content past it, so the
                // grabber/icon/title can never clip off the top (the sheet is bottom-anchored).
                .verticalScroll(rememberScrollState())
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
                Icon(Icons.Outlined.NotificationsActive, null, tint = palette.primary, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Get your daily nudge",
                fontFamily = BricolageGrotesque,
                fontWeight = FontWeight.Bold,
                fontSize = 21.sp,
                lineHeight = 25.sp,
                color = palette.text1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "BragBuddy sends one quiet reminder a day so you actually log your wins — that's what " +
                    "keeps your record ready for review. Allow notifications for that daily nudge; you " +
                    "can change the time or turn it off anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primary)
                    .clickable(onClick = onAllow)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = Color.White, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Allow notifications",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Maybe later",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onMaybeLater)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            Spacer(Modifier.height(10.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}
