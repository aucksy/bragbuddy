package com.bragbuddy.app.ui.common

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * One dated entry bullet — shared by the deep pillar view and the Home inline folder expansion so the
 * two always look identical. Shows the cleaned bullet, cross-references (behaviours it also evidences
 * / the project it came from), the ★ Standout + metric chips, and a ⋮ menu (edit / redo / delete).
 * In [selectionMode] the ⋮ menu is replaced by a checkbox and taps toggle selection.
 */
@Composable
fun EntryBulletRow(
    entry: EntryEntity,
    hue: Color,
    showFromProject: Boolean,
    onEdit: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onTap: () -> Unit = {},
) {
    val palette = BragBuddyTheme.palette
    val date = DateUtils.getRelativeTimeSpanString(
        entry.occurredAt ?: entry.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(if (isSelected) palette.primarySoft else palette.surface)
            .border(1.dp, if (isSelected) palette.primary.copy(alpha = 0.5f) else palette.border, RoundedCornerShape(Radii.lg))
            .combinedClickable(
                onClick = { if (selectionMode) onToggleSelect() else onTap() },
                onLongClick = onLongPress,
            )
            .padding(start = Spacing.card, top = Spacing.card, bottom = Spacing.card, end = 4.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).clip(RoundedCornerShape(2.dp)).background(hue))
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(
                entry.bullet?.takeIf { it.isNotBlank() } ?: entry.rawTranscript,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text1,
            )
            // Cross-references: under a project, note the behaviours it also evidences; under a
            // behaviour, note the project it came from.
            if (showFromProject) {
                entry.project?.takeIf { it.isNotBlank() && !it.equals("Outside-project", true) && !it.equals("Inbox", true) }
                    ?.let {
                        Spacer(Modifier.height(3.dp))
                        Text("from $it", style = MaterialTheme.typography.bodySmall, color = palette.text3)
                    }
            } else if (entry.demonstrates.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "› also evidences ${entry.demonstrates.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(Spacing.s2))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.isExtra) {
                    var showStandout by remember { mutableStateOf(false) }
                    Text(
                        "★ Standout",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.extraInk,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radii.sm))
                            .background(palette.extraSoft)
                            .clickable { showStandout = true }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (showStandout) {
                        AlertDialog(
                            onDismissRequest = { showStandout = false },
                            confirmButton = { TextButton(onClick = { showStandout = false }) { Text("Got it") } },
                            title = { Text("Standout", color = palette.text1) },
                            text = {
                                Text(
                                    "Work beyond your normal role — mentoring, unblocking another team, an initiative you started. BragBuddy flags these so your leadership and standout wins are easy to spot at review time.",
                                    color = palette.text3,
                                )
                            },
                            containerColor = palette.surface,
                        )
                    }
                }
                entry.metric?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.text2,
                        // Bound the chip so a long metric can't devour the row and starve the date into a
                        // one-char-per-line vertical column — it shrinks + ellipsizes; the date stays whole.
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(Radii.sm)).background(palette.surface2).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(date, style = MaterialTheme.typography.bodySmall, color = palette.text3, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
        }
        if (selectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(checkedColor = palette.primary, uncheckedColor = palette.text3),
            )
        } else {
            BulletMenu(onEdit = onEdit, onRedo = onRedo, onDelete = onDelete)
        }
    }
}

@Composable
private fun BulletMenu(onEdit: () -> Unit, onRedo: () -> Unit, onDelete: () -> Unit) {
    val palette = BragBuddyTheme.palette
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Outlined.MoreVert, "More", tint = palette.text3, modifier = Modifier.size(19.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Edit text") },
                onClick = { open = false; onEdit() },
                leadingIcon = { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Redo (re-record)") },
                onClick = { open = false; onRedo() },
                leadingIcon = { Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = { open = false; onDelete() },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}
