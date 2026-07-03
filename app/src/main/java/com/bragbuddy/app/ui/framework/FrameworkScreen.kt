package com.bragbuddy.app.ui.framework

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.PillarColor
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing
import com.bragbuddy.app.ui.theme.pillarColor

/**
 * The Framework editor (Design System §3): your **categories**, grouped by the two axes — *What you
 * did* (goal categories) and *How you did it* (behaviour & growth). Each card shows a ramp colour,
 * name, and an editable description. "Refine by voice" opens a distinct sheet to add / rename /
 * remove / re-describe categories by speaking; the AI applies your instruction to the current set
 * and shows editable cards for a one-tap confirm. The company name is never asked.
 */
@Composable
fun FrameworkScreen(
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: FrameworkViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val refine by viewModel.refine.collectAsStateWithLifecycle()

    var editTarget by remember { mutableStateOf<Pillar?>(null) }
    var showAdd by remember { mutableStateOf(false) }

    val hueOf: (Pillar) -> PillarColor = { p -> pillarColor(framework.pillars.indexOfFirst { it.id == p.id }) }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(Spacing.s4))
            Text("Your framework", style = MaterialTheme.typography.headlineLarge, color = palette.text1)
            Text(
                "${framework.pillars.size} categories · how your work is judged",
                style = MaterialTheme.typography.bodySmall,
                color = palette.text3,
            )
            Spacer(Modifier.height(Spacing.s4))

            if (framework.goalAreas.isNotEmpty()) {
                AxisLabel("What you did")
                framework.goalAreas.forEach { p ->
                    CategoryRow(p, hueOf(p), palette, onEdit = { editTarget = p }, onRemove = { viewModel.remove(p.id) })
                    Spacer(Modifier.height(Spacing.s3))
                }
            }

            val howPillars = framework.behaviours + framework.development
            if (howPillars.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s2))
                AxisLabel("How you did it")
                howPillars.forEach { p ->
                    CategoryRow(p, hueOf(p), palette, onEdit = { editTarget = p }, onRemove = { viewModel.remove(p.id) })
                    Spacer(Modifier.height(Spacing.s3))
                }
            }

            DashedAddButton("Add a category", palette) { showAdd = true }
            Spacer(Modifier.height(contentBottomPadding + 76.dp))
        }

        // Pinned "Refine by voice" primary action.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = contentBottomPadding + Spacing.s4, start = Spacing.screen, end = Spacing.screen),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primary)
                    .clickable { viewModel.openRefine() }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Refine by voice", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        if (refine !is RefineState.Hidden) {
            RefineSheet(
                state = refine,
                palette = palette,
                onDismiss = viewModel::cancelRefine,
                onSetMode = viewModel::setRefineMode,
                onTextChange = viewModel::onDescriptionChange,
                onRequestVoice = viewModel::startVoice,
                onStopVoice = viewModel::stopVoice,
                onBuild = viewModel::buildFromDescription,
                onRenameReview = viewModel::renameReviewPillar,
                onRemoveReview = viewModel::removeReviewPillar,
                onConfirm = viewModel::confirmReview,
                onStartOver = viewModel::backToInput,
            )
        }
    }

    editTarget?.let { target ->
        CategoryDialog(
            title = "Edit category",
            initialName = target.name,
            initialDescription = target.blurb,
            palette = palette,
            onConfirm = { name, desc -> viewModel.editCategory(target.id, name, desc); editTarget = null },
            onDismiss = { editTarget = null },
        )
    }

    if (showAdd) {
        AddCategoryDialog(
            palette = palette,
            onConfirm = { name, desc, kind -> viewModel.addCategory(name, desc, kind); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun AxisLabel(text: String) {
    val palette = BragBuddyTheme.palette
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = palette.text3,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = Spacing.s2),
    )
}

@Composable
private fun CategoryRow(
    pillar: Pillar,
    hue: PillarColor,
    palette: BragPalette,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
            .padding(Spacing.card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(top = 2.dp).size(11.dp).clip(RoundedCornerShape(3.dp)).background(hue.solid))
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(pillar.name, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.Bold)
            if (pillar.blurb.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(pillar.blurb, style = MaterialTheme.typography.bodySmall, color = palette.text3)
            }
        }
        Spacer(Modifier.size(Spacing.s2))
        IconTap(Icons.Outlined.Edit, palette.primary, onEdit)
        Spacer(Modifier.size(Spacing.s1))
        IconTap(Icons.Outlined.Close, palette.text3, onRemove)
    }
}

@Composable
private fun IconTap(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun DashedAddButton(text: String, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .border(1.5.dp, palette.primary.copy(alpha = 0.4f), RoundedCornerShape(Radii.lg))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = palette.primary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = palette.primary, fontWeight = FontWeight.Bold)
    }
}

private fun kindLabel(kind: PillarKind): String = when (kind) {
    PillarKind.GOAL_AREA -> "Goal · projects nest here"
    PillarKind.BEHAVIOUR -> "Behaviour · gathers evidence"
    PillarKind.DEVELOPMENT -> "Growth"
}

// ---------------- Refine sheet ----------------

@Composable
private fun RefineSheet(
    state: RefineState,
    palette: BragPalette,
    onDismiss: () -> Unit,
    onSetMode: (RefineMode) -> Unit,
    onTextChange: (String) -> Unit,
    onRequestVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onBuild: () -> Unit,
    onRenameReview: (Int, String) -> Unit,
    onRemoveReview: (Int) -> Unit,
    onConfirm: () -> Unit,
    onStartOver: () -> Unit,
) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onRequestVoice()
    }
    var renameReviewIdx by remember { mutableStateOf(-1) }
    var renameReviewName by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0F1A).copy(alpha = 0.45f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(enabled = false) {}
                .padding(Spacing.s5),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 42.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(palette.border))
            Spacer(Modifier.height(Spacing.s4))

            // Distinct "refining your framework" banner so this never reads like logging an entry.
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primarySoft)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Dashboard, null, tint = palette.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(6.dp))
                Text("REFINING YOUR FRAMEWORK", style = MaterialTheme.typography.labelSmall, color = palette.primary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(Spacing.s4))

            when (state) {
                is RefineState.Input -> RefineInput(
                    state, palette, onSetMode, onTextChange, onStopVoice, onBuild,
                    onSpeak = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            onRequestVoice()
                        } else {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                )
                RefineState.Thinking -> ThinkingState(palette)
                is RefineState.Review -> ReviewState(
                    state, palette, onConfirm, onStartOver,
                    onRemove = onRemoveReview,
                    onRename = { i, n -> renameReviewIdx = i; renameReviewName = n },
                )
                is RefineState.Error -> ErrorState(state.message, palette, onStartOver, onDismiss)
                RefineState.Hidden -> {}
            }
            Spacer(Modifier.height(Spacing.s4))
        }
    }

    if (renameReviewIdx >= 0) {
        NameDialog(
            title = "Rename category",
            initial = renameReviewName,
            palette = palette,
            onConfirm = { onRenameReview(renameReviewIdx, it); renameReviewIdx = -1 },
            onDismiss = { renameReviewIdx = -1 },
        )
    }
}

@Composable
private fun RefineInput(
    state: RefineState.Input,
    palette: BragPalette,
    onSetMode: (RefineMode) -> Unit,
    onTextChange: (String) -> Unit,
    onStopVoice: () -> Unit,
    onBuild: () -> Unit,
    onSpeak: () -> Unit,
) {
    Text("Tell me how you're reviewed", style = MaterialTheme.typography.titleLarge, color = palette.text1)
    Text(
        "Add, rename, remove, or re-describe your categories — just say it (e.g. “add a category for customer focus”). No company name needed.",
        style = MaterialTheme.typography.bodySmall,
        color = palette.text3,
    )
    Spacer(Modifier.height(Spacing.s3))

    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.surface2).padding(4.dp)) {
        SegToggle("Speak", state.mode == RefineMode.SPEAK, { onSetMode(RefineMode.SPEAK) }, Modifier.weight(1f), palette)
        SegToggle("Type", state.mode == RefineMode.TYPE, { onSetMode(RefineMode.TYPE) }, Modifier.weight(1f), palette)
    }
    Spacer(Modifier.height(Spacing.s3))

    if (state.mode == RefineMode.SPEAK) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.transcribing) {
                CircularProgressIndicator(color = palette.primary, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(Spacing.s2))
                Text("Transcribing…", style = MaterialTheme.typography.bodySmall, color = palette.text3)
            } else {
                Text(
                    if (state.listening) "Listening…" else "Tap to speak",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s2))
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(999.dp))
                        .background(if (state.listening) palette.primaryPress else palette.primary)
                        .clickable { if (state.listening) onStopVoice() else onSpeak() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (state.listening) Icons.Outlined.Stop else Icons.Outlined.Mic, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            if (state.text.isNotBlank()) {
                Spacer(Modifier.height(Spacing.s3))
                Text(state.text, style = MaterialTheme.typography.bodyMedium, color = palette.text1, textAlign = TextAlign.Center)
            }
        }
    } else {
        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            placeholder = { Text("e.g. I'm judged on delivery goals, how I collaborate and lead, and my growth.", color = palette.text3) },
        )
    }

    state.error?.let {
        Spacer(Modifier.height(Spacing.s2))
        Text(it, style = MaterialTheme.typography.bodySmall, color = palette.extraInk)
    }

    Spacer(Modifier.height(Spacing.s4))
    PrimaryButton("Update my framework", enabled = state.text.isNotBlank() && !state.transcribing, palette = palette, onClick = onBuild)
}

@Composable
private fun ThinkingState(palette: BragPalette) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s6), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = palette.primary)
        Spacer(Modifier.height(Spacing.s3))
        Text("Shaping your framework…", style = MaterialTheme.typography.titleMedium, color = palette.text1)
    }
}

@Composable
private fun ReviewState(
    state: RefineState.Review,
    palette: BragPalette,
    onConfirm: () -> Unit,
    onStartOver: () -> Unit,
    onRemove: (Int) -> Unit,
    onRename: (Int, String) -> Unit,
) {
    Text("Here's your framework", style = MaterialTheme.typography.titleLarge, color = palette.text1)
    Text("Rename or remove anything, then confirm.", style = MaterialTheme.typography.bodySmall, color = palette.text3)
    Spacer(Modifier.height(Spacing.s3))

    state.pillars.forEachIndexed { i, p ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.s2)
                .clip(RoundedCornerShape(Radii.md))
                .background(palette.surface2)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(pillarColor(i).solid))
            Spacer(Modifier.size(Spacing.s3))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.Bold)
                Text(kindLabel(p.kind), style = MaterialTheme.typography.labelSmall, color = palette.text3)
            }
            IconTap(Icons.Outlined.Edit, palette.primary) { onRename(i, p.name) }
            Spacer(Modifier.size(Spacing.s1))
            IconTap(Icons.Outlined.Close, palette.text3) { onRemove(i) }
        }
    }

    Spacer(Modifier.height(Spacing.s3))
    PrimaryButton("Looks right — save", enabled = state.pillars.isNotEmpty(), palette = palette, onClick = onConfirm)
    Spacer(Modifier.height(Spacing.s2))
    TextButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) {
        Text("Start over", color = palette.text3, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorState(message: String, palette: BragPalette, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s3)) {
        Text("Hmm, that didn't work", style = MaterialTheme.typography.titleLarge, color = palette.text1)
        Spacer(Modifier.height(Spacing.s2))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = palette.text3)
        Spacer(Modifier.height(Spacing.s4))
        PrimaryButton("Try again", enabled = true, palette = palette, onClick = onRetry)
        Spacer(Modifier.height(Spacing.s2))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel", color = palette.text3, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SegToggle(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, palette: BragPalette) {
    Row(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) palette.primary else palette.text3, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, palette: BragPalette, onClick: () -> Unit) {
    val bg = if (enabled) palette.primary else palette.primary.copy(alpha = 0.4f)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ---------------- Dialogs ----------------

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    palette: BragPalette,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, color = palette.text1) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        containerColor = palette.surface,
    )
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialDescription: String,
    palette: BragPalette,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(name, desc) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, color = palette.text1) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s3))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    minLines = 2,
                    label = { Text("What it covers") },
                    placeholder = { Text("A short description", color = palette.text3) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        containerColor = palette.surface,
    )
}

@Composable
private fun AddCategoryDialog(
    palette: BragPalette,
    onConfirm: (String, String, PillarKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PillarKind.GOAL_AREA) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(name, desc, kind) }, enabled = name.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add a category", color = palette.text1) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    placeholder = { Text("e.g. Customer Focus", color = palette.text3) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s3))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    minLines = 2,
                    label = { Text("What it covers (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.s3))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.surface2).padding(4.dp)) {
                    SegToggle("Goal", kind == PillarKind.GOAL_AREA, { kind = PillarKind.GOAL_AREA }, Modifier.weight(1f), palette)
                    SegToggle("Behaviour", kind == PillarKind.BEHAVIOUR, { kind = PillarKind.BEHAVIOUR }, Modifier.weight(1f), palette)
                    SegToggle("Growth", kind == PillarKind.DEVELOPMENT, { kind = PillarKind.DEVELOPMENT }, Modifier.weight(1f), palette)
                }
            }
        },
        containerColor = palette.surface,
    )
}
