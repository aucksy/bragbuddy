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
 * A **deliverable** heading inside an expanded project (v0.33.0) — the third level, rendered as a
 * grouping rather than another collapse step (owner's call: Home already costs two taps to reach a
 * bullet, and a third would bury the record).
 *
 * An ACTIVE one is [collapsible]=false: its wins are simply listed under it. A **done** one is
 * collapsible and starts closed — it never hides its entries, it just stops competing with live work.
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
        Text(
            // A done one always states its count, since its wins are collapsed behind the row — without
            // it the row would read as though the work vanished.
            if (group.done) "Done · ${group.entryCount}" else "${group.entryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
            maxLines = 1,
        )
        Spacer(Modifier.size(6.dp))
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
