package com.bragbuddy.app.ui.home

import android.content.Intent
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.R
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntrySource
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.capture.CaptureActivity
import com.bragbuddy.app.ui.role.RoleInput
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * Home (Design System §1). Phase 2 shows the **categorized** result — cleaned bullet + placement
 * chip, or a processing/Inbox state. Adds project **folders** (tap → capture into that project) and,
 * on first run, a gentle "what's your role?" prompt. Flat entry list; the structured document is
 * Phase 3.
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val showRolePrompt by viewModel.showRolePrompt.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<EntryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<EntryEntity?>(null) }
    var showCreateFolder by remember { mutableStateOf(false) }

    fun captureInto(project: String?) {
        val intent = Intent(context, CaptureActivity::class.java)
        if (project != null) intent.putExtra(CaptureActivity.EXTRA_PROJECT, project)
        context.startActivity(intent)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.s4))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_eyebrow),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.text1,
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, stringResource(R.string.nav_settings), tint = palette.text2)
            }
        }
        Spacer(Modifier.height(Spacing.s3))

        if (showRolePrompt) {
            RolePromptCard(
                palette = palette,
                onSave = { viewModel.saveRole(it) },
                onDismiss = { viewModel.dismissRolePrompt() },
            )
            Spacer(Modifier.height(Spacing.s3))
        }

        FoldersRow(
            folders = folders,
            palette = palette,
            onOpen = { captureInto(it.name) },
            onCreate = { showCreateFolder = true },
        )
        Spacer(Modifier.height(Spacing.s3))

        Box(Modifier.weight(1f)) {
            if (entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState() }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = contentBottomPadding + Spacing.s4),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        EntryCard(
                            entry = entry,
                            onEdit = { editTarget = entry },
                            onRedo = {
                                context.startActivity(
                                    Intent(context, CaptureActivity::class.java)
                                        .putExtra(CaptureActivity.EXTRA_REPLACE_ID, entry.id),
                                )
                            },
                            onDelete = { deleteTarget = entry },
                        )
                    }
                }
            }
        }
    }

    editTarget?.let { target ->
        EditEntryDialog(
            // Show the cleaned bullet for a filed entry (editing it re-files just this one entry);
            // fall back to the raw transcript for a still-processing / Inbox / failed row.
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

    if (showCreateFolder) {
        CreateFolderDialog(
            palette = palette,
            onConfirm = { viewModel.createFolder(it); showCreateFolder = false },
            onDismiss = { showCreateFolder = false },
        )
    }
}

// ---------------- Project folders ----------------

@Composable
private fun FoldersRow(
    folders: List<ProjectEntity>,
    palette: BragPalette,
    onOpen: (ProjectEntity) -> Unit,
    onCreate: () -> Unit,
) {
    Column {
        Text(
            "PROJECTS",
            style = MaterialTheme.typography.labelSmall,
            color = palette.text3,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 2.dp, bottom = Spacing.s2),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            folders.forEach { FolderCard(it.name, palette) { onOpen(it) } }
            NewFolderCard(hasFolders = folders.isNotEmpty(), palette = palette, onClick = onCreate)
        }
    }
}

@Composable
private fun FolderCard(name: String, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .widthIn(max = 200.dp)
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(palette.primarySoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Folder, null, tint = palette.primary, modifier = Modifier.size(15.dp)) }
        Spacer(Modifier.size(Spacing.s2))
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            color = palette.text1,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun NewFolderCard(hasFolders: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(Radii.lg))
            .border(1.5.dp, palette.primary.copy(alpha = 0.4f), RoundedCornerShape(Radii.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.s3, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = palette.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(
            if (hasFolders) "New" else "New project",
            style = MaterialTheme.typography.titleSmall,
            color = palette.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CreateFolderDialog(palette: BragPalette, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
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

// ---------------- First-run role prompt ----------------

@Composable
private fun RolePromptCard(palette: BragPalette, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("What's your role?", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "Helps me judge what's core vs. standout for you. Not your company.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "Not now", tint = palette.text3, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(Spacing.s3))
        RoleInput(value = draft, onValueChange = { draft = it }, palette = palette, onImeDone = { if (draft.isNotBlank()) onSave(draft) })
        Spacer(Modifier.height(Spacing.s3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (draft.isNotBlank()) palette.primary else palette.primary.copy(alpha = 0.4f))
                    .clickable(enabled = draft.isNotBlank()) { onSave(draft) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Save", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.size(Spacing.s3))
            Text(
                "Not now",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onDismiss).padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

// ---------------- Entry cards (unchanged from v0.5.0) ----------------

@Composable
private fun EntryCard(
    entry: EntryEntity,
    onEdit: () -> Unit,
    onRedo: () -> Unit,
    onDelete: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val relTime = DateUtils.getRelativeTimeSpanString(
        entry.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val sourceLabel = if (entry.source == EntrySource.VOICE) "Voice" else "Typed"

    val headline = entry.bullet?.takeIf { it.isNotBlank() } ?: entry.rawTranscript
    val dotColor = when (entry.status) {
        EntryStatus.PROCESSED -> palette.primary
        EntryStatus.INBOX, EntryStatus.FAILED -> palette.inbox
        EntryStatus.RAW -> palette.text3
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(start = Spacing.card, top = Spacing.card, bottom = Spacing.card, end = 4.dp),
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dotColor),
        )
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text1,
            )

            val chips = statusChips(entry, palette)
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s2))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { Chip(it.text, it.fill, it.ink) }
                }
            }

            Spacer(Modifier.height(Spacing.s2))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (entry.source == EntrySource.VOICE) Icons.Outlined.Mic else Icons.Outlined.Keyboard,
                    contentDescription = null,
                    tint = palette.text3,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.size(5.dp))
                Text(
                    text = "$relTime · $sourceLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }
        }
        EntryMenu(onEdit = onEdit, onRedo = onRedo, onDelete = onDelete)
    }
}

@Composable
private fun EntryMenu(onEdit: () -> Unit, onRedo: () -> Unit, onDelete: () -> Unit) {
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

@Composable
private fun EditEntryDialog(
    initial: String,
    palette: BragPalette,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save & re-file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit entry", color = palette.text1) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        containerColor = palette.surface,
    )
}

private data class ChipSpec(val text: String, val fill: Color, val ink: Color)

private fun statusChips(entry: EntryEntity, palette: BragPalette): List<ChipSpec> {
    val chips = mutableListOf<ChipSpec>()
    when (entry.status) {
        EntryStatus.RAW -> chips += ChipSpec("Processing…", palette.surface2, palette.text3)
        EntryStatus.FAILED -> chips += ChipSpec("Waiting for AI", palette.inboxSoft, palette.inboxInk)
        EntryStatus.INBOX -> chips += ChipSpec("Inbox", palette.inboxSoft, palette.inboxInk)
        EntryStatus.PROCESSED -> {
            entry.goalCategory?.takeIf { it.isNotBlank() && !it.equals("Inbox", true) }
                ?.let { chips += ChipSpec(it, palette.primarySoft, palette.primary) }
            entry.project
                ?.takeIf { it.isNotBlank() && !it.equals("Inbox", true) && !it.equals("Outside-project", true) }
                ?.let { chips += ChipSpec(it, palette.surface2, palette.text2) }
        }
    }
    if (entry.isExtra) chips += ChipSpec("Extra", palette.extraSoft, palette.extraInk)
    return chips
}

@Composable
private fun Chip(text: String, fill: Color, ink: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .wrapContentWidth()
            .clip(RoundedCornerShape(Radii.sm))
            .background(fill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun EmptyState() {
    val palette = BragBuddyTheme.palette
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s6),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(palette.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Inbox, null, tint = palette.primary, modifier = Modifier.size(30.dp))
        }
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = palette.text1,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.text3,
            textAlign = TextAlign.Center,
        )
    }
}
