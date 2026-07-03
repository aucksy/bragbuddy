package com.bragbuddy.app.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.R
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.ui.role.RoleInput
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing
import com.bragbuddy.app.ui.theme.pillarColor

/**
 * Home (Design System §1 · "Home — your living document"). Phase 3 turns the flat list into the
 * structured document: goal/growth **pillars** hold **project cards** (name · N entries · updated);
 * behaviour pillars gather the entries that **evidence** them; the **Inbox** peek waits at the end.
 * Tapping any section opens the deep pillar view ([onOpenPillar]) where the dated bullets live and
 * entries are added/edited — Home stays an overview, the depth is one tap in.
 *
 * (The header's "Summarise" action from the design is a Phase 5 feature; the Settings gear stays
 * here until the summary surface lands — a documented, pre-existing deviation.)
 */
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenPillar: (String) -> Unit,
    onReviewInbox: () -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val doc by viewModel.doc.collectAsStateWithLifecycle()
    val showRolePrompt by viewModel.showRolePrompt.collectAsStateWithLifecycle()

    // When set, the folder-create dialog is open for this goal area (null area = first goal area).
    var createFolderFor by remember { mutableStateOf<CreateFolderTarget?>(null) }

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

        // First-run role prompt sits above the document so a brand-new user (empty Home) still sees it.
        if (showRolePrompt) {
            RolePromptCard(
                palette = palette,
                onSave = { viewModel.saveRole(it) },
                onDismiss = { viewModel.dismissRolePrompt() },
            )
            Spacer(Modifier.height(Spacing.s3))
        }

        if (doc.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(onNewProject = { createFolderFor = CreateFolderTarget(null) })
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
                contentPadding = PaddingValues(top = Spacing.s1, bottom = contentBottomPadding + Spacing.s4),
            ) {
                if (doc.processing.isNotEmpty()) {
                    item(key = "processing") { ProcessingCard(doc.processing, palette) }
                }

                items(doc.goals, key = { "goal-" + it.pillar.id }) { section ->
                    GoalSectionView(
                        section = section,
                        palette = palette,
                        onOpen = { onOpenPillar(section.pillar.id) },
                        onAddProject = { createFolderFor = CreateFolderTarget(section.pillar.name) },
                    )
                }

                items(doc.behaviours, key = { "beh-" + it.pillar.id }) { section ->
                    BehaviourSectionView(section = section, palette = palette, onOpen = { onOpenPillar(section.pillar.id) })
                }

                doc.inbox?.let { peek ->
                    item(key = "inbox-peek") { InboxPeekCard(peek, palette, onReview = onReviewInbox) }
                }
            }
        }
    }

    createFolderFor?.let { target ->
        CreateFolderDialog(
            palette = palette,
            onConfirm = { viewModel.createFolder(it, target.goalArea); createFolderFor = null },
            onDismiss = { createFolderFor = null },
        )
    }
}

private data class CreateFolderTarget(val goalArea: String?)

// ---------------- Goal / growth pillar section ----------------

@Composable
private fun GoalSectionView(
    section: GoalSection,
    palette: BragPalette,
    onOpen: () -> Unit,
    onAddProject: () -> Unit,
) {
    val hue = pillarColor(section.colorIndex)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        SectionHeader(
            dot = hue.solid,
            name = section.pillar.name,
            trailing = if (section.namedProjectCount > 0) {
                "${section.namedProjectCount} ${plural(section.namedProjectCount, "project")}"
            } else {
                "${section.entryCount} ${plural(section.entryCount, "entry", "entries")}"
            },
            palette = palette,
            onClick = onOpen,
        )
        section.projects.forEach { project ->
            ProjectCard(project = project, palette = palette, onClick = onOpen)
        }
        AddRow(text = "Add project", palette = palette, onClick = onAddProject)
    }
}

@Composable
private fun ProjectCard(project: ProjectBullets, palette: BragPalette, onClick: () -> Unit) {
    val subtitle = when {
        project.entryCount == 0 -> "No entries yet"
        else -> {
            val rel = DateUtils.getRelativeTimeSpanString(
                project.lastUpdated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            "${project.entryCount} ${plural(project.entryCount, "entry", "entries")} · updated $rel"
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.card, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(if (project.isOutside) palette.surface2 else palette.primarySoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Folder,
                null,
                tint = if (project.isOutside) palette.text3 else palette.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(
                if (project.isOutside) OUTSIDE_PROJECT_LABEL else project.name,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text1,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.text3, maxLines = 1)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = palette.text3, modifier = Modifier.size(20.dp))
    }
}

// ---------------- Behaviour pillar section ----------------

@Composable
private fun BehaviourSectionView(section: BehaviourSection, palette: BragPalette, onOpen: () -> Unit) {
    val hue = pillarColor(section.colorIndex)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        SectionHeader(
            dot = hue.solid,
            name = section.pillar.name,
            trailing = "${section.evidenceCount} ${plural(section.evidenceCount, "entry", "entries")}",
            palette = palette,
            onClick = onOpen,
        )
        section.sample?.let { sample ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
                    .clickable(onClick = onOpen)
                    .padding(Spacing.card),
            ) {
                Text(
                    sample.headline(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text1,
                )
                val from = sample.project?.takeIf { it.isNotBlank() && !it.equals("Outside-project", true) && !it.equals("Inbox", true) }
                if (from != null) {
                    Spacer(Modifier.height(2.dp))
                    Text("from $from", style = MaterialTheme.typography.bodySmall, color = palette.text3)
                }
                if (section.moreCount > 0) {
                    Spacer(Modifier.height(Spacing.s2))
                    Text(
                        "+${section.moreCount} more evidence this",
                        style = MaterialTheme.typography.labelMedium,
                        color = hue.solid,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

// ---------------- Inbox peek ----------------

@Composable
private fun InboxPeekCard(peek: InboxPeek, palette: BragPalette, onReview: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.inboxSoft)
            .clickable(onClick = onReview)
            .padding(Spacing.card),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(palette.inbox))
            Spacer(Modifier.size(Spacing.s2))
            Text("Inbox", style = MaterialTheme.typography.titleSmall, color = palette.inboxInk, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "${peek.count} ${plural(peek.count, "needs", "need")} a project",
                style = MaterialTheme.typography.labelMedium,
                color = palette.inboxInk,
                fontWeight = FontWeight.SemiBold,
            )
        }
        peek.first?.let {
            Spacer(Modifier.height(Spacing.s2))
            Text(it.headline(), style = MaterialTheme.typography.bodyMedium, color = palette.text1, maxLines = 2)
        }
        Spacer(Modifier.height(Spacing.s2))
        Text("Review →", style = MaterialTheme.typography.labelLarge, color = palette.inbox, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Processing (just-captured) ----------------

@Composable
private fun ProcessingCard(processing: List<EntryEntity>, palette: BragPalette) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Filing ${processing.size} new ${plural(processing.size, "entry", "entries")}…",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text2,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Chip("Processing…", palette.surface2, palette.text3)
        }
        processing.take(3).forEach {
            Text("• ${it.rawTranscript}", style = MaterialTheme.typography.bodySmall, color = palette.text3, maxLines = 1)
        }
    }
}

// ---------------- Shared bits ----------------

@Composable
private fun SectionHeader(dot: Color, name: String, trailing: String, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Spacer(Modifier.size(Spacing.s2))
        Text(name, style = MaterialTheme.typography.titleMedium, color = palette.text1, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = palette.text3)
    }
}

@Composable
private fun AddRow(text: String, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.card, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = palette.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = palette.primary, fontWeight = FontWeight.SemiBold)
    }
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

private fun EntryEntity.headline(): String = bullet?.takeIf { it.isNotBlank() } ?: rawTranscript

private fun plural(n: Int, one: String, many: String = one + "s"): String = if (n == 1) one else many

// ---------------- First-run role prompt (unchanged from v0.6.0) ----------------

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

// ---------------- Create-folder dialog ----------------

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

// ---------------- Empty state ----------------

@Composable
private fun EmptyState(onNewProject: () -> Unit) {
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
        Spacer(Modifier.height(Spacing.s1))
        AddRow(text = "New project", palette = palette, onClick = onNewProject)
    }
}
