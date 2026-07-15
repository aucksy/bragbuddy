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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.ai.SummaryBehaviour
import com.bragbuddy.app.data.ai.SummaryCompetency
import com.bragbuddy.app.data.ai.SummaryGoalArea
import com.bragbuddy.app.data.framework.Framework
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

    var showGenerate by remember { mutableStateOf(false) }
    // Long-press edit/delete (feature #1) + restore-from-set-aside (feature #5) overlays. Hoisted here
    // so their custom-scrim sheets/dialogs cover the WHOLE screen (like GenerateSheet), not the padded
    // content Column.
    var pointerAction by remember { mutableStateOf<String?>(null) }
    var pointerEdit by remember { mutableStateOf<String?>(null) }
    var restoreNote by remember { mutableStateOf<String?>(null) }

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
                        onRestoreRequest = { note -> restoreNote = note },
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
        if (restoreNote != null && s != null) {
            RestorePickerSheet(
                palette = palette,
                note = restoreNote!!,
                framework = s.framework,
                onPick = { area -> restoreNote?.let { viewModel.restoreSetAside(it, area) }; restoreNote = null },
                onDismiss = { restoreNote = null },
            )
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
    onRestoreRequest: (String) -> Unit,
) {
    val cached = state.cached ?: return
    val result = cached.result
    var setAsideOpen by remember { mutableStateOf(false) }
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
                )
                Spacer(Modifier.height(Spacing.s4))
            }
            if (result.setAside.isNotEmpty()) {
                SetAsidePanel(
                    count = result.setAside.size,
                    open = setAsideOpen,
                    notes = result.setAside,
                    palette = palette,
                    onToggle = { setAsideOpen = !setAsideOpen },
                    onRestore = onRestoreRequest,
                )
            }
            Spacer(Modifier.height(contentBottomPadding + 12.dp))
        }
    }
}

// ---------------- document sections ----------------

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
                    ProjectFolder(folder, area.name, hue, state, palette, onSwap, onLongPress)
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
                val many = folder.items.size > 1
                folder.items.forEachIndexed { localIndex, ia ->
                    val ach = ia.achievement
                    val prevFlat = folder.items.getOrNull(localIndex - 1)?.flatIndex
                    val nextFlat = folder.items.getOrNull(localIndex + 1)?.flatIndex
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
                    )
                    Spacer(Modifier.height(Spacing.s2))
                }
            }
        }
    }
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
                AchievementRow(ev, hue, palette, pinned = false, count = 1, showControls = false, canUp = false, canDown = false, onUp = {}, onDown = {}, onLongPress = { onLongPress(ev) })
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
            AchievementRow(ev, hue, palette, pinned = false, count = 1, showControls = false, canUp = false, canDown = false, onUp = {}, onDown = {}, onLongPress = { onLongPress(ev) })
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
) {
    val hue = pillarColor(framework.pillars.size + 1)
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
                AchievementRow(d, hue, palette, pinned = false, count = 1, showControls = false, canUp = false, canDown = false, onUp = {}, onDown = {}, onLongPress = { onLongPress(d) })
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
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Long-press → edit/delete this line (feature #1). detectTapGestures yields to a scroll
            // drag, so the surrounding verticalScroll still works; child arrow taps keep their own.
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
        if (showControls) {
            Spacer(Modifier.width(4.dp))
            ControlIcon(Icons.Filled.KeyboardArrowUp, canUp, palette, onUp)
            ControlIcon(Icons.Filled.KeyboardArrowDown, canDown, palette, onDown)
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

@Composable
private fun SetAsidePanel(
    count: Int,
    open: Boolean,
    notes: List<com.bragbuddy.app.data.ai.SetAsideNote>,
    palette: BragPalette,
    onToggle: () -> Unit,
    onRestore: (String) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface).padding(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2).clickable(onClick = onToggle).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.FilterList, null, tint = palette.text3, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.s2))
            Text("$count set aside", style = MaterialTheme.typography.bodySmall, color = palette.text2, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = palette.text3, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(visible = open) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    "Condensed to keep the summary crisp — nothing was deleted from your record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s2))
                notes.forEach { n ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 6.dp).size(4.dp).clip(RoundedCornerShape(2.dp)).background(palette.text3))
                        Spacer(Modifier.width(Spacing.s2))
                        Column(Modifier.weight(1f)) {
                            Text(n.what, style = MaterialTheme.typography.bodySmall, color = palette.text2, fontWeight = FontWeight.SemiBold)
                            if (n.why.isNotBlank()) Text(n.why, style = MaterialTheme.typography.bodySmall, color = palette.text3)
                        }
                        Spacer(Modifier.width(Spacing.s2))
                        // Restore this note back into the summary (feature #5).
                        Text(
                            "Restore",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(Radii.sm)).clickable { onRestore(n.what) }.padding(horizontal = 8.dp, vertical = 4.dp),
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
            Spacer(Modifier.height(18.dp + bottomInset))
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
            Spacer(Modifier.height(18.dp + bottomInset))
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

/** Pick which goal area a restored set-aside note lands in (feature #5). */
@Composable
private fun RestorePickerSheet(
    palette: BragPalette,
    note: String,
    framework: Framework,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    val areas = framework.pillars.filter { it.kind != PillarKind.BEHAVIOUR }.map { it.name }
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
            Text("Restore to which section?", style = MaterialTheme.typography.titleMedium, color = palette.text1)
            Spacer(Modifier.height(Spacing.s2))
            Text(note, style = MaterialTheme.typography.bodySmall, color = palette.text3, maxLines = 2)
            Spacer(Modifier.height(Spacing.s4))
            if (areas.isEmpty()) {
                Text("No goal areas in your framework yet.", style = MaterialTheme.typography.bodySmall, color = palette.text3)
            } else {
                areas.forEach { area ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2).clickable { onPick(area) }.padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(area, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(Spacing.s2))
                }
            }
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Spacer(Modifier.height(18.dp + bottomInset))
        }
    }
}

private fun achievementDisplay(bullet: String, metric: String?, project: String?): String {
    val base = if (!metric.isNullOrBlank() && !bullet.contains(metric.trim(), ignoreCase = true)) "$bullet — ${metric.trim()}" else bullet
    val proj = project?.trim()?.takeIf { it.isNotBlank() }
    return if (proj != null) "$base  ·  $proj" else base
}
