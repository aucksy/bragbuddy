package com.bragbuddy.app.ui.home

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.R
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.EntryStatus
import com.bragbuddy.app.ui.common.LocalSnackbarController
import com.bragbuddy.app.ui.capture.CaptureLauncher
import com.bragbuddy.app.ui.common.EntryBulletRow
import com.bragbuddy.app.ui.entry.EntryDetailSheet
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
    onOpenFolder: (String, String) -> Unit,
    onReviewInbox: () -> Unit,
    onOpenSummary: () -> Unit,
    onOpenReliability: () -> Unit,
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = LocalSnackbarController.current
    // M2 · flash "Filed ✓ → <goal area>" the moment an entry finishes filing (RAW → PROCESSED).
    LaunchedEffect(Unit) { viewModel.filedConfirmation.collect { snackbar.show(it) } }
    val doc by viewModel.doc.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val showRolePrompt by viewModel.showRolePrompt.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    // M2 · the one-slot nudge queue: at most ONE dismissible card, resolved by priority in the VM.
    val activeNudge by viewModel.activeNudge.collectAsStateWithLifecycle()
    // Phase 4 · "Add impact" card content (the card itself is rendered only when [activeNudge] == Impact).
    val impactCandidates by viewModel.impactCandidates.collectAsStateWithLifecycle()
    val impactSuggestion by viewModel.impactSuggestion.collectAsStateWithLifecycle()

    // Time/system-state cards re-evaluate whenever Home comes (back) into view; the lifecycle-aware
    // collectors above also restart their upstreams on resume, so a background→foreground open
    // re-checks "has the reminder time passed" and the reminder-health probes.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // When set, the folder-create dialog is open for this goal area (null area = first goal area).
    var createFolderFor by remember { mutableStateOf<CreateFolderTarget?>(null) }
    // Per-entry edit / delete (inline folder expansion mirrors the deep pillar view's actions).
    var editTarget by remember { mutableStateOf<EntryEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<EntryEntity?>(null) }
    // Tap an entry → detail sheet (snapshot; toggles update it optimistically so ★/Pin flip instantly).
    var detailEntry by remember { mutableStateOf<EntryEntity?>(null) }
    // The "Add impact" card's expand state + the entry whose add-impact sheet is open.
    var impactExpanded by remember { mutableStateOf(false) }
    var impactTarget by remember { mutableStateOf<EntryEntity?>(null) }

    fun copyAll() {
        val text = exportDocument(doc)
        if (text.isBlank()) {
            snackbar.show("Nothing to copy yet")
        } else {
            clipboard.setText(AnnotatedString(text))
            snackbar.show("Copied — paste into Word or Docs")
        }
    }
    // Collapsible sections — expanded ids; default = none (everything starts collapsed) except the
    // Performance Goals section, seeded open once below (fix: it should be expanded by default).
    val expanded = remember { mutableStateListOf<String>() }
    fun toggle(key: String) { if (expanded.contains(key)) expanded.remove(key) else expanded.add(key) }
    // Folder-level inline expansion, keyed "<pillarId>::<folderName>". Default = collapsed.
    val expandedFolders = remember { mutableStateListOf<String>() }
    fun toggleFolder(key: String) { if (expandedFolders.contains(key)) expandedFolders.remove(key) else expandedFolders.add(key) }

    // Seed the Performance Goals section open once (user can then collapse it and it stays collapsed).
    var seededExpand by remember { mutableStateOf(false) }
    LaunchedEffect(doc.goals) {
        if (!seededExpand && doc.goals.isNotEmpty()) {
            doc.goals.firstOrNull { it.pillar.name.equals("Performance Goals", ignoreCase = true) }
                ?.let { expanded.add("goal-" + it.pillar.id) }
            seededExpand = true
        }
    }

    // In-context "+" (Add entry to a folder) → the 3-choice chooser, anchored to that folder.
    fun captureInto(project: String?) = CaptureLauncher.openChooser(context, project)
    fun redo(entry: EntryEntity) = CaptureLauncher.redo(context, entry.id)

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
            if (!doc.isEmpty) {
                Text(
                    "Copy",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.primarySoft)
                        .clickable { copyAll() }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
                Spacer(Modifier.size(Spacing.s2))
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
            // A brand-new user on a risky OEM still deserves the reminder-health warning — the only
            // nudge that can be active with no entries (daily/impact/preview all need prior content).
            if (activeNudge != HomeViewModel.HomeNudge.None) {
                HomeNudgeCard(
                    nudge = activeNudge, palette = palette, impactCandidates = impactCandidates,
                    impactExpanded = impactExpanded, onImpactToggle = { impactExpanded = !impactExpanded },
                    onAddImpact = { impactTarget = it }, onDismissImpact = { viewModel.dismissImpactCard() },
                    onOpenReliability = onOpenReliability, onDismissReliability = { viewModel.dismissReliabilityCard() },
                    onDailyAdd = { CaptureLauncher.openDefault(context) }, onDismissDaily = { viewModel.dismissDailyNudge() },
                    previewFirstGoal = doc.goals.firstOrNull()?.pillar?.name,
                    previewFirstBehaviour = doc.behaviours.firstOrNull()?.pillar?.name,
                    onSeePreview = onOpenSummary, onDismissPreview = { viewModel.dismissPreviewBanner() },
                )
                Spacer(Modifier.height(Spacing.s3))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(onNewProject = { createFolderFor = CreateFolderTarget(null) })
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(Spacing.s4),
                contentPadding = PaddingValues(top = Spacing.s1, bottom = contentBottomPadding + Spacing.s4),
            ) {
                // M2 · exactly ONE dismissible nudge card (priority-resolved in the VM), above the strips.
                if (activeNudge != HomeViewModel.HomeNudge.None) {
                    item(key = "nudge") {
                        HomeNudgeCard(
                            nudge = activeNudge, palette = palette, impactCandidates = impactCandidates,
                            impactExpanded = impactExpanded, onImpactToggle = { impactExpanded = !impactExpanded },
                            onAddImpact = { impactTarget = it }, onDismissImpact = { viewModel.dismissImpactCard() },
                            onOpenReliability = onOpenReliability, onDismissReliability = { viewModel.dismissReliabilityCard() },
                            onDailyAdd = { CaptureLauncher.openDefault(context) }, onDismissDaily = { viewModel.dismissDailyNudge() },
                            previewFirstGoal = doc.goals.firstOrNull()?.pillar?.name,
                            previewFirstBehaviour = doc.behaviours.firstOrNull()?.pillar?.name,
                            onSeePreview = onOpenSummary, onDismissPreview = { viewModel.dismissPreviewBanner() },
                        )
                    }
                }
                // Status strips (not dismissible cards) — coexist with the single nudge above.
                if (doc.waiting.isNotEmpty()) {
                    item(key = "waiting") { WaitingCard(doc.waiting, isOnline, palette) }
                }
                if (doc.processing.isNotEmpty()) {
                    item(key = "processing") { ProcessingCard(doc.processing, palette) }
                }

                items(doc.goals, key = { "goal-" + it.pillar.id }) { section ->
                    GoalSectionView(
                        section = section,
                        palette = palette,
                        expanded = expanded.contains("goal-" + section.pillar.id),
                        onToggle = { toggle("goal-" + section.pillar.id) },
                        isFolderExpanded = { folder -> expandedFolders.contains(section.pillar.id + "::" + folder) },
                        onToggleFolder = { folder -> toggleFolder(section.pillar.id + "::" + folder) },
                        onSeeMore = { folder -> onOpenFolder(section.pillar.id, folder) },
                        onAddEntry = { folder -> captureInto(folder) },
                        onOpenDetail = { detailEntry = it },
                        onEdit = { editTarget = it },
                        onRedo = { redo(it) },
                        onDelete = { deleteTarget = it },
                        onAddProject = { createFolderFor = CreateFolderTarget(section.pillar.name) },
                    )
                }

                items(doc.behaviours, key = { "beh-" + it.pillar.id }) { section ->
                    BehaviourSectionView(
                        section = section,
                        palette = palette,
                        expanded = expanded.contains("beh-" + section.pillar.id),
                        onToggle = { toggle("beh-" + section.pillar.id) },
                        onOpen = { onOpenPillar(section.pillar.id) },
                    )
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

    detailEntry?.let { target ->
        EntryDetailSheet(
            entry = target,
            folders = folders,
            framework = framework,
            onSaveEdit = { newText -> viewModel.editText(target.id, newText); detailEntry = null },
            onRecategorize = { goalArea, project, demonstrates ->
                viewModel.recategorize(target, goalArea, project, demonstrates); detailEntry = null
            },
            onToggleExtra = { v -> viewModel.setExtra(target.id, v); detailEntry = target.copy(isExtra = v) },
            onTogglePin = { v -> viewModel.setPinned(target.id, v); detailEntry = target.copy(isPinned = v) },
            onDelete = { detailEntry = null; deleteTarget = target },
            onDismiss = { detailEntry = null },
        )
    }

    // Fetch the project-aware coaching question whenever an add-impact sheet opens (cleared on close).
    LaunchedEffect(impactTarget) { impactTarget?.let { viewModel.loadImpactSuggestion(it) } }
    impactTarget?.let { target ->
        AddImpactSheet(
            bullet = target.bullet.orEmpty(),
            suggestion = impactSuggestion,
            onAdd = { added ->
                viewModel.addImpact(target, added)
                snackbar.show("Adding your impact…")
                impactTarget = null
                viewModel.clearImpactSuggestion()
            },
            onDismiss = { impactTarget = null; viewModel.clearImpactSuggestion() },
        )
    }
}

private data class CreateFolderTarget(val goalArea: String?)

/** Cap on entries shown inline under a folder on Home; more are reached via "See more" → folder screen. */
private const val MAX_INLINE_ENTRIES = 10

// ---------------- Goal / growth pillar section ----------------

@Composable
private fun GoalSectionView(
    section: GoalSection,
    palette: BragPalette,
    expanded: Boolean,
    onToggle: () -> Unit,
    isFolderExpanded: (String) -> Boolean,
    onToggleFolder: (String) -> Unit,
    onSeeMore: (String) -> Unit,
    onAddEntry: (String) -> Unit,
    onOpenDetail: (EntryEntity) -> Unit,
    onEdit: (EntryEntity) -> Unit,
    onRedo: (EntryEntity) -> Unit,
    onDelete: (EntryEntity) -> Unit,
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
            expanded = expanded,
            palette = palette,
            onClick = onToggle,
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                section.projects.forEach { project ->
                    val key = if (project.isOutside) OUTSIDE_PROJECT_LABEL else project.name
                    FolderCard(
                        project = project,
                        hue = hue.solid,
                        palette = palette,
                        expanded = isFolderExpanded(key),
                        onToggle = { onToggleFolder(key) },
                        onSeeMore = { onSeeMore(key) },
                        onAddEntry = { onAddEntry(project.name) },
                        onOpenDetail = onOpenDetail,
                        onEdit = onEdit,
                        onRedo = onRedo,
                        onDelete = onDelete,
                    )
                }
                AddRow(text = "Add project", palette = palette, onClick = onAddProject)
            }
        }
    }
}

/**
 * A project folder on Home — tap to expand its recent entries inline (fully actionable: ⋮ edit / redo
 * / delete + "Add entry"). Only the last [MAX_INLINE_ENTRIES] show inline; if there are more, a
 * "See all N" row opens the dedicated folder screen ([onSeeMore]). Collapsed, it looks like before.
 */
@Composable
private fun FolderCard(
    project: ProjectBullets,
    hue: Color,
    palette: BragPalette,
    expanded: Boolean,
    onToggle: () -> Unit,
    onSeeMore: () -> Unit,
    onAddEntry: () -> Unit,
    onOpenDetail: (EntryEntity) -> Unit,
    onEdit: (EntryEntity) -> Unit,
    onRedo: (EntryEntity) -> Unit,
    onDelete: (EntryEntity) -> Unit,
) {
    val subtitle = when {
        project.entryCount == 0 -> "No entries yet"
        else -> {
            val rel = DateUtils.getRelativeTimeSpanString(
                project.lastUpdated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            "${project.entryCount} ${plural(project.entryCount, "entry", "entries")} · updated $rel"
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg)),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = Spacing.card, vertical = 13.dp),
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
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                if (expanded) "Collapse" else "Expand",
                tint = palette.text3,
                modifier = Modifier.size(20.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.padding(start = Spacing.card, end = Spacing.card, bottom = Spacing.card),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                if (project.entries.isEmpty()) {
                    Text("No entries yet", style = MaterialTheme.typography.bodySmall, color = palette.text3)
                } else {
                    project.entries.take(MAX_INLINE_ENTRIES).forEach { entry ->
                        EntryBulletRow(
                            entry = entry,
                            hue = hue,
                            showFromProject = false,
                            onEdit = { onEdit(entry) },
                            onRedo = { onRedo(entry) },
                            onDelete = { onDelete(entry) },
                            onTap = { onOpenDetail(entry) },
                        )
                    }
                    if (project.entryCount > MAX_INLINE_ENTRIES) {
                        SeeMoreRow(project.entryCount, palette, onSeeMore)
                    }
                }
                if (!project.isOutside) {
                    AddRow(text = "Add entry to ${project.name}", palette = palette, onClick = onAddEntry)
                }
            }
        }
    }
}

@Composable
private fun SeeMoreRow(total: Int, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.card, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("See all $total", style = MaterialTheme.typography.labelLarge, color = palette.primary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = palette.primary, modifier = Modifier.size(18.dp))
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

// ---------------- Behaviour pillar section ----------------

@Composable
private fun BehaviourSectionView(
    section: BehaviourSection,
    palette: BragPalette,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val hue = pillarColor(section.colorIndex)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        SectionHeader(
            dot = hue.solid,
            name = section.pillar.name,
            trailing = "${section.evidenceCount} ${plural(section.evidenceCount, "entry", "entries")}",
            expanded = expanded,
            palette = palette,
            onClick = onToggle,
        )
        AnimatedVisibility(visible = expanded) {
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

// ---------------- M2 · one-slot nudge dispatcher ----------------

/** Renders the single active dismissible nudge card (priority resolved in the VM). Used from both the
 *  empty-state branch and the document LazyColumn, so exactly one card is ever visible. */
@Composable
private fun HomeNudgeCard(
    nudge: HomeViewModel.HomeNudge,
    palette: BragPalette,
    impactCandidates: List<EntryEntity>,
    impactExpanded: Boolean,
    onImpactToggle: () -> Unit,
    onAddImpact: (EntryEntity) -> Unit,
    onDismissImpact: () -> Unit,
    onOpenReliability: () -> Unit,
    onDismissReliability: () -> Unit,
    onDailyAdd: () -> Unit,
    onDismissDaily: () -> Unit,
    previewFirstGoal: String?,
    previewFirstBehaviour: String?,
    onSeePreview: () -> Unit,
    onDismissPreview: () -> Unit,
) {
    when (nudge) {
        HomeViewModel.HomeNudge.Reliability ->
            ReliabilityCard(palette, onReview = onOpenReliability, onDismiss = onDismissReliability)
        HomeViewModel.HomeNudge.Daily ->
            DailyNudgeCard(palette, onAdd = onDailyAdd, onDismiss = onDismissDaily)
        HomeViewModel.HomeNudge.Impact ->
            ImpactCard(
                entries = impactCandidates, expanded = impactExpanded, onToggle = onImpactToggle,
                onAddImpact = onAddImpact, onDismiss = onDismissImpact, palette = palette,
            )
        is HomeViewModel.HomeNudge.Preview ->
            PreviewBannerCard(
                entryCount = nudge.count, firstGoalName = previewFirstGoal,
                firstBehaviourName = previewFirstBehaviour, onSee = onSeePreview, onDismiss = onDismissPreview,
            )
        HomeViewModel.HomeNudge.None -> Unit
    }
}

// ---------------- Phase 7 · retention + reliability cards ----------------
// (Not in the design files except the preview banner — the others are built from tokens; flagged.)

/** Queued offline captures — voice notes (PENDING_AUDIO) and image scans (PENDING_IMAGE): visible so
 *  an offline capture never disappears. Copy is modality-aware (voice / scan / mixed) and
 *  connectivity-aware — "waiting for network" would mislead while online (a queued item then usually
 *  means a service/key hiccup being retried). */
@Composable
private fun WaitingCard(waiting: List<EntryEntity>, isOnline: Boolean, palette: BragPalette) {
    val count = waiting.size
    val images = waiting.count { it.status == EntryStatus.PENDING_IMAGE }
    val noun = when {
        images == 0 -> "voice ${plural(count, "note")}"
        images == count -> plural(count, "scan")
        else -> plural(count, "capture")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(palette.primarySoft),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.CloudOff, null, tint = palette.primary, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(
                if (isOnline) "$count $noun saved — processing soon"
                else "$count $noun saved — waiting for network",
                style = MaterialTheme.typography.titleSmall,
                color = palette.text1,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (isOnline) "They'll be filed automatically. If this persists, check your Groq key in Settings."
                else "They'll be read and filed automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.text3,
            )
        }
    }
}

/** On-open "you haven't logged today" fallback (PRD P0-1) — the reminder's quiet safety net. */
@Composable
private fun DailyNudgeCard(palette: BragPalette, onAdd: () -> Unit, onDismiss: () -> Unit) {
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
                Text("Nothing logged today yet", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "One quick line is enough — what did you get done?",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }
            Box(
                Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "Not today", tint = palette.text3, modifier = Modifier.size(16.dp)) }
        }
        Spacer(Modifier.height(Spacing.s3))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(palette.primary)
                .clickable(onClick = onAdd)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("Add something", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

/** Detected reminder risk (battery optimization / exact alarms / notifications) → guided fix. */
@Composable
private fun ReliabilityCard(palette: BragPalette, onReview: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(palette.extraSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Notifications, null, tint = palette.extra, modifier = Modifier.size(16.dp)) }
            Spacer(Modifier.size(Spacing.s3))
            Column(Modifier.weight(1f)) {
                Text("Keep your reminder alive", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "This phone can silence background apps — a couple of quick settings keep the daily nudge reliable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }
        }
        Spacer(Modifier.height(Spacing.s3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Review settings",
                style = MaterialTheme.typography.labelLarge,
                color = palette.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primarySoft)
                    .clickable(onClick = onReview)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
            Spacer(Modifier.size(Spacing.s3))
            Text(
                "Not now",
                style = MaterialTheme.typography.labelLarge,
                color = palette.text3,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * The Design §7 "Early preview · week 1" banner — an indigo gradient promo shown once a handful of
 * entries are filed and no summary has ever been generated. The gradient and white inks are the
 * design's own (deliberately identical in dark theme, like the mockup).
 */
@Composable
private fun PreviewBannerCard(
    entryCount: Int,
    firstGoalName: String?,
    firstBehaviourName: String?,
    onSee: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF5A63E0), Color(0xFF3D46C0))))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "Your summary's taking shape",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(24.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Close, "Dismiss", tint = Color.White.copy(alpha = 0.75f), modifier = Modifier.size(14.dp)) }
        }
        Spacer(Modifier.height(10.dp))
        // The mockup's skeleton preview — two mini sections with placeholder lines (decorative).
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .padding(12.dp),
        ) {
            SkeletonSection(label = (firstGoalName ?: "Performance Goals").uppercase(), widths = listOf(1f, 0.74f))
            Spacer(Modifier.height(11.dp))
            SkeletonSection(label = (firstBehaviourName ?: "Leadership").uppercase(), widths = listOf(0.88f))
        }
        Spacer(Modifier.height(11.dp))
        Text(
            "From just $entryCount entries. Keep going — it only gets richer.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White)
                .clickable(onClick = onSee)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "See the preview",
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF3D46C0),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SkeletonSection(label: String, widths: List<Float>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(Color.White))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
    widths.forEach { w ->
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth(w)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.5f)),
        )
    }
}

// ---------------- Shared bits ----------------

@Composable
private fun SectionHeader(dot: Color, name: String, trailing: String, expanded: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Spacer(Modifier.size(Spacing.s2))
        Text(name, style = MaterialTheme.typography.titleMedium, color = palette.text1, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        Spacer(Modifier.size(Spacing.s2))
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            if (expanded) "Collapse" else "Expand",
            tint = palette.text3,
            modifier = Modifier.size(20.dp),
        )
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
