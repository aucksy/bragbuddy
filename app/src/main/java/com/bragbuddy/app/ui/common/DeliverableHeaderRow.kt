package com.bragbuddy.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bragbuddy.app.ui.home.DeliverableGroup
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii

/**
 * A **deliverable** heading inside an expanded project (v0.33.0) — the third level.
 *
 * Since v0.40.3 EVERY deliverable is [collapsible] (the owner reversed the v0.33.0 "no third tap"
 * call); only the DEFAULT differs by lifecycle: an ACTIVE one starts open, a **done** one starts
 * closed — neither ever hides its entries for good, a closed one just stops competing with live work.
 * The ⋮ carries the lifecycle (rename / mark done / delete); it mirrors `EntryBulletRow.BulletMenu`,
 * which is the in-repo precedent for a per-row menu (the Design System specs none — flagged, as in
 * v0.31.0).
 *
 * Lives in `ui/common` alongside [EntryBulletRow], and for the same reason: Home and the deep pillar
 * view both render this level, and two copies would drift into two different-looking records.
 */
@Composable
fun DeliverableHeader(
    group: DeliverableGroup,
    hue: Color,
    palette: BragPalette,
    expanded: Boolean,
    collapsible: Boolean,
    onToggle: () -> Unit,
    onAddEntry: () -> Unit,
    onRename: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    // Keyed on the group, not left positional. Callers wrap each group in `key(name)` so a reorder moves
    // the whole slot and an open menu follows its own row — this is the backstop for anywhere that
    // doesn't: if this slot is ever handed a DIFFERENT deliverable, the menu closes instead of silently
    // re-pointing its Delete at it (the v0.33.0 stability assessment's async-reorder path).
    var menuOpen by remember(group.name) { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .then(if (collapsible) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (group.done) {
                Icon(Icons.Outlined.CheckCircle, null, tint = palette.text3, modifier = Modifier.size(13.dp))
            } else {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(hue))
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            group.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (group.done) palette.text3 else palette.text1,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // ONE weighted child, and no trailing `Spacer(weight(1f))`. A weighted child's max width is
            // its SHARE of the free space (`fill = false` only relaxes the minimum), so a weighted name
            // beside a weighted Spacer split the row 50/50 — the name ellipsised at half width with
            // blank space sitting next to it, which is the one thing this row exists to show (found in
            // the v0.33.0 stability assessment). Row measures unweighted children first, so the count,
            // the chevron and the ⋮ each reserve their width; `fill = true` (the default) then lets the
            // name claim all the remaining space and push them to the right edge — no Spacer needed.
            modifier = Modifier.weight(1f),
        )
        // The gap belongs BEFORE the count, not after: the name takes all the free width, so a long one
        // ellipsises right up against whatever follows it ("Merchant onboardi…12") unless something
        // separates them. The chevron/⋮ side already has the touch box's own padding.
        Spacer(Modifier.size(6.dp))
        Text(
            // A done one always states its count, since its wins are collapsed behind the row — without
            // it the row would read as though the work vanished.
            if (group.done) "Done · ${group.entryCount}" else "${group.entryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
            maxLines = 1,
        )
        if (collapsible) {
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                if (expanded) "Collapse" else "Expand",
                tint = palette.text3,
                modifier = Modifier.size(16.dp),
            )
        }
        Box {
            Box(
                // 40dp, not the icon's 15: this menu holds Delete, and a target under the ~48dp
                // guidance is one mis-tap away from an action the user didn't mean. The glyph stays
                // small; only the touchable box grows.
                Modifier.size(40.dp).clip(RoundedCornerShape(999.dp)).clickable { menuOpen = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.MoreVert, "More", tint = palette.text3, modifier = Modifier.size(15.dp)) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Add entry") },
                    onClick = { menuOpen = false; onAddEntry() },
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menuOpen = false; onRename() },
                )
                DropdownMenuItem(
                    text = { Text(if (group.done) "Mark active" else "Mark done") },
                    onClick = { menuOpen = false; onToggleDone() },
                )
                DropdownMenuItem(
                    // Names what survives, because the word "delete" next to a list of wins reads like
                    // it takes them with it — it doesn't, and nobody should have to find that out.
                    text = { Text("Delete (keeps entries)") },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/**
 * Shown when an expanded deliverable holds nothing — otherwise opening one does visibly nothing, which
 * reads as broken rather than empty. Lives here beside [DeliverableHeader] for the same reason: Home and
 * the pillar view both render this level, and two copies drift into two different-looking records.
 */
@Composable
fun EmptyDeliverableNote(palette: BragPalette) {
    Text(
        "Nothing logged here yet",
        style = MaterialTheme.typography.bodySmall,
        color = palette.text3,
        modifier = Modifier.padding(start = 22.dp),
    )
}

/**
 * The **deliverable-level impact hint** — one quiet, tappable line under a deliverable's wins:
 * "[count] wins here could be stronger — add a number". It replaces the retired app-wide Home
 * counter card (which summed every log in the record); the unit of coaching is now ONE deliverable,
 * and the line isn't rendered at all once that deliverable's wins collectively carry a number and an
 * impact angle (see `ImpactCandidates.hintFor`). Indent matches [EmptyDeliverableNote], so it reads
 * as part of the group.
 */
@Composable
fun DeliverableImpactHint(count: Int, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(start = 22.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.TrendingUp, null, tint = palette.primary, modifier = Modifier.size(13.dp))
        Spacer(Modifier.size(5.dp))
        Text(
            "$count ${if (count == 1) "win" else "wins"} here could be stronger — add a number",
            style = MaterialTheme.typography.labelMedium,
            color = palette.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
