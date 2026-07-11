package com.bragbuddy.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/** How many candidate rows the card shows inline before a "+N more" tail (keeps the card compact). */
private const val IMPACT_INLINE_CAP = 6

/**
 * The Phase 4 **"Add impact"** card — a collapsible Home card listing filed wins that would be stronger
 * with a number. Collapsed by default (a count + "Show"); expands inline to the candidate list, each row
 * opening the project-aware add-impact sheet. Dismissible for the session. Built from tokens in the
 * existing Home-card style (reliability / daily-nudge); not a separate Design-System screen.
 */
@Composable
fun ImpactCard(
    entries: List<EntryEntity>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAddImpact: (EntryEntity) -> Unit,
    onDismiss: () -> Unit,
    palette: BragPalette,
) {
    val count = entries.size
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(palette.primarySoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.TrendingUp, null, tint = palette.primary, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.size(Spacing.s3))
            // Tapping the title area toggles the list (kept separate from the ✕ so neither swallows the other).
            Column(Modifier.weight(1f).clickable(onClick = onToggle)) {
                Text(
                    "Make ${count} ${if (count == 1) "win" else "wins"} stronger",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text1,
                )
                Text(
                    "Add a number to show the impact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    if (expanded) "Hide" else "Show",
                    tint = palette.text2,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "Dismiss", tint = palette.text3, modifier = Modifier.size(16.dp)) }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(Spacing.s3))
                entries.take(IMPACT_INLINE_CAP).forEach { entry ->
                    ImpactRow(entry = entry, onClick = { onAddImpact(entry) }, palette = palette)
                    Spacer(Modifier.height(Spacing.s2))
                }
                if (count > IMPACT_INLINE_CAP) {
                    Text(
                        "+ ${count - IMPACT_INLINE_CAP} more — strengthen these first",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text3,
                        modifier = Modifier.padding(top = 2.dp, start = 2.dp),
                    )
                }
            }
        }
    }
}

/** One candidate row: the bullet + an "Add" affordance; the whole row opens the add-impact sheet. */
@Composable
private fun ImpactRow(entry: EntryEntity, onClick: () -> Unit, palette: BragPalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(palette.surface2)
            .clickable(onClick = onClick)
            .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.bullet.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = palette.text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.s2))
        Row(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(palette.primarySoft)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Add, null, tint = palette.primary, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("Add", style = MaterialTheme.typography.labelSmall, color = palette.primary, fontWeight = FontWeight.Bold)
        }
    }
}
