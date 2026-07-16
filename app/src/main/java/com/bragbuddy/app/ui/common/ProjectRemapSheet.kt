package com.bragbuddy.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * A pending **project rename-remap** offer (Phase B2b): [count] filed records are still tagged to
 * [oldName], which was just renamed to [newName]. [oldArea] is the goal area those records are under
 * (the folder's category BEFORE the edit) — it scopes which records match. [newArea] is the folder's
 * goal area AFTER the edit — the target area for a carry (records follow the folder even if its category
 * changed) and the goal area a brand-new project (option c) is created under.
 */
data class ProjectRemap(
    val oldName: String,
    val newName: String,
    val oldArea: String,
    val newArea: String,
    val count: Int,
)

/**
 * The 3-option project rename-remap prompt (Phase B2b), shown after a project (folder) with filed
 * records is renamed — from the Framework editor's project rows or the Settings folder dialog. It is
 * the project-level analogue of the category rename-remap, extended to three destinations:
 *  - **(a) Carry** — move the records to the renamed folder's new name (goal area unchanged).
 *  - **(b) Move to another project** — reassign to an existing project (its goal area follows).
 *  - **(c) A new project** — create one here and move the records into it (under the source goal area).
 *
 * Deterministic, no AI. A **custom scrim + bottom sheet** (matching the capture / entry-detail sheets),
 * never a Material `ModalBottomSheet` — swipe-dismiss is never vetoed. Dismissing leaves the records as
 * they are; they surface under "Uncategorized" until re-homed (never lost). Not in the Design System —
 * built from tokens; the look was approved with the creator.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectRemapSheet(
    remap: ProjectRemap,
    otherProjects: List<ProjectEntity>,
    onCarry: () -> Unit,
    onReassign: (ProjectEntity) -> Unit,
    onCreateNew: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val noRipple = remember { MutableInteractionSource() }

    // 0 = carry to the new name · 1 = an existing project · 2 = a new project named here.
    var choice by remember { mutableStateOf(0) }
    var pickedId by remember { mutableStateOf<Long?>(null) }
    var newName by remember { mutableStateOf("") }

    val canApply = when (choice) {
        1 -> pickedId != null && otherProjects.any { it.id == pickedId }
        2 -> newName.isNotBlank()
        else -> true
    }
    val one = remap.count == 1
    val recordWord = if (one) "record" else "records"

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .imePadding()
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp),
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(5.dp)
                    .clip(RoundedCornerShape(3.dp)).background(palette.text3.copy(alpha = 0.35f)),
            )
            Spacer(Modifier.height(16.dp))

            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Move ${remap.count} $recordWord?",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text1,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.s2))
                Text(
                    "${remap.count} filed ${if (one) "record is" else "records are"} tagged to " +
                        "“${remap.oldName}”, now renamed to “${remap.newName}”. Where should " +
                        "${if (one) "it" else "they"} go?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text3,
                )

                Spacer(Modifier.height(Spacing.s4))

                OptionRow(
                    selected = choice == 0,
                    title = "Carry ${if (one) "it" else "them"} to “${remap.newName}”",
                    subtitle = "Keep the records with this project under its new name.",
                    palette = palette,
                    onClick = { choice = 0 },
                )
                Spacer(Modifier.height(Spacing.s2))

                OptionRow(
                    selected = choice == 1,
                    title = "Move to another project",
                    subtitle = "Reassign the records to a project you already have.",
                    palette = palette,
                    onClick = { choice = 1 },
                )
                if (choice == 1) {
                    Spacer(Modifier.height(Spacing.s2))
                    if (otherProjects.isEmpty()) {
                        Text(
                            "No other projects yet — create one below instead.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.text3,
                            modifier = Modifier.padding(start = 34.dp, bottom = Spacing.s2),
                        )
                    } else {
                        FlowRow(
                            Modifier.fillMaxWidth().padding(start = 34.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            otherProjects.forEach { p ->
                                SelectChip(p.name, selected = pickedId == p.id, palette = palette) { pickedId = p.id }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.s2))

                OptionRow(
                    selected = choice == 2,
                    title = "Assign to a new project",
                    subtitle = "Create a project (under “${remap.newArea}”) and move them there.",
                    palette = palette,
                    onClick = { choice = 2 },
                )
                if (choice == 2) {
                    Spacer(Modifier.height(Spacing.s2))
                    Box(Modifier.padding(start = 34.dp)) {
                        NameField(newName, { newName = it }, "New project name", palette)
                    }
                }

                Spacer(Modifier.height(Spacing.s5))
                ApplyButton(
                    enabled = canApply,
                    palette = palette,
                ) {
                    when (choice) {
                        1 -> otherProjects.firstOrNull { it.id == pickedId }?.let(onReassign)
                        2 -> newName.trim().takeIf { it.isNotBlank() }?.let(onCreateNew)
                        else -> onCarry()
                    }
                }
                Spacer(Modifier.height(Spacing.s2))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).clickable(onClick = onDismiss).padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Leave them (they'll show under “Outside project”)",
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.text3,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            // + the app's own bottom bar/FAB when opened from the Framework TAB. 0.dp from Settings
            // (a pushed route with no bar).
            Spacer(Modifier.height(18.dp + bottomInset + LocalBottomBarInset.current))
        }
    }
}

@Composable
private fun OptionRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    palette: BragPalette,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .border(1.5.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(Radii.md))
            .background(if (selected) palette.primarySoft else palette.surface)
            .clickable(onClick = onClick)
            .padding(Spacing.card),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Radio dot.
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(999.dp))
                .border(2.dp, if (selected) palette.primary else palette.text3, RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary))
        }
        Spacer(Modifier.width(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        }
    }
}

@Composable
private fun SelectChip(label: String, selected: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.White else palette.text2,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.primary else palette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, placeholder: String, palette: BragPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(Radii.md))
            .border(1.dp, palette.border, RoundedCornerShape(Radii.md))
            .background(palette.surface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.text3)
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 14.sp)),
            cursorBrush = SolidColor(palette.primary),
        )
    }
}

@Composable
private fun ApplyButton(enabled: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.primary else palette.primary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) { Text("Apply", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) }
}
