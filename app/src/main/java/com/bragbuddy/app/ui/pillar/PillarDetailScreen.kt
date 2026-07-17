package com.bragbuddy.app.ui.pillar

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.local.DELIVERABLE_LABEL
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.ui.capture.CaptureLauncher
import com.bragbuddy.app.ui.common.DeliverableHeader
import com.bragbuddy.app.ui.common.EmptyDeliverableNote
import com.bragbuddy.app.ui.common.EntryBulletRow
import com.bragbuddy.app.ui.common.LocalSnackbarController
import com.bragbuddy.app.ui.entry.EntryDetailSheet
import com.bragbuddy.app.ui.home.OUTSIDE_PROJECT_LABEL
import com.bragbuddy.app.ui.home.exportBehaviourBlock
import com.bragbuddy.app.ui.home.exportFolderBlock
import com.bragbuddy.app.ui.home.exportGoalBlock
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
 * here now (Home is a clean overview). Adding an entry to a project opens the 3-choice capture
 * chooser anchored to it ([CaptureLauncher.openChooser]) so no spoken prefix is needed.
 */
@Composable
fun PillarDetailScreen(
    onBack: () -> Unit,
    viewModel: PillarDetailViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbarController.current
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val deliverables by viewModel.deliverables.collectAsStateWithLifecycle()
    val hue = pillarColor(detail.colorIndex)

    var editTarget by remember { mutableStateOf<EntryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<EntryEntity?>(null) }
    var detailEntry by remember { mutableStateOf<EntryEntity?>(null) }
    var showAddProject by remember { mutableStateOf(false) }

    fun copySection() {
        val text = when {
            detail.singleFolder -> detail.projects.firstOrNull()?.let { exportFolderBlock(it) }.orEmpty()
            detail.isBehaviour -> exportBehaviourBlock(detail.name, detail.evidence)
            else -> exportGoalBlock(detail.name, detail.projects)
        }
        val hasBody = text.contains("•")
        if (!hasBody) {
            snackbar.show("Nothing to copy yet")
        } else {
            clipboard.setText(AnnotatedString(text))
            snackbar.show("Copied — paste into Word or Docs")
        }
    }

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

    // Deliverable state (v0.33.0) — only DONE ones collapse; keys are scoped "<project>::<deliverable>"
    // because a deliverable is unique by (name, project, goalArea), never by name alone.
    val expandedDeliverables = remember { mutableStateListOf<String>() }
    fun toggleDeliverable(key: String) {
        if (expandedDeliverables.contains(key)) expandedDeliverables.remove(key) else expandedDeliverables.add(key)
    }
    var createDeliverableFor by remember { mutableStateOf<DeliverableTarget?>(null) }
    var renameDeliverable by remember { mutableStateOf<DeliverableTarget?>(null) }
    var deleteDeliverable by remember { mutableStateOf<DeliverableTarget?>(null) }

    // In-context "+" (Add a note / Add entry to a project) → the 3-choice chooser, anchored if named.
    fun capture(project: String?) = CaptureLauncher.openChooser(context, project)
    /** Tap-in filing: pins the project AND the deliverable, so the AI guesses neither (v0.33.0). */
    fun captureInto(project: String, deliverable: String) =
        CaptureLauncher.openChooser(context, project, deliverable)
    fun redo(entry: EntryEntity) = CaptureLauncher.redo(context, entry.id)

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
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (detail.found) {
                    Text(
                        "Copy",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.primarySoft)
                            .clickable { copySection() }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                    )
                }
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
                                            onTap = { detailEntry = entry },
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
                // Scoped to one folder ("See more" from Home): no folder header — but the deliverable
                // grouping DOES render here. This screen exists precisely because the project outgrew
                // Home's inline cap, so it is the likeliest place to have deliverables at all; a flat
                // list here would drop the structure exactly where it matters most.
                val proj = detail.projects.firstOrNull()
                val entries = proj?.entries.orEmpty()
                if (entries.isEmpty() && proj?.deliverables.isNullOrEmpty()) {
                    item(key = "folder-empty") { PillarEmpty("No entries here yet.", palette) }
                } else if (proj != null) {
                    item(key = "folder-entries") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                            ProjectBody(
                                project = proj,
                                hue = hue.solid,
                                palette = palette,
                                selectionMode = selectionMode,
                                isSelected = { selected.contains(it) },
                                isDeliverableExpanded = { expandedDeliverables.contains("${proj.name}::$it") },
                                onToggleDeliverable = { toggleDeliverable("${proj.name}::$it") },
                                onToggleSelect = { toggle(it) },
                                onEnterSelection = { enterSelection(it) },
                                onEdit = { editTarget = it },
                                onRedo = { redo(it) },
                                onDeleteEntry = { deleteTarget = it },
                                onTapEntry = { detailEntry = it },
                                onAddEntryTo = { d -> captureInto(proj.name, d) },
                                onRenameDeliverable = { d -> renameDeliverable = DeliverableTarget(proj.name, d) },
                                onSetDeliverableDone = { d, done ->
                                    viewModel.setDeliverableDoneByName(d, proj.name, done)
                                },
                                onDeleteDeliverable = { d -> deleteDeliverable = DeliverableTarget(proj.name, d) },
                            )
                        }
                    }
                }
                if (!detail.synthetic) {
                    item(key = "add-folder-deliverable") {
                        // `proj != null` matters: the folder arg can match no group (deleted from
                        // another surface, or a restore landed while this screen was open), and
                        // `detail.name` then falls back to the raw route argument — so this would offer
                        // to create a deliverable under a project that doesn't exist, producing a row
                        // that renders nowhere (v0.33.0 assessment).
                        if (proj != null && !proj.isOutside) {
                            AddRow("Add ${DELIVERABLE_LABEL.lowercase()}", palette) {
                                createDeliverableFor = DeliverableTarget(detail.name, "")
                            }
                        }
                    }
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
                                    ProjectBody(
                                        project = project,
                                        hue = hue.solid,
                                        palette = palette,
                                        selectionMode = selectionMode,
                                        isSelected = { selected.contains(it) },
                                        isDeliverableExpanded = { expandedDeliverables.contains("${project.name}::$it") },
                                        onToggleDeliverable = { toggleDeliverable("${project.name}::$it") },
                                        onToggleSelect = { toggle(it) },
                                        onEnterSelection = { enterSelection(it) },
                                        onEdit = { editTarget = it },
                                        onRedo = { redo(it) },
                                        onDeleteEntry = { deleteTarget = it },
                                        onTapEntry = { detailEntry = it },
                                        onAddEntryTo = { d -> captureInto(project.name, d) },
                                        onRenameDeliverable = { d ->
                                            renameDeliverable = DeliverableTarget(project.name, d)
                                        },
                                        onSetDeliverableDone = { d, done ->
                                            viewModel.setDeliverableDoneByName(d, project.name, done)
                                        },
                                        onDeleteDeliverable = { d ->
                                            deleteDeliverable = DeliverableTarget(project.name, d)
                                        },
                                    )
                                    if (!project.isOutside) {
                                        AddRow("Add entry to ${project.name}", palette) { capture(project.name) }
                                        AddRow("Add ${DELIVERABLE_LABEL.lowercase()}", palette) {
                                            createDeliverableFor = DeliverableTarget(project.name, "")
                                        }
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
    // Sibling names a new/renamed deliverable must not collide with. Scoped by BOTH parents, reading
    // the SAME `detail.goalArea` the VM's actions scope by — deriving the area here independently is
    // how the two would drift into checking one project's names while writing to another's.
    fun takenNames(project: String) = deliverables
        .filter {
            it.project.equals(project, ignoreCase = true) &&
                it.goalArea.equals(detail.goalArea, ignoreCase = true)
        }
        .map { it.name }

    createDeliverableFor?.let { target ->
        AddProjectDialog(
            palette = palette,
            title = "New ${DELIVERABLE_LABEL.lowercase()} in ${target.project}",
            placeholder = "e.g. Merchant onboarding",
            taken = takenNames(target.project),
            onConfirm = { viewModel.createDeliverable(it, target.project); createDeliverableFor = null },
            onDismiss = { createDeliverableFor = null },
        )
    }
    renameDeliverable?.let { target ->
        AddProjectDialog(
            palette = palette,
            title = "Rename ${DELIVERABLE_LABEL.lowercase()}",
            placeholder = "e.g. Merchant onboarding",
            initial = target.name,
            confirmLabel = "Save",
            taken = takenNames(target.project),
            onConfirm = {
                viewModel.renameDeliverableByName(target.name, target.project, it)
                renameDeliverable = null
            },
            onDismiss = { renameDeliverable = null },
        )
    }
    deleteDeliverable?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteDeliverable = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDeliverableByName(target.name, target.project)
                    deleteDeliverable = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteDeliverable = null }) { Text("Cancel") } },
            title = { Text("Delete “${target.name}”?", color = palette.text1) },
            // Says plainly what survives — deleting a grouping next to a list of wins reads like it
            // takes them with it, and the record is the one thing the user can't afford to lose.
            text = {
                Text(
                    "Your entries stay — they'll just list under ${target.project} instead. " +
                        "Only the grouping goes.",
                    color = palette.text3,
                )
            },
            containerColor = palette.surface,
        )
    }
    detailEntry?.let { target ->
        EntryDetailSheet(
            entry = target,
            folders = folders,
            framework = framework,
            deliverables = deliverables,
            onSaveEdit = { newText -> viewModel.editText(target.id, newText); detailEntry = null },
            onRecategorize = { goalArea, project, deliverable, demonstrates, createNew ->
                viewModel.recategorize(target, goalArea, project, deliverable, demonstrates, createNew)
                detailEntry = null
            },
            onToggleExtra = { v -> viewModel.setExtra(target.id, v); detailEntry = target.copy(isExtra = v) },
            onTogglePin = { v -> viewModel.setPinned(target.id, v); detailEntry = target.copy(isPinned = v) },
            onDelete = { detailEntry = null; deleteTarget = target },
            onDismiss = { detailEntry = null },
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
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 8)
        },
        containerColor = palette.surface,
    )
}

@Composable
private fun AddProjectDialog(
    palette: BragPalette,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    // Defaulted so the original "New project folder" call site is unchanged; the deliverable
    // create/rename dialogs reuse it, since all three ask the same single question (v0.33.0).
    title: String = "New project folder",
    placeholder: String = "e.g. Raven Migration",
    initial: String = "",
    confirmLabel: String = "Create",
    /** Sibling names this must not collide with. A duplicate is blocked and said out loud here,
     *  because the DAO below is `UPDATE OR IGNORE` and can only fail silently. */
    taken: List<String> = emptyList(),
) {
    var name by remember { mutableStateOf(initial) }
    val trimmed = name.trim()
    val unchanged = trimmed.equals(initial.trim(), ignoreCase = true)
    val duplicate = !unchanged && taken.any { it.equals(trimmed, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                // A rename to the same name is a no-op the VM would drop anyway — disabling it here
                // means the button never looks like it did something it didn't.
                enabled = trimmed.isNotEmpty() && !unchanged && !duplicate,
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, color = palette.text1) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    isError = duplicate,
                    placeholder = { Text(placeholder, color = palette.text3) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (duplicate) {
                    Spacer(Modifier.height(Spacing.s2))
                    Text(
                        "There's already one called “$trimmed” here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        containerColor = palette.surface,
    )
}

/** A deliverable this screen is acting on. The goal area comes from the screen itself (its pillar), so
 *  only the project + name need carrying. [name] is "" for a pending create. */
private data class DeliverableTarget(val project: String, val name: String)

/**
 * One project's entries with the **deliverable** level folded in (v0.33.0), in the order Home uses:
 * active groups (heading + wins, no extra tap) → loose wins (no heading) → done groups, collapsed.
 *
 * Shared by this screen's two renderings — the pillar view's project cards and the single-folder
 * ("See all") screen — because they show the same thing and would otherwise drift. Uncapped: this is
 * the deep view, so nothing is held back.
 */
@Composable
private fun ProjectBody(
    project: ProjectBullets,
    hue: Color,
    palette: BragPalette,
    selectionMode: Boolean,
    isSelected: (Long) -> Boolean,
    isDeliverableExpanded: (String) -> Boolean,
    onToggleDeliverable: (String) -> Unit,
    onToggleSelect: (Long) -> Unit,
    onEnterSelection: (Long) -> Unit,
    onEdit: (EntryEntity) -> Unit,
    onRedo: (EntryEntity) -> Unit,
    onDeleteEntry: (EntryEntity) -> Unit,
    onTapEntry: (EntryEntity) -> Unit,
    onAddEntryTo: (String) -> Unit,
    onRenameDeliverable: (String) -> Unit,
    onSetDeliverableDone: (String, Boolean) -> Unit,
    onDeleteDeliverable: (String) -> Unit,
) {
    @Composable
    fun bullet(entry: EntryEntity, indent: Boolean) {
        EntryBulletRow(
            entry = entry,
            hue = hue,
            showFromProject = false,
            selectionMode = selectionMode,
            isSelected = isSelected(entry.id),
            indent = indent,
            onToggleSelect = { onToggleSelect(entry.id) },
            onLongPress = { onEnterSelection(entry.id) },
            onEdit = { onEdit(entry) },
            onRedo = { onRedo(entry) },
            onDelete = { onDeleteEntry(entry) },
            onTap = { onTapEntry(entry) },
        )
    }

    // `key(g.name)` on each group: these render in a forEach, so without it Compose binds state to the
    // SLOT, and groups reorder on their own (the sort is by recency — an entry finishing filing in the
    // background re-sorts them under an open menu). The popup hides the swap, so the next tap would hit
    // whatever slid into that slot. Keyed, the whole group moves with its state.
    project.activeDeliverables.forEach { g ->
        key(g.name) {
            DeliverableHeader(
                group = g,
                hue = hue,
                palette = palette,
                expanded = true,
                collapsible = false,
                onToggle = {},
                onAddEntry = { onAddEntryTo(g.name) },
                onRename = { onRenameDeliverable(g.name) },
                onToggleDone = { onSetDeliverableDone(g.name, true) },
                onDelete = { onDeleteDeliverable(g.name) },
            )
            g.entries.forEach { bullet(it, indent = true) }
            // Home says this; without it the deep view shows a bare heading and reads like a bug.
            if (g.entries.isEmpty()) EmptyDeliverableNote(palette)
        }
    }
    project.loose.forEach { bullet(it, indent = false) }
    project.doneDeliverables.forEach { g ->
        key(g.name) {
            // Selection mode force-opens everything, exactly as the project level does — a hidden win
            // can't be bulk-selected, and a done deliverable's wins are still part of the record.
            val open = selectionMode || isDeliverableExpanded(g.name)
            DeliverableHeader(
                group = g,
                hue = hue,
                palette = palette,
                expanded = open,
                collapsible = true,
                onToggle = { onToggleDeliverable(g.name) },
                onAddEntry = { onAddEntryTo(g.name) },
                onRename = { onRenameDeliverable(g.name) },
                onToggleDone = { onSetDeliverableDone(g.name, false) },
                onDelete = { onDeleteDeliverable(g.name) },
            )
            if (open) {
                g.entries.forEach { bullet(it, indent = true) }
                if (g.entries.isEmpty()) EmptyDeliverableNote(palette)
            }
        }
    }
}

