package com.bragbuddy.app.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Inbox
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * The Inbox tab (Design System §1 · "Inbox waits at the end"). Phase 3 adds **tap-to-resolve**: an
 * entry the AI wasn't sure about can be filed in one tap — a suggested project, any folder, or
 * "Outside project". FAILED entries can also be retried. Nothing is ever lost; the raw transcript
 * rides along until it's resolved.
 */
@Composable
fun InboxScreen(
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()

    val selected = remember { mutableStateListOf<Long>() }
    var selectionMode by remember { mutableStateOf(false) }
    var showBulkDelete by remember { mutableStateOf(false) }
    fun toggle(id: Long) {
        if (selected.contains(id)) selected.remove(id) else selected.add(id)
        if (selected.isEmpty()) selectionMode = false
    }
    fun enterSelection(id: Long) {
        selectionMode = true
        if (!selected.contains(id)) selected.add(id)
    }
    fun exitSelection() { selectionMode = false; selected.clear() }

    // Drop from the selection any entry that has since left the Inbox (resolved/retried elsewhere).
    val presentIds = entries.map { it.id }.toSet()
    if (selectionMode) selected.retainAll(presentIds)
    if (selectionMode && selected.isEmpty()) selectionMode = false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.s4))
        if (selectionMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { exitSelection() }) {
                    Icon(Icons.Outlined.Close, "Cancel selection", tint = palette.text2)
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    "${selected.size} selected",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text1,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { if (selected.isNotEmpty()) showBulkDelete = true }) {
                    Icon(Icons.Outlined.Delete, "Delete selected", tint = MaterialTheme.colorScheme.error)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.padding(end = 8.dp).size(9.dp).clip(RoundedCornerShape(3.dp)).background(palette.inbox),
                )
                Text("Inbox", style = MaterialTheme.typography.headlineLarge, color = palette.text1)
            }
            Text(
                "Entries I wasn't sure about wait here — file each in a tap, no capture ever interrupted.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.text3,
            )
        }
        Spacer(Modifier.height(Spacing.s3))

        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { InboxZero() }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = contentBottomPadding + Spacing.s4),
            ) {
                items(entries, key = { it.id }) { entry ->
                    InboxCard(
                        entry = entry,
                        folders = folders,
                        palette = palette,
                        selectionMode = selectionMode,
                        isSelected = selected.contains(entry.id),
                        isOnline = isOnline,
                        onToggleSelect = { toggle(entry.id) },
                        onLongPress = { enterSelection(entry.id) },
                        onRetry = { viewModel.retry(entry.id) },
                        onAssign = { viewModel.resolveToProject(entry, it) },
                        onOutside = { viewModel.resolveOutside(entry) },
                    )
                }
            }
        }
    }

    if (showBulkDelete) {
        val n = selected.size
        AlertDialog(
            onDismissRequest = { showBulkDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMany(selected.toList())
                    showBulkDelete = false
                    exitSelection()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBulkDelete = false }) { Text("Cancel") } },
            title = { Text("Delete $n ${if (n == 1) "entry" else "entries"}?", color = palette.text1) },
            text = { Text("This removes them for good. The record can't get them back.", color = palette.text3) },
            containerColor = palette.surface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InboxCard(
    entry: EntryEntity,
    folders: List<ProjectEntity>,
    palette: BragPalette,
    selectionMode: Boolean,
    isSelected: Boolean,
    isOnline: Boolean,
    onToggleSelect: () -> Unit,
    onLongPress: () -> Unit,
    onRetry: () -> Unit,
    onAssign: (String) -> Unit,
    onOutside: () -> Unit,
) {
    val headline = entry.bullet?.takeIf { it.isNotBlank() } ?: entry.rawTranscript
    val failed = entry.status == EntryStatus.FAILED
    val reason = when {
        // Calm offline copy (Phase 7): while offline, a FAILED entry isn't an error — it's waiting,
        // and OfflineRecovery retries it automatically the moment the connection returns.
        failed && !isOnline -> "Offline — will retry when you're connected"
        failed -> "Couldn't reach the AI"
        entry.suggestedProjects.isNotEmpty() -> "Not sure which project"
        else -> "Needs a home"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(if (isSelected) palette.primarySoft else palette.surface)
            .border(1.dp, if (isSelected) palette.primary.copy(alpha = 0.5f) else palette.border, RoundedCornerShape(Radii.lg))
            .combinedClickable(onClick = { if (selectionMode) onToggleSelect() }, onLongClick = onLongPress)
            .padding(Spacing.card),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.padding(top = 6.dp).size(6.dp).clip(RoundedCornerShape(2.dp)).background(palette.inbox).align(Alignment.Top),
            )
            Spacer(Modifier.size(Spacing.s3))
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text1,
                modifier = Modifier.weight(1f),
            )
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() },
                    colors = CheckboxDefaults.colors(checkedColor = palette.primary, uncheckedColor = palette.text3),
                )
            }
        }

        Spacer(Modifier.height(Spacing.s2))
        Pill(reason, palette.inboxSoft, palette.inboxInk)

        // Resolve controls hide while multi-selecting (the whole card is a selection target then).
        if (!selectionMode) {
            Spacer(Modifier.height(Spacing.s3))
            Text("File into", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.s2))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                entry.suggestedProjects.take(2).forEach { suggestion ->
                    ActionChip(suggestion, palette.primarySoft, palette.primary, onClick = { onAssign(suggestion) })
                }
                if (folders.isNotEmpty()) {
                    FolderPickerChip(folders = folders, palette = palette, onPick = onAssign)
                }
                ActionChip("Outside project", palette.surface2, palette.text2, onClick = onOutside)
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
}

@Composable
private fun FolderPickerChip(folders: List<ProjectEntity>, palette: BragPalette, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(Radii.sm))
                .background(palette.surface2)
                .clickable { open = true }
                .padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Other folder", style = MaterialTheme.typography.labelSmall, color = palette.text2, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Outlined.ExpandMore, null, tint = palette.text3, modifier = Modifier.size(15.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            folders.forEach { f ->
                DropdownMenuItem(
                    text = { Text(f.name) },
                    onClick = { open = false; onPick(f.name) },
                )
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, fill: Color, ink: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun Pill(text: String, fill: Color, ink: Color) {
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
