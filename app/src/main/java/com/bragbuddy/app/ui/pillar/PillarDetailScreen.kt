package com.bragbuddy.app.ui.pillar

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.ui.capture.CaptureActivity
import com.bragbuddy.app.ui.common.EntryBulletRow
import com.bragbuddy.app.ui.home.OUTSIDE_PROJECT_LABEL
import com.bragbuddy.app.ui.home.ProjectBullets
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing
import com.bragbuddy.app.ui.theme.pillarColor

/**
 * The deep pillar view — the depth that lives "one tap in" from the Home overview. For a goal /
 * growth pillar it lists projects with their dated bullets (add-entry per project, add-project);
 * for a behaviour pillar it lists the entries that evidence it. Per-entry edit / redo / delete lives
 * here now (Home is a clean overview). Adding an entry to a project anchors the capture to it
 * ([CaptureActivity.EXTRA_PROJECT]) so no spoken prefix is needed.
 */
@Composable
fun PillarDetailScreen(
    onBack: () -> Unit,
    viewModel: PillarDetailViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val hue = pillarColor(detail.colorIndex)

    var editTarget by remember { mutableStateOf<EntryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<EntryEntity?>(null) }
    var showAddProject by remember { mutableStateOf(false) }

    // Collapsible groups (default collapsed). Selection mode forces everything open so bullets are
    // reachable to select.
    val expandedProjects = remember { mutableStateListOf<String>() }
    var evidenceExpanded by remember { mutableStateOf(false) }
    fun toggleProject(name: String) { if (expandedProjects.contains(name)) expandedProjects.remove(name) else expandedProjects.add(name) }

    // Multi-select bulk delete.
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

    // Drop from the selection any entry that has since left this pillar (edited/re-filed elsewhere),
    // so the count and checkboxes never go stale.
    val presentIds = (detail.projects.flatMap { it.entries } + detail.evidence).map { it.id }.toSet()
    if (selectionMode) {
        selected.retainAll(presentIds)
        if (selected.isEmpty()) selectionMode = false
    }

    fun capture(project: String?) {
        val intent = Intent(context, CaptureActivity::class.java)
        if (project != null) intent.putExtra(CaptureActivity.EXTRA_PROJECT, project)
        context.startActivity(intent)
    }
    fun redo(entry: EntryEntity) {
        context.startActivity(
            Intent(context, CaptureActivity::class.java).putExtra(CaptureActivity.EXTRA_REPLACE_ID, entry.id),
        )
    }

    Column(
        Modifier.fillMaxSize().background(palette.bg).statusBarsPadding().padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.s2))
        if (selectionMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { exitSelection() }) {
                    Icon(Icons.Outlined.Close, "Cancel selection", tint = palette.text2)
                }
                Spacer(Modifier.size(Spacing.s1))
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = palette.text2)
                }
                Spacer(Modifier.size(Spacing.s1))
                Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(hue.solid))
                Spacer(Modifier.size(Spacing.s2))
                Text(
                    detail.name.ifBlank { "Pillar" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = palette.text1,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s3))

        if (!detail.found) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This pillar no longer exists.", color = palette.text3, style = MaterialTheme.typography.bodyMedium)
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            contentPadding = PaddingValues(bottom = Spacing.s12),
        ) {
            if (detail.singleFolder) {
                // Scoped to one folder: the header already names it; show which pillar it rolls up to.
                if (detail.blurb.isNotBlank()) {
                    item(key = "folder-context") {
                        Text(
                            "in ${detail.blurb}",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.text3,
                        )
                    }
                }
            } else {
                item(key = "about") {
                    Column {
                        Text(
                            "ABOUT THIS PILLAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.text3,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(Spacing.s1))
                        Text(
                            detail.blurb.ifBlank { "What this pillar covers." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.text2,
                        )
                    }
                }
            }

            if (detail.isBehaviour) {
                if (detail.evidence.isEmpty()) {
                    item(key = "beh-empty") { PillarEmpty("No evidence yet. Log work that shows this and it lands here.", palette) }
                } else {
                    val show = selectionMode || evidenceExpanded
                    item(key = "evidence") {
                        Column {
                            GroupHeader("Evidence", detail.evidence.size, show, palette) { evidenceExpanded = !evidenceExpanded }
                            AnimatedVisibility(visible = show) {
                                Column(
                                    Modifier.padding(top = Spacing.s2),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                                ) {
                                    detail.evidence.forEach { entry ->
                                        EntryBulletRow(
                                            entry = entry,
                                            hue = hue.solid,
                                            showFromProject = true,
                                            selectionMode = selectionMode,
                                            isSelected = selected.contains(entry.id),
                                            onToggleSelect = { toggle(entry.id) },
                                            onLongPress = { enterSelection(entry.id) },
                                            onEdit = { editTarget = entry },
                                            onRedo = { redo(entry) },
                                            onDelete = { deleteTarget = entry },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item(key = "add-note") {
                    AddRow("Add a note", palette) { capture(null) }
                }
            } else if (detail.singleFolder) {
                // Scoped to one folder ("See more" from Home): entries directly, no folder headers.
                val proj = detail.projects.firstOrNull()
                val entries = proj?.entries.orEmpty()
                if (entries.isEmpty()) {
                    item(key = "folder-empty") { PillarEmpty("No entries here yet.", palette) }
                } else {
                    item(key = "folder-entries") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                            entries.forEach { entry ->
                                EntryBulletRow(
                                    entry = entry,
                                    hue = hue.solid,
                                    showFromProject = false,
                                    selectionMode = selectionMode,
                                    isSelected = selected.contains(entry.id),
                                    onToggleSelect = { toggle(entry.id) },
                                    onLongPress = { enterSelection(entry.id) },
                                    onEdit = { editTarget = entry },
                                    onRedo = { redo(entry) },
                                    onDelete = { deleteTarget = entry },
                                )
                            }
                        }
                    }
                }
                if (!detail.synthetic) {
                    item(key = "add-folder-entry") {
                        val outside = proj?.isOutside == true
                        AddRow(if (outside) "Add a note" else "Add entry to ${detail.name}", palette) {
                            capture(if (outside) null else detail.name)
                        }
                    }
                }
            } else {
                detail.projects.forEach { project ->
                    val show = selectionMode || expandedProjects.contains(project.name)
                    item(key = "proj-" + project.name) {
                        Column {
                            ProjectHeader(project, show, palette) { toggleProject(project.name) }
                            AnimatedVisibility(visible = show) {
                                Column(
                                    Modifier.padding(top = Spacing.s2),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                                ) {
                                    project.entries.forEach { entry ->
                                        EntryBulletRow(
                                            entry = entry,
                                            hue = hue.solid,
                                            showFromProject = false,
                                            selectionMode = selectionMode,
                                            isSelected = selected.contains(entry.id),
                                            onToggleSelect = { toggle(entry.id) },
                                            onLongPress = { enterSelection(entry.id) },
                                            onEdit = { editTarget = entry },
                                            onRedo = { redo(entry) },
                                            onDelete = { deleteTarget = entry },
                                        )
                                    }
                                    if (!project.isOutside) {
                                        AddRow("Add entry to ${project.name}", palette) { capture(project.name) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!detail.synthetic) {
                    item(key = "add-project") { AddRow("Add project", palette) { showAddProject = true } }
                    item(key = "add-detail") { AddRow("Add a detail to this pillar", palette) { capture(null) } }
                }
            }
        }
    }

    editTarget?.let { target ->
        EditEntryDialog(
            initial = target.bullet?.takeIf { it.isNotBlank() } ?: target.rawTranscript,
            palette = palette,
            onSave = { viewModel.editText(target.id, it); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(target.id); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            title = { Text("Delete this entry?", color = palette.text1) },
            text = { Text("This removes it for good. The record can't get it back.", color = palette.text3) },
            containerColor = palette.surface,
        )
    }
    if (showAddProject) {
        AddProjectDialog(
            palette = palette,
            onConfirm = { viewModel.createFolder(it); showAddProject = false },
            onDismiss = { showAddProject = false },
        )
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

@Composable
private fun ProjectHeader(project: ProjectBullets, expanded: Boolean, palette: BragPalette, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onToggle).padding(top = Spacing.s2, bottom = 2.dp),
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp))
                .background(if (project.isOutside) palette.surface2 else palette.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Folder,
                null,
                tint = if (project.isOutside) palette.text3 else palette.primary,
                modifier = Modifier.size(15.dp),
            )
        }
        Spacer(Modifier.size(Spacing.s2))
        Text(
            if (project.isOutside) OUTSIDE_PROJECT_LABEL else project.name,
            style = MaterialTheme.typography.titleSmall,
            color = palette.text1,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${project.entryCount}",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
        )
        Spacer(Modifier.size(Spacing.s2))
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            if (expanded) "Collapse" else "Expand",
            tint = palette.text3,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun GroupHeader(text: String, count: Int, expanded: Boolean, palette: BragPalette, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onToggle).padding(top = Spacing.s1, bottom = 2.dp),
    ) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
        Spacer(Modifier.size(Spacing.s2))
        Text("· $count", style = MaterialTheme.typography.labelSmall, color = palette.text3)
        Spacer(Modifier.weight(1f))
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            if (expanded) "Collapse" else "Expand",
            tint = palette.text3,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun PillarEmpty(text: String, palette: BragPalette) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.text3, modifier = Modifier.padding(vertical = Spacing.s3))
}

@Composable
private fun AddRow(text: String, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .border(1.dp, palette.primary.copy(alpha = 0.35f), RoundedCornerShape(Radii.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.card, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = palette.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = palette.primary, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun EditEntryDialog(initial: String, palette: BragPalette, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save & re-file") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit entry", color = palette.text1) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        },
        containerColor = palette.surface,
    )
}

@Composable
private fun AddProjectDialog(palette: BragPalette, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New project folder", color = palette.text1) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("e.g. Raven Migration", color = palette.text3) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        containerColor = palette.surface,
    )
}
