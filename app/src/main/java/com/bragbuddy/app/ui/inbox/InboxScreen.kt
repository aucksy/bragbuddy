package com.bragbuddy.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * The Inbox tab (Design System §1 · "Inbox waits at the end"). Phase 2 is a **read + retry**
 * surface — it proves categorization routed unclear/failed entries here without losing them. The
 * one-tap resolve (assign a suggested project) lands in Phase 3.
 */
@Composable
fun InboxScreen(
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.s4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.padding(end = 8.dp).size(9.dp).clip(RoundedCornerShape(3.dp)).background(palette.inbox),
            )
            Text("Inbox", style = MaterialTheme.typography.headlineLarge, color = palette.text1)
        }
        Text(
            "Entries I wasn't sure about wait here — no capture is ever interrupted.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
        )
        Spacer(Modifier.height(Spacing.s3))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InboxZero() }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = contentBottomPadding + Spacing.s4),
            ) {
                items(entries, key = { it.id }) { InboxCard(it, onRetry = { viewModel.retry(it.id) }) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(entry: EntryEntity, onRetry: () -> Unit) {
    val palette = BragBuddyTheme.palette
    val headline = entry.bullet?.takeIf { it.isNotBlank() } ?: entry.rawTranscript
    val failed = entry.status == EntryStatus.FAILED
    val reason = when {
        failed -> "Couldn't reach the AI"
        entry.suggestedProjects.isNotEmpty() -> "Not sure which project"
        else -> "Needs a home"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
    ) {
        Row {
            Box(
                Modifier.padding(top = 6.dp).size(6.dp).clip(RoundedCornerShape(2.dp)).background(palette.inbox),
            )
            Spacer(Modifier.size(Spacing.s3))
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text1,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.s2))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(reason, palette.inboxSoft, palette.inboxInk)
            // The AI's best guesses (read-only in Phase 2; tap-to-assign is Phase 3).
            entry.suggestedProjects.take(2).forEach { Pill(it, palette.surface2, palette.text2) }
        }

        if (failed) {
            Spacer(Modifier.height(Spacing.s3))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primarySoft)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = palette.primary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.size(6.dp))
                Text("Try again", style = MaterialTheme.typography.labelLarge, color = palette.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Pill(text: String, fill: androidx.compose.ui.graphics.Color, ink: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(Radii.sm)).background(fill).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun InboxZero() {
    val palette: BragPalette = BragBuddyTheme.palette
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s6),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(palette.inboxSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Inbox, null, tint = palette.inbox, modifier = Modifier.size(30.dp)) }
        Text("Inbox zero", style = MaterialTheme.typography.titleLarge, color = palette.text1, textAlign = TextAlign.Center)
        Text(
            "Nothing waiting. Cleanly filed entries live on Home.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text3,
            textAlign = TextAlign.Center,
        )
    }
}
