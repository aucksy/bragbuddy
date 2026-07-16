package com.bragbuddy.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bragbuddy.app.ui.common.LocalBottomBarInset
import com.bragbuddy.app.ui.common.rememberDiscardGuard
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * The Phase 4 **"Add impact"** sheet — reached from the Home impact card. Shows the win, an AI
 * project-aware question about what to quantify (loading → the question, or a generic fallback), and a
 * type-only field for the number/result. On Add the number is merged into the bullet (the AI never
 * invents it — it comes from here). A custom scrim + Column sheet (never a Material ModalBottomSheet —
 * the veto-freeze rule), matching the app's other sheets; the [rememberDiscardGuard] protects a
 * half-typed number from Back / scrim.
 */
@Composable
fun AddImpactSheet(
    bullet: String,
    suggestion: HomeViewModel.ImpactSuggestUi?,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val noRipple = remember { MutableInteractionSource() }
    var text by remember { mutableStateOf("") }
    val requestDismiss = rememberDiscardGuard(
        dirty = text.isNotBlank(),
        onDismiss = onDismiss,
        message = "This impact hasn't been added yet.",
    )
    val canAdd = text.isNotBlank()

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
                .clickable(interactionSource = noRipple, indication = null, onClick = requestDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp)
                    .clip(RoundedCornerShape(3.dp)).background(palette.text3.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(palette.primarySoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.TrendingUp, null, tint = palette.primary, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(Spacing.s3))
                Text(
                    "Add impact",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text1,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(Spacing.s3))
            // The win being strengthened (context).
            Text(
                "“${bullet.trim()}”",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text3,
            )

            Spacer(Modifier.height(Spacing.s3))
            // The project-aware coaching question (or a spinner while it loads).
            SuggestionBox(suggestion, palette)

            Spacer(Modifier.height(Spacing.s3))
            ImpactField(value = text, onValueChange = { text = it }, palette = palette)

            Spacer(Modifier.height(Spacing.s4))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (canAdd) palette.primary else palette.primary.copy(alpha = 0.4f))
                    .clickable(enabled = canAdd) { onAdd(text.trim()) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Add impact", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(Spacing.s2))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).clickable(onClick = requestDismiss).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Not now", style = MaterialTheme.typography.labelLarge, color = palette.text3, fontWeight = FontWeight.SemiBold)
            }

            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // + the app's own bottom bar/FAB, which MainScaffold draws OVER the Home tab.
            Spacer(Modifier.height(16.dp + bottomInset + LocalBottomBarInset.current))
        }
    }
}

@Composable
private fun SuggestionBox(suggestion: HomeViewModel.ImpactSuggestUi?, palette: BragPalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(palette.primarySoft)
            .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (suggestion) {
            null, HomeViewModel.ImpactSuggestUi.Loading -> {
                CircularProgressIndicator(color = palette.primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(Spacing.s3))
                Text(
                    "Thinking about what to quantify…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text2,
                )
            }
            is HomeViewModel.ImpactSuggestUi.Ready -> {
                if (suggestion.isAi) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = palette.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Spacing.s3))
                }
                Text(
                    suggestion.question,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text1,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ImpactField(value: String, onValueChange: (String) -> Unit, palette: BragPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(Radii.md))
            .border(1.dp, palette.border, RoundedCornerShape(Radii.md))
            .background(palette.surface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text("e.g. cut drop-off 18%", style = MaterialTheme.typography.bodyMedium, color = palette.text3)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            // Short by nature, but allow a phrase to wrap and scroll inside the field (v0.21.2 rule).
            singleLine = false,
            maxLines = 3,
            textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 15.sp)),
            cursorBrush = SolidColor(palette.primary),
        )
    }
}
