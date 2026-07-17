package com.bragbuddy.app.ui.summary

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryCompetency
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.entry.Recategorize
import com.bragbuddy.app.data.framework.Framework
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.sp
import com.bragbuddy.app.data.ai.SummaryAchievement
import com.bragbuddy.app.data.local.DELIVERABLE_LABEL
import com.bragbuddy.app.data.local.DeliverableEntity
import com.bragbuddy.app.data.local.NO_PROJECT_LABEL
import com.bragbuddy.app.data.local.isNamedProject
import com.bragbuddy.app.ui.common.LocalBottomBarInset
import com.bragbuddy.app.ui.common.LocalSnackbarController
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.rollup.SummaryLength
import com.bragbuddy.app.data.rollup.SummaryPeriod
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.PillarColor
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing
import com.bragbuddy.app.ui.theme.pillarColor

/**
 * The appraisal summary (Design System §5). Reads the running rollup, windows it to the chosen
 * period, and shows a curated, length-capped write-up: per-pillar wins, routine work rolled into
 * counted lines, behaviour evidence, growth, a calm Set-aside note, and clean Copy for Word/Docs.
 * Pin (entry-level, v0.12.0) surfaces as a chip; promote/demote reorders locally; Regenerate calls
 * the model only when the rollup changed.
 */
@Composable
fun SummaryScreen(
    contentBottomPadding: Dp,
    onOpenSettings: () -> Unit,
    autoGenerate: Boolean = false,
    onAutoGenerateConsumed: () -> Unit = {},
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val snackbar = LocalSnackbarController.current
    val clipboard = LocalClipboardManager.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val generating by viewModel.generating.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val allDeliverables by viewModel.allDeliverables.collectAsStateWithLifecycle()

    var showGenerate by remember { mutableStateOf(false) }
    // Long-press edit/delete (feature #1) + restore-from-set-aside (feature #5) overlays. Hoisted here
    // so their custom-scrim sheets/dialogs cover the WHOLE screen (like GenerateSheet), not the padded
    // content Column.
    var pointerAction by remember { mutableStateOf<String?>(null) }
    var pointerEdit by remember { mutableStateOf<String?>(null) }
    // (bullet, its current goal area) — the line being retagged (v0.31.0).
    var retagLine by remember { mutableStateOf<RetagTarget?>(null) }

    // Home's early-preview banner lands here with autoGenerate set: the tap was the consent for
    // one metered generation. Waits for the state to load, consumes the flag exactly once, and
    // defers to generate()'s own guards (fresh cache → "Already up to date", no double call).
    LaunchedEffect(autoGenerate, state?.phase) {
        if (!autoGenerate) return@LaunchedEffect
        val s = state ?: return@LaunchedEffect
        onAutoGenerateConsumed()
        when (s.phase) {
            SummaryViewModel.Phase.NOT_GENERATED -> viewModel.generate()
            SummaryViewModel.Phase.READY -> if (s.isStale) viewModel.generate()
            else -> Unit // NEEDS_KEY / EMPTY / LOADING — the screen already shows the right state
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.show(it)
            viewModel.consumeMessage()
        }
    }

    fun copy(text: String, label: String) {
        if (text.isBlank()) {
            snackbar.show("Nothing to copy yet")
        } else {
            clipboard.setText(AnnotatedString(text))
            snackbar.show("$label — paste into Word or Docs")
        }
    }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        val s = state
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(Spacing.s4))
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Your appraisal summary", style = MaterialTheme.typography.bodySmall, color = palette.text3)
                    val headline = if (s != null && s.phase == SummaryViewModel.Phase.READY) "${s.period.label} summary" else "Summary"
                    Text(
                        headline,
                        style = MaterialTheme.typography.headlineLarge,
                        color = palette.text1,
                    )
                }
                if (s != null && s.phase != SummaryViewModel.Phase.NEEDS_KEY) {
                    HeaderPill("Options", palette.surface2, palette.text2) { showGenerate = true }
                }
            }
            Spacer(Modifier.height(Spacing.s3))

            if (s == null) {
                LoadingBlock(palette)
            } else {
                when (s.phase) {
                    SummaryViewModel.Phase.LOADING -> LoadingBlock(palette)
                    SummaryViewModel.Phase.NEEDS_KEY -> NeedsKeyBlock(palette, onOpenSettings)
                    SummaryViewModel.Phase.EMPTY -> EmptyBlock(palette, s, contentBottomPadding) { showGenerate = true }
                    SummaryViewModel.Phase.NOT_GENERATED -> NotGeneratedBlock(palette, s, contentBottomPadding) { showGenerate = true }
                    SummaryViewModel.Phase.READY -> ReadyBlock(
                        palette = palette,
                        state = s,
                        contentBottomPadding = contentBottomPadding,
                        onCopyText = { text, label -> copy(text, label) },
                        onRegenerate = {
                            if (s.isStale) viewModel.generate()
                            else snackbar.show("Already up to date")
                        },
                        onSwap = { area, a, b -> viewModel.swapAchievements(area, a, b) },
                        onLongPressPointer = { raw -> pointerAction = raw },
                        onRestoreItem = { item -> viewModel.restoreSetAside(item.bullet, item.area) },
                        onRestoreAll = { viewModel.restoreAllSetAside() },
                        onEditPointer = { raw -> pointerEdit = raw },
                        onDeletePointer = { raw -> viewModel.deletePointer(raw) },
                        onRetagRequest = { target -> retagLine = target },
                    )
                }
            }
        }

        if (showGenerate && s != null) {
            GenerateSheet(
                palette = palette,
                state = s,
                onSelectPeriod = viewModel::selectPeriod,
                onSelectLength = viewModel::selectLength,
                onGenerate = { viewModel.generate(); showGenerate = false },
                onDismiss = { showGenerate = false },
            )
        }

        pointerAction?.let { raw ->
            PointerActionSheet(
                palette = palette,
                text = raw,
                onEdit = { pointerEdit = raw; pointerAction = null },
                onDelete = { viewModel.deletePointer(raw); pointerAction = null },
                onDismiss = { pointerAction = null },
            )
        }
        pointerEdit?.let { raw ->
            EditPointerDialog(
                palette = palette,
                initial = raw,
                onSave = { newText -> viewModel.editPointer(raw, newText); pointerEdit = null },
                onDismiss = { pointerEdit = null },
            )
        }
        retagLine?.let { target ->
            if (s != null) {
                RetagSheet(
                    palette = palette,
                    line = target.bullet,
                    currentArea = target.area,
                    currentProject = target.project,
                    currentProjectKnown = target.projectKnown,
                    framework = s.framework,
                    folders = s.allFolders,
                    deliverables = allDeliverables,
                    onApply = { category, project, deliverable, createNew, touched, projTouched ->
                        viewModel.retagAchievement(
                            target.area, target.bullet, category, project, deliverable,
                            createNew, touched, projTouched,
                        )
                        retagLine = null
                    },
                    onDismiss = { retagLine = null },
                )
            }
        }

        if (generating) GeneratingOverlay(palette)
    }
}

// ---------------- phase blocks ----------------

@Composable
private fun LoadingBlock(palette: BragPalette) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = palette.primary, strokeWidth = 2.5.dp)
    }
}

@Composable
private fun NeedsKeyBlock(palette: BragPalette, onOpenSettings: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = 60.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SparkTile(palette)
            Spacer(Modifier.height(Spacing.s4))
            Text("Add your Groq key to generate", style = MaterialTheme.typography.titleLarge, color = palette.text1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.s2))
            Text(
                "The summary uses your AI key. Add it in Settings — then generate a polished, review-ready write-up any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text3,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.s5))
            PrimaryButton("Open Settings", palette, enabled = true, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun EmptyBlock(palette: BragPalette, state: SummaryViewModel.ScreenState, bottom: Dp, onOpen: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = bottom + 12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SparkTile(palette)
            Spacer(Modifier.height(Spacing.s4))
            Text("Nothing to summarise yet", style = MaterialTheme.typography.titleLarge, color = palette.text1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.s2))
            Text(
                "No filed entries in ${state.period.label.lowercase()} (${state.window.rangeText}). Log a few updates, or pick another period.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text3,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.s5))
            PrimaryButton("Choose period & length", palette, enabled = true, onClick = onOpen)
        }
    }
}

@Composable
private fun NotGeneratedBlock(palette: BragPalette, state: SummaryViewModel.ScreenState, bottom: Dp, onOpen: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = bottom + 12.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SparkTile(palette)
            Spacer(Modifier.height(Spacing.s4))
            Text("Curate your summary", style = MaterialTheme.typography.titleLarge, color = palette.text1, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.s2))
            Text(
                "${state.entryCount} entries in ${state.period.label.lowercase()} (${state.window.rangeText}). Generate the strongest wins per pillar, ready to paste into any review form.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text3,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.s5))
            PrimaryButton("Generate summary", palette, enabled = true, leadingSpark = true, onClick = onOpen)
        }
    }
}

@Composable
private fun ReadyBlock(
    palette: BragPalette,
    state: SummaryViewModel.ScreenState,
    contentBottomPadding: Dp,
    onCopyText: (String, String) -> Unit,
    onRegenerate: () -> Unit,
    onSwap: (String, Int, Int) -> Unit,
    onLongPressPointer: (String) -> Unit,
    onRestoreItem: (SummaryViewModel.SetAsideItem) -> Unit,
    onRestoreAll: () -> Unit,
    onEditPointer: (String) -> Unit,
    onDeletePointer: (String) -> Unit,
    onRetagRequest: (RetagTarget) -> Unit,
) {
    val cached = state.cached ?: return
    val result = cached.result
    // rememberSaveable: the panel is deep down a long scroll, and losing its open state on a rotation or
    // process death means finding it again.
    var setAsideOpen by rememberSaveable { mutableStateOf(false) }
    // Collapsible categories (feature #3). Ephemeral, mirroring Home / Pillar-detail; empty set =
    // everything EXPANDED (this is a read-and-copy screen, so content is visible by default).
    val collapsed = remember { mutableStateListOf<String>() }
    fun toggle(key: String) { if (collapsed.contains(key)) collapsed.remove(key) else collapsed.add(key) }
    val generatedAt = DateUtils.getRelativeTimeSpanString(
        cached.generatedAtMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()

    Column(Modifier.fillMaxSize()) {
        // Sub-header: length · range · generated + status + copy
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${state.length.label} · ${cached.periodRangeText} · $generatedAt",
                style = MaterialTheme.typography.bodySmall,
                color = palette.text3,
                modifier = Modifier.weight(1f),
            )
            HeaderPill("Copy", palette.primarySoft, palette.primary) {
                onCopyText(exportSummary(result, "${state.period.label} summary"), "Copied summary")
            }
        }
        Spacer(Modifier.height(Spacing.s2))
        StatusChip(state.isStale, palette, onRegenerate)
        Spacer(Modifier.height(Spacing.s3))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            result.summary.goalAreas.forEach { area ->
                if (area.achievements.isNotEmpty() || area.rolledUp.isNotEmpty()) {
                    val key = "goal-${area.name}"
                    GoalAreaSection(
                        area = area,
                        state = state,
                        palette = palette,
                        expanded = !collapsed.contains(key),
                        onToggle = { toggle(key) },
                        onCopy = { onCopyText(exportGoalArea(area), "Copied section") },
                        onSwap = onSwap,
                        onLongPress = onLongPressPointer,
                        onEdit = onEditPointer,
                        onDelete = onDeletePointer,
                        onRetag = onRetagRequest,
                    )
                    Spacer(Modifier.height(Spacing.s4))
                }
            }
            result.summary.behaviours.forEach { b ->
                if (b.evidence.isNotEmpty() || b.competencies.any { it.evidence.isNotEmpty() }) {
                    val key = "beh-${b.name}"
                    BehaviourSection(
                        b = b,
                        framework = state.framework,
                        palette = palette,
                        expanded = !collapsed.contains(key),
                        onToggle = { toggle(key) },
                        onCopy = { onCopyText(exportBehaviour(b), "Copied section") },
                        onLongPress = onLongPressPointer,
                    )
                    Spacer(Modifier.height(Spacing.s4))
                }
            }
            if (result.summary.development.isNotEmpty()) {
                DevelopmentSection(
                    items = result.summary.development,
                    framework = state.framework,
                    palette = palette,
                    expanded = !collapsed.contains("dev"),
                    onToggle = { toggle("dev") },
                    onLongPress = onLongPressPointer,
                    onEdit = onEditPointer,
                    onDelete = onDeletePointer,
                    onRetag = onRetagRequest,
                )
                Spacer(Modifier.height(Spacing.s4))
            }
            // Gated on REAL dropped items, not on the model's notes: rule 7 asks for a setAside note on
            // every run, so a Detailed summary that dropped nothing would otherwise render a panel
            // announcing "0 items set aside".
            if (state.setAsideItems.isNotEmpty()) {
                SetAsidePanel(
                    items = state.setAsideItems,
                    notes = result.setAside,
                    open = setAsideOpen,
                    palette = palette,
                    onToggle = { setAsideOpen = !setAsideOpen },
                    onRestoreItem = onRestoreItem,
                    onRestoreAll = onRestoreAll,
                )
            }
            Spacer(Modifier.height(contentBottomPadding + 12.dp))
        }
    }
}

// ---------------- document sections ----------------

/**
 * A summary line the user asked to re-place, with everything the sheet needs to preselect honestly:
 * its text, the area it currently sits under, and its current project (null = no specific project).
 *
 * The project MUST travel with it. Without it the sheet opens with "No specific project" selected, so a
 * user who only wanted to fix the CATEGORY would tap Apply and silently un-file the entry from a project
 * they never touched — writing OUTSIDE_PROJECT into the record.
 */
/**
 * A summary line the user wants to re-place. [project] null is ambiguous on its own — it can mean "no
 * project" (a goal-area line that really has none) or "unknown" (a DEVELOPMENT line, which the model
 * returns as a bare string with no project attached). [projectKnown] disambiguates, because the sheet
 * must not offer a guess as though it were the answer: treating unknown as "none" wrote OUTSIDE_PROJECT
 * over a project the user never touched.
 */
private data class RetagTarget(
    val bullet: String,
    val area: String,
    val project: String?,
    val projectKnown: Boolean = true,
)

/**
 * Build a retag target from a rendered achievement, distinguishing the model **not saying** a project
 * from it saying there **isn't** one. Both arrive as a null-ish `project`, and conflating them is a
 * durable data loss:
 *  - `SummaryAchievement.project` is `String? = null` and the PART B schema literally offers
 *    `"project": "string or null"` — the model is never told it must echo the rollup's project, and for
 *    a **pinned** item it provably cannot (`pinnedForPrompt` carries only bullet + area);
 *  - a **restored set-aside** line is built by app code as `SummaryAchievement(bullet = text)`, so its
 *    project is ALWAYS null by construction — a deterministic repro, no model involved.
 * Treating those as "no project" made the sheet show "No specific project" as the chosen answer and
 * Apply wrote `OUTSIDE_PROJECT` — un-filing the win from a real project it was never shown, wiping the
 * deliverable with it, and anchoring both so nothing could restore them (v0.33.1 assessment).
 *
 * A genuinely project-less entry does NOT arrive this way: it carries the `Outside-project` sentinel
 * through the rollup, which [isNamedProject] recognises — so "said Outside" stays *known*, and only
 * silence is unknown.
 */
private fun retagTargetFor(ach: SummaryAchievement, areaName: String): RetagTarget {
    val said = ach.project?.trim().orEmpty()
    return RetagTarget(
        bullet = ach.bullet,
        area = areaName,
        project = said.takeIf { it.isNamedProject() },
        projectKnown = said.isNotEmpty(),
    )
}

private fun goalHue(framework: Framework, name: String): PillarColor {
    val idx = framework.pillars.indexOfFirst { it.name.equals(name, ignoreCase = true) }
    return pillarColor(if (idx >= 0) idx else framework.pillars.size)
}

private fun isPinned(pinnedBullets: List<String>, bullet: String): Boolean =
    pinnedBullets.any {
        it.equals(bullet, ignoreCase = true) || bullet.contains(it, ignoreCase = true) || it.contains(bullet, ignoreCase = true)
    }

@Composable
private fun GoalAreaSection(
    area: SummaryGoalArea,
    state: SummaryViewModel.ScreenState,
    palette: BragPalette,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onSwap: (String, Int, Int) -> Unit,
    onLongPress: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,

    onRetag: (RetagTarget) -> Unit,
) {
    val hue = goalHue(state.framework, area.name)
    SectionHeader(area.name, hue, palette, expanded, onToggle, onCopy)
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(Modifier.height(Spacing.s3))
            // Item 5: group the area's wins into collapsible project folders (mirrors Home). Falls back
            // to a flat list when there's no structure to show (single project / all loose).
            val folders = groupAchievementsByProject(area.achievements)
            if (folders != null) {
                folders.forEach { folder ->
                    ProjectFolder(folder, area.name, hue, state, palette, onSwap, onLongPress, onEdit, onDelete, onRetag)
                    Spacer(Modifier.height(Spacing.s2))
                }
            } else {
                val many = area.achievements.size > 1
                area.achievements.forEachIndexed { i, ach ->
                    AchievementRow(
                        text = achievementDisplay(ach.bullet, ach.metric, ach.project),
                        hue = hue,
                        palette = palette,
                        pinned = isPinned(state.pinnedBullets, ach.bullet),
                        count = ach.count,
                        showControls = many,
                        canUp = i > 0,
                        canDown = i < area.achievements.lastIndex,
                        onUp = { onSwap(area.name, i, i - 1) },
                        onDown = { onSwap(area.name, i, i + 1) },
                        onLongPress = { onLongPress(ach.bullet) },
                        onEdit = { onEdit(ach.bullet) },
                        onDelete = { onDelete(ach.bullet) },
                        onRetag = { onRetag(retagTargetFor(ach, area.name)) },
                    )
                    Spacer(Modifier.height(Spacing.s2))
                }
            }
            area.rolledUp.forEach { r ->
                val raw = r.bullet.ifBlank { r.routineType }
                RolledUpRow(raw, r.count, hue, palette, onLongPress = { onLongPress(raw) })
                Spacer(Modifier.height(Spacing.s2))
            }
        }
    }
}

/**
 * One project folder inside a goal-area section (item 5): a collapsible header naming the project +
 * its achievements (project shown by the header, so not repeated inline). Up/down reorder is scoped
 * to this folder — the arrows swap the achievement's real slot in the area's flat list.
 */
@Composable
private fun ProjectFolder(
    folder: SummaryFolder,
    areaName: String,
    hue: PillarColor,
    state: SummaryViewModel.ScreenState,
    palette: BragPalette,
    onSwap: (String, Int, Int) -> Unit,
    onLongPress: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetag: (RetagTarget) -> Unit,
) {
    // Default expanded (this is a read-and-copy screen — content visible by default). Keyed by name so
    // the state survives recomposition/reorder within the folder.
    var open by remember(folder.name) { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.sm)).clickable { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Folder, null, tint = hue.ink, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(Spacing.s2))
            Text(
                folder.name,
                style = MaterialTheme.typography.labelMedium,
                color = hue.ink,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${folder.items.size}", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Icon(
                if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null, tint = palette.text3, modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(Modifier.padding(start = Spacing.s3)) {
                Spacer(Modifier.height(Spacing.s2))
                // v0.34.0 · the third level: sub-group this folder's wins by deliverable. Null = no
                // structure worth drawing (all one deliverable / all loose) → render exactly as before.
                val groups = groupFolderByDeliverable(folder.items)
                if (groups != null) {
                    groups.forEach { g ->
                        g.name?.let { DeliverableSubHeader(it, hue, palette) }
                        AchievementRows(
                            g.items, areaName, hue, state, palette,
                            onSwap, onLongPress, onEdit, onDelete, onRetag,
                        )
                    }
                } else {
                    AchievementRows(
                        folder.items, areaName, hue, state, palette,
                        onSwap, onLongPress, onEdit, onDelete, onRetag,
                    )
                }
            }
        }
    }
}

/**
 * The achievement rows of ONE list — a whole project folder, or one deliverable group inside it.
 *
 * Reorder is scoped to whatever list it is handed: the arrows swap real slots in the area's flat
 * `achievements`, but only ever with a neighbour from this same list, so a win can't be shuffled out of
 * its deliverable by an arrow tap. Same mechanism the folder level has always used, one level deeper.
 */
@Composable
private fun AchievementRows(
    items: List<IndexedAchievement>,
    areaName: String,
    hue: PillarColor,
    state: SummaryViewModel.ScreenState,
    palette: BragPalette,
    onSwap: (String, Int, Int) -> Unit,
    onLongPress: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetag: (RetagTarget) -> Unit,
) {
    val many = items.size > 1
    items.forEachIndexed { localIndex, ia ->
        val ach = ia.achievement
        val prevFlat = items.getOrNull(localIndex - 1)?.flatIndex
        val nextFlat = items.getOrNull(localIndex + 1)?.flatIndex
        AchievementRow(
            text = achievementDisplay(ach.bullet, ach.metric, null),
            hue = hue,
            palette = palette,
            pinned = isPinned(state.pinnedBullets, ach.bullet),
            count = ach.count,
            showControls = many,
            canUp = prevFlat != null,
            canDown = nextFlat != null,
            onUp = { prevFlat?.let { onSwap(areaName, ia.flatIndex, it) } },
            onDown = { nextFlat?.let { onSwap(areaName, ia.flatIndex, it) } },
            onLongPress = { onLongPress(ach.bullet) },
            onEdit = { onEdit(ach.bullet) },
            onDelete = { onDelete(ach.bullet) },
            onRetag = { onRetag(retagTargetFor(ach, areaName)) },
        )
        Spacer(Modifier.height(Spacing.s2))
    }
}

/**
 * A deliverable heading inside a project folder (v0.34.0). Deliberately NOT the shared
 * [com.bragbuddy.app.ui.common.DeliverableHeader]: that one carries the lifecycle (add entry / rename /
 * mark done / delete) against Home's `DeliverableGroup` model, and none of it belongs on a
 * read-and-copy screen whose only correction path is the ⋮ retag. It borrows the same visual
 * vocabulary — the small hue square — so the two levels still read as one record.
 */
@Composable
private fun DeliverableSubHeader(name: String, hue: PillarColor, palette: BragPalette) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(hue.ink))
        Spacer(Modifier.width(6.dp))
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = palette.text2,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(Spacing.s1))
}

@Composable
private fun BehaviourSection(
    b: SummaryBehaviour,
    framework: Framework,
    palette: BragPalette,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
    onLongPress: (String) -> Unit,
) {
    val hue = goalHue(framework, b.name)
    SectionHeader(b.name, hue, palette, expanded, onToggle, onCopy)
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(Modifier.height(Spacing.s3))
            // Category-level evidence — a flat behaviour's bullets, or (item 4) bullets that fit no
            // named competency. Any evidence from an UNNAMED competency (a model glitch) folds up here
            // too, so it's never lost or shown under an empty sub-heading.
            val looseEvidence = b.evidence + b.competencies.filter { it.name.isBlank() }.flatMap { it.evidence }
            looseEvidence.forEach { ev ->
                AchievementRow(ev, hue, palette, pinned = false, count = 1, showControls = false, canUp = false, canDown = false, onUp = {}, onDown = {}, onLongPress = { onLongPress(ev) }, showMenu = false)
                Spacer(Modifier.height(Spacing.s2))
            }
            // Nested competencies (item 4): the user's category (e.g. "Leadership") is the header
            // above; each named competency is a sub-heading with its own evidence, indented.
            b.competencies.forEach { comp ->
                if (comp.name.isNotBlank() && comp.evidence.isNotEmpty()) {
                    CompetencyGroup(comp, hue, palette, onLongPress)
                    Spacer(Modifier.height(Spacing.s2))
                }
            }
        }
    }
}

@Composable
private fun CompetencyGroup(
    comp: SummaryCompetency,
    hue: PillarColor,
    palette: BragPalette,
    onLongPress: (String) -> Unit,
) {
    Column(Modifier.padding(start = Spacing.s3)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(hue.solid))
            Spacer(Modifier.width(Spacing.s2))
            Text(comp.name, style = MaterialTheme.typography.labelMedium, color = hue.ink, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(Spacing.s2))
        comp.evidence.forEach { ev ->
            AchievementRow(ev, hue, palette, pinned = false, count = 1, showControls = false, canUp = false, canDown = false, onUp = { }, onDown = { }, onLongPress = { onLongPress(ev) }, showMenu = false)
            Spacer(Modifier.height(Spacing.s2))
        }
    }
}

@Composable
private fun DevelopmentSection(
    items: List<String>,
    framework: Framework,
    palette: BragPalette,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRetag: (RetagTarget) -> Unit,
) {
    val hue = pillarColor(framework.pillars.size + 1)
    // Development items ARE real placements (the model files into a DEVELOPMENT area), so they get the
    // full menu — and this is exactly the area the owner's screenshot was about. The retag needs the
    // area's real name, not the hardcoded header below: use the framework's own development pillar so a
    // renamed one still retags correctly.
    val devAreaName = framework.pillars.firstOrNull { it.kind == PillarKind.DEVELOPMENT }?.name
        ?: "Learning & Growth"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(hue.solid))
        Spacer(Modifier.width(Spacing.s2))
        Text(
            "LEARNING & GROWTH",
            style = MaterialTheme.typography.labelMedium,
            color = hue.ink,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            null, tint = palette.text3, modifier = Modifier.size(18.dp),
        )
    }
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column {
            Spacer(Modifier.height(Spacing.s3))
            items.forEach { d ->
                AchievementRow(
                    d, hue, palette, pinned = false, count = 1, showControls = false,
                    canUp = false, canDown = false, onUp = { }, onDown = { },
                    onLongPress = { onLongPress(d) },
                    onEdit = { onEdit(d) },
                    onDelete = { onDelete(d) },
                    // A development line is a bare string in the model's output — its project is UNKNOWN, not none.
                    onRetag = { onRetag(RetagTarget(d, devAreaName, null, projectKnown = false)) },
                )
                Spacer(Modifier.height(Spacing.s2))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    name: String,
    hue: PillarColor,
    palette: BragPalette,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Toggle target = dot + name + chevron (a SEPARATE clickable from Copy, so a Copy tap is never
        // swallowed by the collapse toggle).
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp)).background(hue.solid))
            Spacer(Modifier.width(Spacing.s2))
            Text(name.uppercase(), style = MaterialTheme.typography.labelMedium, color = hue.ink, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                null, tint = palette.text3, modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(Spacing.s2))
        Text(
            "Copy",
            style = MaterialTheme.typography.labelSmall,
            color = palette.text3,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onCopy).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AchievementRow(
    text: String,
    hue: PillarColor,
    palette: BragPalette,
    pinned: Boolean,
    count: Int,
    showControls: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLongPress: () -> Unit,
    /**
     * The ⋮ menu is drawn only where its actions mean something. Behaviour/competency evidence lines are
     * a computed VIEW of a win that already appears under its goal area — "change category or project"
     * has no meaning there (the line has no placement of its own to change), so those rows keep the
     * long-press and omit the menu rather than offer three items that do nothing.
     */
    showMenu: Boolean = true,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRetag: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Long-press still opens the same actions (users have relied on it since v0.12.0) — but it
            // was the ONLY way in, with no affordance. The ⋮ menu is now the discoverable path.
            // detectTapGestures yields to a scroll drag, so the surrounding verticalScroll still works;
            // child arrow/menu taps keep their own.
            .pointerInput(text) { detectTapGestures(onLongPress = { onLongPress() }) },
    ) {
        Box(Modifier.padding(top = 6.dp).size(5.dp).clip(RoundedCornerShape(2.dp)).background(hue.solid))
        Spacer(Modifier.width(Spacing.s3))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.text1, modifier = Modifier.weight(1f))
        if (count > 1) {
            // A repeated notable win — emphasized "×N" chip (distinct from the muted routine roll-up).
            Spacer(Modifier.width(6.dp))
            Text(
                "×$count",
                style = MaterialTheme.typography.labelSmall,
                color = hue.ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(Radii.sm)).background(hue.soft).padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        if (pinned) {
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier.clip(RoundedCornerShape(Radii.sm)).background(palette.primarySoft).padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.PushPin, null, tint = palette.primary, modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(3.dp))
                Text("Pinned", style = MaterialTheme.typography.labelSmall, color = palette.primary, fontWeight = FontWeight.Bold)
            }
        }
        // The design's sanctioned inline reorder tiles stay exactly where they are drawn; the ⋮ menu is
        // added alongside for the actions that have no specced home (edit / delete / re-place).
        if (showControls) {
            Spacer(Modifier.width(4.dp))
            ControlIcon(Icons.Filled.KeyboardArrowUp, canUp, palette, onUp)
            ControlIcon(Icons.Filled.KeyboardArrowDown, canDown, palette, onDown)
        }
        if (showMenu) AchievementMenu(palette = palette, onEdit = onEdit, onDelete = onDelete, onRetag = onRetag)
    }
}

/**
 * The per-line ⋮ menu. Modelled on the app's own [com.bragbuddy.app.ui.common.EntryBulletRow] kebab —
 * NOT on the Design System, which specifies no menu primitive anywhere (flagged: its Summary spec draws
 * an inline Pinned/up/down/regenerate row instead, and the existing Home kebab is itself an undocumented
 * code-side invention). Matching code precedent keeps the two menus consistent with each other.
 */
@Composable
private fun AchievementMenu(
    palette: BragPalette,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRetag: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Outlined.MoreVert, "More", tint = palette.text3, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Change category or project") },
                onClick = { open = false; onRetag() },
                leadingIcon = { Icon(Icons.Outlined.DriveFileMove, null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Edit line") },
                onClick = { open = false; onEdit() },
                leadingIcon = { Icon(Icons.Outlined.Edit, null, modifier = Modifier.size(18.dp)) },
            )
            DropdownMenuItem(
                text = { Text("Delete line") },
                onClick = { open = false; onDelete() },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun ControlIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(Radii.sm))
            .background(palette.surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (enabled) palette.text2 else palette.text3.copy(alpha = 0.4f), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun RolledUpRow(text: String, count: Int, hue: PillarColor, palette: BragPalette, onLongPress: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2)
            .pointerInput(text) { detectTapGestures(onLongPress = { onLongPress() }) }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Layers, null, tint = palette.text3, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(Spacing.s2))
        Text(text, style = MaterialTheme.typography.bodySmall, color = palette.text2, modifier = Modifier.weight(1f))
        if (count > 0) {
            Spacer(Modifier.width(Spacing.s2))
            Text(
                "×$count",
                style = MaterialTheme.typography.labelSmall,
                color = hue.ink,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(hue.soft).padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * What didn't make the cut — expandable, in full, and restorable (v0.31.0).
 *
 * Two layers, because they are two different things:
 *  - [items] = the REAL dropped wins, derived client-side (rollup candidates the model didn't use). Each
 *    is its own full text and knows its own goal area, so Restore needs no destination picker and puts
 *    back exactly what was left out. "Restore all" acts on these.
 *  - [notes] = the model's own categorical explanation ("Routine check-ins · condensed to keep to one
 *    page"). PROSE ONLY, and deliberately not restorable. Restoring one used to inject a bullet reading
 *    literally "Routine check-ins" — a label pretending to be an achievement. It explains what happened;
 *    it is not a thing you can get back. (v0.13.0 flagged exactly this; v0.22.0 shipped restore anyway.)
 *
 * So the header counts [items] — the things you can actually act on. When nothing was dropped, the panel
 * is an explanation with nothing to restore, which is the truth.
 */
@Composable
private fun SetAsidePanel(
    items: List<SummaryViewModel.SetAsideItem>,
    notes: List<com.bragbuddy.app.data.ai.SetAsideNote>,
    open: Boolean,
    palette: BragPalette,
    onToggle: () -> Unit,
    onRestoreItem: (SummaryViewModel.SetAsideItem) -> Unit,
    onRestoreAll: () -> Unit,
) {
    val count = items.size
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface).padding(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2).clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.FilterList, null, tint = palette.text3, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.s2))
            Text(
                "$count ${if (count == 1) "item" else "items"} set aside",
                style = MaterialTheme.typography.bodySmall,
                color = palette.text2,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = palette.text3, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                // The model's own reason, when it gave one; else the design's authoritative copy.
                val why = notes.mapNotNull { n ->
                    listOfNotNull(n.what.takeIf { it.isNotBlank() }, n.why.takeIf { it.isNotBlank() }).joinToString(" — ").takeIf { it.isNotBlank() }
                }
                Text(
                    if (why.isNotEmpty()) why.joinToString("  ·  ")
                    else "Routine check-ins and duplicates were condensed to keep this to one page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s2))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Nothing was deleted from your record. Tap any to add it back.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text3,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Restore all",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(Radii.sm)).clickable(onClick = onRestoreAll).padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.s2))
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.text3))
                        Spacer(Modifier.width(Spacing.s2))
                        Column(Modifier.weight(1f)) {
                            // Full text, never truncated — "I need to be able to tap and see that
                            // completely" is the whole point of this panel.
                            Text(
                                achievementDisplay(item.bullet, item.metric, null),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.text2,
                            )
                            Text(item.area, style = MaterialTheme.typography.labelSmall, color = palette.text3)
                        }
                        Spacer(Modifier.width(Spacing.s2))
                        Text(
                            "Restore",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Radii.sm)).clickable { onRestoreItem(item) }.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(stale: Boolean, palette: BragPalette, onRegenerate: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (stale) {
            Row(
                Modifier.clip(RoundedCornerShape(999.dp)).background(palette.extraSoft).clickable(onClick = onRegenerate).padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Refresh, null, tint = palette.extraInk, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("New entries since — Regenerate", style = MaterialTheme.typography.labelSmall, color = palette.extraInk, fontWeight = FontWeight.Bold)
            }
        } else {
            Text("Up to date", style = MaterialTheme.typography.labelSmall, color = palette.positive, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------- generate sheet ----------------

@Composable
private fun GenerateSheet(
    palette: BragPalette,
    state: SummaryViewModel.ScreenState,
    onSelectPeriod: (SummaryPeriod) -> Unit,
    onSelectLength: (SummaryLength) -> Unit,
    onGenerate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                // This sheet is the tallest (period + 3 length rows + CTA). Scroll rather than push its
                // top off-screen on a short display / large font scale.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(palette.text3.copy(alpha = 0.35f)))
            Spacer(Modifier.height(16.dp))
            Text("Generate summary", style = MaterialTheme.typography.titleLarge, color = palette.text1)
            Spacer(Modifier.height(Spacing.s4))

            Text("PERIOD", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.s2))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SummaryPeriod.entries.forEach { p ->
                    val selected = p == state.period
                    Text(
                        p.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else palette.text2,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(Radii.sm))
                            .background(if (selected) palette.primary else Color.Transparent)
                            .clickable { onSelectPeriod(p) }.padding(vertical = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s2))
            Text("${state.window.rangeText} · ${state.entryCount} entries", style = MaterialTheme.typography.bodySmall, color = palette.text3)

            Spacer(Modifier.height(Spacing.s4))
            Text("LENGTH", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.s2))
            SummaryLength.entries.forEach { l ->
                LengthRow(l, selected = l == state.length, palette = palette) { onSelectLength(l) }
                Spacer(Modifier.height(Spacing.s2))
            }

            Spacer(Modifier.height(Spacing.s3))
            val upToDate = state.cached != null && !state.isStale
            PrimaryButton(
                text = when {
                    upToDate -> "Up to date"
                    state.cached != null -> "Regenerate"
                    else -> "Generate"
                },
                palette = palette,
                enabled = state.hasContent,
                leadingSpark = !upToDate,
                onClick = onGenerate,
            )
            if (!state.hasContent) {
                Spacer(Modifier.height(Spacing.s2))
                Text("No entries in this period yet.", style = MaterialTheme.typography.bodySmall, color = palette.text3, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // + the app's own bottom bar/FAB, which MainScaffold draws OVER this screen.
            Spacer(Modifier.height(18.dp + bottomInset + LocalBottomBarInset.current))
        }
    }
}

@Composable
private fun LengthRow(length: SummaryLength, selected: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md))
            .background(if (selected) palette.primarySoft else palette.surface2)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(999.dp)).background(if (selected) palette.primary else palette.surface),
            contentAlignment = Alignment.Center,
        ) { if (selected) Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.White)) }
        Spacer(Modifier.width(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(length.label, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.Bold)
            Text(length.sub, style = MaterialTheme.typography.labelSmall, color = palette.text3)
        }
    }
}

// ---------------- shared bits ----------------

@Composable
private fun HeaderPill(text: String, fill: Color, ink: Color, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = ink,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(fill).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun PrimaryButton(text: String, palette: BragPalette, enabled: Boolean, leadingSpark: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.primary else palette.surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingSpark) {
            Icon(Icons.Outlined.AutoAwesome, null, tint = if (enabled) Color.White else palette.text3, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (enabled) Color.White else palette.text3, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SparkTile(palette: BragPalette) {
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(palette.primarySoft),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.AutoAwesome, null, tint = palette.primary, modifier = Modifier.size(30.dp)) }
}

@Composable
private fun GeneratingOverlay(palette: BragPalette) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize()
            .background(palette.bg.copy(alpha = 0.7f))
            // Consume touches so the summary underneath can't be edited while it's being replaced.
            .clickable(interactionSource = noRipple, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = palette.primary, strokeWidth = 2.5.dp)
            Spacer(Modifier.height(Spacing.s3))
            Text("Curating your summary…", style = MaterialTheme.typography.bodyMedium, color = palette.text2)
        }
    }
}

// ---------------- pointer edit / delete / restore (Phase 1) ----------------

/** Long-press on a summary pointer → Edit / Delete. Custom-scrim sheet (never a Material sheet). */
@Composable
private fun PointerActionSheet(
    palette: BragPalette,
    text: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .padding(horizontal = 18.dp).padding(top = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(palette.text3.copy(alpha = 0.35f)))
            Spacer(Modifier.height(16.dp))
            Text("THIS LINE", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.s2))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = palette.text1, maxLines = 3)
            Spacer(Modifier.height(Spacing.s4))
            PointerActionButton(Icons.Outlined.Edit, "Edit line", palette.text1, palette, onEdit)
            Spacer(Modifier.height(Spacing.s2))
            PointerActionButton(Icons.Outlined.DeleteOutline, "Delete line", MaterialTheme.colorScheme.error, palette, onDelete)
            Spacer(Modifier.height(Spacing.s3))
            Text(
                "Only changes this summary — your saved record on Home isn't affected.",
                style = MaterialTheme.typography.labelSmall,
                color = palette.text3,
            )
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // + the app's own bottom bar/FAB, which MainScaffold draws OVER this screen.
            Spacer(Modifier.height(18.dp + bottomInset + LocalBottomBarInset.current))
        }
    }
}

@Composable
private fun PointerActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    ink: Color,
    palette: BragPalette,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = ink, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(Spacing.s3))
        Text(label, style = MaterialTheme.typography.titleSmall, color = ink, fontWeight = FontWeight.SemiBold)
    }
}

/** Edit a pointer's wording. Reuses the app's AlertDialog editor pattern (bounded, scrolls internally). */
@Composable
private fun EditPointerDialog(
    palette: BragPalette,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Edit line", color = palette.text1) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8)
        },
        containerColor = palette.surface,
    )
}

/**
 * **Retag a summary line** (v0.31.0) — pick a placement category, then any project under it, or no
 * specific project. Mirrors the entry-detail Recategorize sheet's shape so the two read the same.
 *
 * Every project the user has is reachable here, including ones this summary never mentions and ones the
 * AI has never filed into — that is the owner's explicit ask ("tag any card to another existing project
 * even if it's not yet used"). Reachability is via its OWN category, because a project belongs to a
 * category ([ProjectEntity] is unique by `(name, goalArea)`); picking a project therefore also settles
 * the category, which is exactly right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RetagSheet(
    palette: BragPalette,
    line: String,
    currentArea: String,
    /** The line's CURRENT project (null = none). Preselected, so opening the sheet to change only the
     *  CATEGORY can't silently un-file the entry from a project the user never touched. */
    currentProject: String?,
    /**
     * False when [currentProject] is **unknown**, not "none" — DEVELOPMENT lines are plain strings in the
     * model's output and carry no project, so the sheet has nothing to preselect.
     *
     * The distinction is load-bearing: treating unknown as "none" rendered "No specific project" as the
     * chosen answer, and Apply then wrote `OUTSIDE_PROJECT` over a project the user never touched — plus
     * (once the deliverable axis existed) wiped its deliverable too. Unknown gets the same treatment as
     * the deliverable axis: an explicit "Leave as is", opt-in, and untouched means the host keeps each
     * entry's own value.
     */
    currentProjectKnown: Boolean = true,
    framework: Framework,
    folders: List<SummaryViewModel.FolderRef>,
    deliverables: List<DeliverableEntity>,
    /**
     * (category, project, deliverable, createDeliverable, **deliverableTouched**, **projectTouched**).
     *
     * A `*Touched` false = "the user didn't say" → the host MUST leave each entry's existing value alone.
     * This sheet, unlike the entry-detail one, works on AI-written prose that resolves back to its source
     * entries only at Apply time (and a merged card resolves to several, which may differ), so anything
     * it cannot know is opt-in rather than pre-filled with a guess — see the "Leave as is" chips.
     * `projectTouched` is always true when the project WAS knowable (it was preselected from the truth,
     * so writing it back is a no-op).
     */
    onApply: (String, String?, String?, Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    val categories = remember(framework) { Recategorize.placementCategories(framework) }
    // Keyed on the LINE, not the area: two lines in the same area are different entries with different
    // projects, and keying on the area would hand the second one the first one's selection.
    // The category the sheet OPENS on: the line's own area, or NOTHING when that names no placement
    // category (an off-framework area — e.g. one renamed since the summary was generated).
    //
    // Nothing, deliberately. Two wrong answers were tried here first. Measuring "changed" against the
    // line's RAW area made `categoryChanged` true before the user touched anything, silently settling
    // the placement and wiping the deliverable of a win they only opened to look at. Falling back to
    // `categories.first()` fixed that and introduced a worse one: the sheet preselected an arbitrary
    // category, reported `categoryChanged == false` because it was comparing the fallback to itself, and
    // Apply then wrote that category while "Leave as is" faithfully kept the entry's project — a durable
    // (project, goalArea) pair that exists nowhere, invisible to every editor (they all scope by
    // (name, goalArea)) and beyond the reach of any later rename. The sheet was moving the win to a
    // category the user never picked, while telling them nothing had changed.
    //
    // A sheet that doesn't know the answer must ASK, not guess. Null preselects no radio and leaves
    // Apply disabled (it is already gated on `selectedCategory != null`), so the only way out is an
    // explicit pick — which makes `categoryChanged` true honestly, settles the project axis visibly, and
    // renders the deliverable axis. Opening the sheet to look and dismissing still writes nothing.
    val initialCategory = remember(line, currentArea, categories) {
        categories.firstOrNull { it.name.equals(currentArea, ignoreCase = true) }?.name
    }
    var selectedCategory by rememberSaveable(line) { mutableStateOf(initialCategory) }
    // Scoped to the current category, mirroring Recategorize.defaultFolder: a folder is unique by
    // (name, goalArea), so a project only preselects under the category it actually belongs to.
    val initialFolder = remember(line, currentProject, initialCategory, folders) {
        // Resolve to the FOLDER's own spelling rather than echoing the model's: `currentProject` is an
        // unconstrained model-authored string, and Apply writes it straight into the record.
        //
        // Scoped by `initialCategory` — the category the sheet actually OPENS on — not the line's raw
        // area, so the preselect can never contradict the chips below it. When the area names no live
        // category, `initialCategory` is null and this correctly resolves to null too: nothing is
        // preselected on either axis, because the sheet genuinely doesn't know where the win lives.
        currentProject?.let { p ->
            folders.firstOrNull {
                it.name.equals(p, ignoreCase = true) && it.goalArea.equals(initialCategory.orEmpty(), ignoreCase = true)
            }?.name
        }
    }
    var selectedFolder by rememberSaveable(line) { mutableStateOf(initialFolder) }
    // "Known" must ALSO mean resolvable. A project name that no longer matches a live folder (renamed or
    // deleted since the summary was generated) cannot be preselected — so claiming to know it asserts
    // "no project" for a win that has one, and Apply writes that in. Treat it as unknown: offer "Leave as
    // is" and let the host keep the entry's own project. `currentProject == null` with `known` is the
    // genuine "Outside" answer and stays resolved.
    val projectResolved = currentProjectKnown && (currentProject == null || initialFolder != null)
    // EXPLICIT answers only. The real question — "has the project been decided?" — is DERIVED below;
    // storing it in this flag is what let a category change strand it.
    var projectTouched by rememberSaveable(line) { mutableStateOf(false) }
    // The deliverable axis (v0.33.0). THREE states, not two — this is the fix for a HIGH found in the
    // v0.33.0 accuracy assessment. It previously hardcoded "no deliverable" as the selection, so a user
    // who opened this sheet to fix the CATEGORY of a win they had tap-in filed into a deliverable had
    // that deliverable (and its anchor) silently wiped by Apply — durable, no undo, under a snackbar
    // reading "Re-filed in your record". That is the v0.31.0 project-axis HIGH, reproduced verbatim.
    //
    // The root cause was pretending to know: a summary line is AI prose whose source entries are only
    // resolved at Apply time. So "untouched" is now its own state and means LEAVE IT ALONE, which the
    // host honours per-entry — correct even for a merged card whose entries differ.
    var deliverableTouched by rememberSaveable(line) { mutableStateOf(false) }
    var selectedDeliverable by rememberSaveable(line) { mutableStateOf<String?>(null) }
    // "Leave as is" only means anything while the entry stays in the project it's already in. Moving it
    // elsewhere settles the deliverable question by itself — the old one lives in the OLD project and
    // cannot come along. Without this, "untouched" let the host re-resolve the entry's existing tag
    // against the DESTINATION, and a same-named deliverable there ("Phase 1", "Q1", "Discovery" — names
    // that repeat across projects) would silently adopt the win and durably anchor it to a deliverable
    // the user never chose. Derived, not a flag, so returning to the original project restores "as is".
    // A deliverable named right here, staged rather than created — Apply creates and files in one
    // locked step, so a fast tap can't beat the insert and have its tag silently dropped.
    var newDeliverable by rememberSaveable(line) { mutableStateOf<String?>(null) }
    var namingDeliverable by rememberSaveable(line) { mutableStateOf(false) }
    var newDeliverableText by rememberSaveable(line) { mutableStateOf("") }

    // Moving the win settles the deliverable on its own — see above. "Moved" means EITHER parent moved:
    // a deliverable is identified by (name, project, goalArea), and a project only by (name, goalArea),
    // so the same folder name legitimately exists under two categories. Comparing the project NAME alone
    // read a category-only move as "unchanged", left "Leave as is" active, and let the win be adopted
    // AND anchored into the destination category's own, unrelated same-named deliverable — the exact
    // failure this guard exists to stop, surviving along the axis it forgot to check.
    //
    // `projectKnown` is false for DEVELOPMENT summary lines, whose project can't be resolved at render
    // time. There "unchanged" is unknowable, so an explicit pick is treated as a move.
    val categoryChanged = !selectedCategory.orEmpty().equals(initialCategory.orEmpty(), ignoreCase = true)
    // Is the PROJECT question answered? Three ways, and it must be DERIVED, not stored:
    //  - we knew the answer and preselected it (writing the truth back is a no-op);
    //  - the user picked one;
    //  - the CATEGORY moved, which answers it whether they like it or not: a folder is unique by
    //    (name, goalArea), so the old one cannot exist in the category they just chose.
    // That last clause fixes a HIGH. A development line (project unknown) whose category was changed kept
    // "Leave as is" selected, so Apply wrote the OLD project name under the NEW category — a folder that
    // lives nowhere, durably anchored, invisible to every editor (they all scope by (name, goalArea)) and
    // beyond the reach of any later rename. The code even asserted "a known project can't survive a
    // category change" while this very branch let it do exactly that.
    val projectSettled = projectResolved || projectTouched || categoryChanged
    val projectChanged =
        if (!projectResolved) projectSettled
        else !selectedFolder.orEmpty().equals(currentProject.orEmpty(), ignoreCase = true)
    val placementChanged = categoryChanged || projectChanged
    val effectiveTouched = deliverableTouched || placementChanged

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                // The category × folder list is as long as the user's framework — scroll, never clip.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(palette.text3.copy(alpha = 0.35f)))
            Spacer(Modifier.height(16.dp))
            Text("Where does this belong?", style = MaterialTheme.typography.titleMedium, color = palette.text1)
            Spacer(Modifier.height(Spacing.s2))
            Text(line, style = MaterialTheme.typography.bodySmall, color = palette.text3, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Spacing.s4))

            if (categories.isEmpty()) {
                Text("Add a category in the Framework tab first.", style = MaterialTheme.typography.bodySmall, color = palette.text3)
            } else {
                categories.forEach { cat ->
                    val isSel = selectedCategory?.equals(cat.name, ignoreCase = true) == true
                    // Changing the category invalidates both narrower axes — they belong to the old one.
                    val pickCategory = {
                        if (!isSel) {
                            val backHome = cat.name.equals(initialCategory.orEmpty(), ignoreCase = true)
                            selectedCategory = cat.name
                            // Returning to the line's OWN category restores its real project instead of
                            // leaving it blank: a round trip (A → B → A) landed on "No specific project",
                            // so Apply silently un-filed a win the user never meant to touch and took its
                            // deliverable with it. Any OTHER category genuinely cannot hold that folder,
                            // so there it resets.
                            selectedFolder = if (backHome) initialFolder else null
                            selectedDeliverable = null
                            newDeliverable = null
                            namingDeliverable = false
                            deliverableTouched = false
                            // Explicit answers only; `projectSettled` re-derives the rest.
                            projectTouched = false
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md))
                            .clickable(onClick = pickCategory)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSel,
                            onClick = pickCategory,
                            colors = RadioButtonDefaults.colors(selectedColor = palette.primary, unselectedColor = palette.text3),
                        )
                        Text(cat.name, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.SemiBold)
                    }
                    if (isSel) {
                        val catFolders = folders.filter { it.goalArea.equals(cat.name, ignoreCase = true) }
                        FlowRow(
                            Modifier.fillMaxWidth().padding(start = 30.dp, bottom = Spacing.s2),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Offered only when the line's project is genuinely UNKNOWN (a DEVELOPMENT
                            // line). Without it the sheet showed "No specific project" as though it were
                            // the answer, and Apply un-filed a win from a project the user never touched.
                            if (!projectResolved && !categoryChanged) {
                                SelectChip("Leave as is", selected = !projectSettled, palette = palette) {
                                    projectTouched = false
                                    selectedFolder = null
                                    selectedDeliverable = null
                                    newDeliverable = null
                                    namingDeliverable = false
                                    deliverableTouched = false
                                }
                            }
                            SelectChip(
                                NO_PROJECT_LABEL,
                                selected = projectSettled && selectedFolder == null,
                                palette = palette,
                            ) {
                                projectTouched = true
                                selectedFolder = null
                                selectedDeliverable = null
                                newDeliverable = null
                                namingDeliverable = false
                                // Moving to "no project" DOES settle the deliverable — there is nowhere
                                // for one to live — so this counts as touching the axis.
                                deliverableTouched = true
                            }
                            catFolders.forEach { f ->
                                SelectChip(
                                    f.name,
                                    selected = projectSettled && selectedFolder?.equals(f.name, ignoreCase = true) == true,
                                    palette = palette,
                                ) {
                                    if (!projectSettled || selectedFolder?.equals(f.name, ignoreCase = true) != true) {
                                        projectTouched = true
                                        selectedFolder = f.name
                                        selectedDeliverable = null
                                        newDeliverable = null
                                        namingDeliverable = false
                                        deliverableTouched = false
                                    }
                                }
                            }
                        }
                        // The deliverable level — only under a real project, the only place one exists.
                        // Also requires the project to be SETTLED: with an unknown project left
                        // "as is" there is no project to scope a deliverable list to.
                        if (selectedFolder != null && projectSettled) {
                            val catDeliverables = Recategorize.deliverablesFor(cat.name, selectedFolder, deliverables)
                            Text(
                                DELIVERABLE_LABEL.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.text3,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 30.dp),
                            )
                            Spacer(Modifier.height(Spacing.s2))
                            FlowRow(
                                Modifier.fillMaxWidth().padding(start = 30.dp, bottom = Spacing.s2),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // "Leave as is" is the DEFAULT and is not a no-op chip: it is the only
                                // honest answer this sheet has, because it doesn't know the line's
                                // deliverable. Picking anything else overrides it. It is offered ONLY
                                // while the win stays in its own project — once it's moving elsewhere
                                // there is no "as is" to keep, and showing it against a different
                                // project's list would be claiming something incoherent.
                                if (!placementChanged) {
                                    SelectChip(
                                        "Leave as is",
                                        selected = !deliverableTouched,
                                        palette = palette,
                                    ) {
                                        deliverableTouched = false
                                        selectedDeliverable = null
                                        newDeliverable = null
                                        namingDeliverable = false
                                    }
                                }
                                SelectChip(
                                    "None",
                                    selected = effectiveTouched && selectedDeliverable == null && newDeliverable == null,
                                    palette = palette,
                                ) {
                                    deliverableTouched = true
                                    selectedDeliverable = null; newDeliverable = null; namingDeliverable = false
                                }
                                catDeliverables.forEach { d ->
                                    SelectChip(
                                        if (d.done) "${d.name} · Done" else d.name,
                                        selected = selectedDeliverable?.equals(d.name, ignoreCase = true) == true,
                                        palette = palette,
                                    ) {
                                        deliverableTouched = true
                                        selectedDeliverable = d.name; newDeliverable = null; namingDeliverable = false
                                    }
                                }
                                newDeliverable?.let { pending ->
                                    SelectChip("$pending · new", selected = true, palette = palette) {}
                                }
                                if (newDeliverable == null && !namingDeliverable) {
                                    SelectChip("+ New", selected = false, palette = palette) {
                                        namingDeliverable = true; newDeliverableText = ""
                                    }
                                }
                            }
                            if (namingDeliverable) {
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 30.dp, bottom = Spacing.s2),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier.weight(1f).clip(RoundedCornerShape(Radii.md))
                                            .background(palette.surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        if (newDeliverableText.isEmpty()) {
                                            Text(
                                                "Name this ${DELIVERABLE_LABEL.lowercase()}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = palette.text3,
                                            )
                                        }
                                        BasicTextField(
                                            value = newDeliverableText,
                                            onValueChange = { newDeliverableText = it },
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(
                                                color = palette.text1, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                            ),
                                            cursorBrush = SolidColor(palette.primary),
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    SelectChip("Add", selected = newDeliverableText.isNotBlank(), palette = palette) {
                                        val n = newDeliverableText.trim()
                                        if (n.isNotEmpty()) {
                                            newDeliverable = n
                                            selectedDeliverable = null
                                            namingDeliverable = false
                                            deliverableTouched = true
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    SelectChip("Cancel", selected = false, palette = palette) {
                                        namingDeliverable = false; newDeliverableText = ""
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.s3))
                // Conditional, because it is: when the line can't be matched back to a saved entry this
                // falls back to a summary-only move. Promising the record outright would be a claim the
                // code can't always honour — the snackbar afterwards says which actually happened.
                Text(
                    "Where this line can be matched to a saved entry, your record is corrected too — so Home and future summaries agree.",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s3))
                PrimaryButton("Apply", palette, enabled = selectedCategory != null) {
                    selectedCategory?.let {
                        // Honour a name still sitting in the "+ New" field. Requiring "Add" first and
                        // silently binning it otherwise punishes the user for a step that looks optional —
                        // they typed the name and pressed the button that says Apply.
                        val staged = newDeliverable
                            ?: newDeliverableText.trim().takeIf { t -> namingDeliverable && t.isNotEmpty() }
                        onApply(
                            it,
                            selectedFolder,
                            staged ?: selectedDeliverable,
                            staged != null,
                            effectiveTouched || staged != null,
                            projectSettled,
                        )
                    }
                }
            }
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // + the app's own bottom bar/FAB, which MainScaffold draws OVER this screen.
            Spacer(Modifier.height(18.dp + bottomInset + LocalBottomBarInset.current))
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) palette.primary else palette.text2,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.primarySoft else palette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

private fun achievementDisplay(bullet: String, metric: String?, project: String?): String {
    val base = if (!metric.isNullOrBlank() && !bullet.contains(metric.trim(), ignoreCase = true)) "$bullet — ${metric.trim()}" else bullet
    val proj = project?.trim()?.takeIf { it.isNotBlank() }
    return if (proj != null) "$base  ·  $proj" else base
}
