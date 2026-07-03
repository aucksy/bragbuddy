package com.bragbuddy.app.ui.framework

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.PillarColor
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing
import com.bragbuddy.app.ui.theme.pillarColor

/**
 * The Framework editor (Design System §3, revised): your **categories**, grouped by the two axes.
 * Each category is a **collapsible card** — collapsed by default (just name + count) to keep the page
 * short. Expand to see its summary + projects; tap **Edit** to open the full sheet where the category
 * summary and each project's summary are edited by **voice or text**. (The old "Refine by voice"
 * whole-framework button is gone — editing is direct and per-field.) The company name is never asked.
 */
@Composable
fun FrameworkScreen(
    contentBottomPadding: androidx.compose.ui.unit.Dp,
    viewModel: FrameworkViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val framework by viewModel.framework.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()

    var editing by remember { mutableStateOf<Pillar?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<Pillar?>(null) }
    val expanded = remember { mutableStateListOf<String>() } // expanded category ids; default = none

    val hueOf: (Pillar) -> PillarColor = { p -> pillarColor(framework.pillars.indexOfFirst { it.id == p.id }) }
    val subsOf: (Pillar) -> List<ProjectEntity> = { p -> folders.filter { it.goalArea.equals(p.name, ignoreCase = true) } }
    fun toggle(id: String) { if (expanded.contains(id)) expanded.remove(id) else expanded.add(id) }

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
                    CategoryCard(
                        p, hueOf(p), subsOf(p), expanded.contains(p.id), palette,
                        onToggle = { toggle(p.id) }, onEdit = { editing = p }, onRemove = { removeTarget = p },
                    )
                    Spacer(Modifier.height(Spacing.s3))
                }
            }

            val howPillars = framework.behaviours + framework.development
            if (howPillars.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s2))
                AxisLabel("How you did it")
                howPillars.forEach { p ->
                    CategoryCard(
                        p, hueOf(p), subsOf(p), expanded.contains(p.id), palette,
                        onToggle = { toggle(p.id) }, onEdit = { editing = p }, onRemove = { removeTarget = p },
                    )
                    Spacer(Modifier.height(Spacing.s3))
                }
            }

            Spacer(Modifier.height(Spacing.s2))
            DashedAddButton("Add a category", palette) { showAdd = true }
            Spacer(Modifier.height(contentBottomPadding + Spacing.s6))
        }
    }

    editing?.let { p ->
        CategoryEditSheet(
            pillar = p,
            subFolders = subsOf(p),
            takenNames = framework.pillars.filter { it.id != p.id }.map { it.name.trim().lowercase() }.toSet(),
            viewModel = viewModel,
            onClose = { editing = null },
        )
    }

    if (showAdd) {
        AddCategoryDialog(
            palette = palette,
            takenNames = framework.pillars.map { it.name.trim().lowercase() }.toSet(),
            onConfirm = { name, desc, kind -> viewModel.addCategory(name, desc, kind); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }

    removeTarget?.let { p ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(p.id); removeTarget = null }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } },
            title = { Text("Remove “${p.name}”?", color = palette.text1) },
            text = { Text("Removes this category and its projects. Entries already filed stay in your record.", color = palette.text3) },
            containerColor = palette.surface,
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
private fun CategoryCard(
    pillar: Pillar,
    hue: PillarColor,
    subFolders: List<ProjectEntity>,
    expanded: Boolean,
    palette: BragPalette,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    val countLabel = if (pillar.kind == PillarKind.GOAL_AREA) {
        "${subFolders.size} ${if (subFolders.size == 1) "project" else "projects"}"
    } else {
        "${subFolders.size} ${if (subFolders.size == 1) "focus area" else "focus areas"}"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(Radii.lg)),
    ) {
        // Collapsed header — tap to expand/collapse.
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(Spacing.card),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(hue.solid))
            Spacer(Modifier.size(Spacing.s3))
            Text(pillar.name, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(countLabel, style = MaterialTheme.typography.bodySmall, color = palette.text3)
            Spacer(Modifier.size(Spacing.s2))
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                if (expanded) "Collapse" else "Expand",
                tint = palette.text3,
                modifier = Modifier.size(20.dp),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(start = Spacing.card, end = Spacing.card, bottom = Spacing.card)) {
                if (pillar.blurb.isNotBlank()) {
                    Text(pillar.blurb, style = MaterialTheme.typography.bodySmall, color = palette.text2)
                    Spacer(Modifier.height(Spacing.s3))
                }
                if (subFolders.isEmpty()) {
                    Text(
                        if (pillar.kind == PillarKind.GOAL_AREA) "No projects yet." else "No focus areas yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text3,
                    )
                } else {
                    subFolders.forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Folder, null, tint = palette.primary, modifier = Modifier.size(15.dp).padding(top = 2.dp))
                            Spacer(Modifier.size(Spacing.s2))
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.bodyMedium, color = palette.text1, fontWeight = FontWeight.SemiBold)
                                f.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = palette.text3)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.s3))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).background(palette.primarySoft).clickable(onClick = onEdit).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Edit, null, tint = palette.primary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Edit", style = MaterialTheme.typography.labelLarge, color = palette.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(
                        Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onRemove).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Delete, null, tint = palette.text3, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Remove", style = MaterialTheme.typography.labelLarge, color = palette.text3, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
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

@Composable
private fun AddCategoryDialog(
    palette: BragPalette,
    takenNames: Set<String>,
    onConfirm: (String, String, PillarKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(PillarKind.GOAL_AREA) }
    val nameTaken = name.isNotBlank() && takenNames.contains(name.trim().lowercase())
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(name, desc, kind) }, enabled = name.isNotBlank() && !nameTaken) { Text("Add") } },
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
                    isError = nameTaken,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (nameTaken) {
                    Text("A category with this name already exists.", style = MaterialTheme.typography.labelSmall, color = palette.extraInk)
                }
                Spacer(Modifier.height(Spacing.s3))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    minLines = 2,
                    label = { Text("Summary (optional)") },
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) palette.primary else palette.text3, fontWeight = FontWeight.Bold)
    }
}
