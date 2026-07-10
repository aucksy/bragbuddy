package com.bragbuddy.app.ui.entry

import android.text.format.DateUtils
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
import com.bragbuddy.app.data.local.EntryEntity
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.common.rememberDiscardGuard
import com.bragbuddy.app.ui.home.OUTSIDE_PROJECT_LABEL
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * Tap an entry → this detail sheet (Phase 4). Shows the cleaned bullet AND the raw transcript it came
 * from, plus its placement, and gathers every per-entry action in one place: **Edit** (inline, with its
 * own Save — Phase B2b, matching the framework editor's per-item Save), **Move** (reassign to another
 * folder — no AI re-call), **★ Standout** toggle, **Pin** toggle (for the Phase 5 summary), and
 * **Delete**. A custom scrim + bottom sheet (matching the capture sheet), not a Material
 * ModalBottomSheet — swipe-dismiss is never vetoed. Not in the design files (built from existing
 * tokens); flagged.
 *
 * [entry] is a snapshot the host updates optimistically on toggle, so ★/Pin flip instantly.
 * [onSaveEdit] receives the edited text; the host re-files it (no AI-less move — this re-runs the
 * categorizer) and dismisses the sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetailSheet(
    entry: EntryEntity,
    folders: List<ProjectEntity>,
    onSaveEdit: (String) -> Unit,
    onMoveToProject: (String) -> Unit,
    onMoveOutside: () -> Unit,
    onToggleExtra: (Boolean) -> Unit,
    onTogglePin: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val noRipple = remember { MutableInteractionSource() }
    var showMove by remember { mutableStateOf(false) }
    // Inline edit state — keyed on the entry id so an optimistic ★/Pin recomposition of the SAME entry
    // doesn't reset a mid-edit, but opening a different entry starts fresh.
    var editing by remember(entry.id) { mutableStateOf(false) }
    val editBaseline = entry.bullet?.takeIf { it.isNotBlank() } ?: entry.rawTranscript
    var editText by remember(entry.id) { mutableStateOf(editBaseline) }

    // Guard the scrim tap + system Back against losing an in-progress edit.
    val editDirty = editing && editText.trim() != editBaseline.trim()
    val requestDismiss = rememberDiscardGuard(
        dirty = editDirty,
        onDismiss = onDismiss,
        message = "Your edit to this entry hasn't been saved. Discard it?",
    )

    val cleaned = entry.bullet?.takeIf { it.isNotBlank() }
    val date = DateUtils.getRelativeTimeSpanString(
        entry.occurredAt ?: entry.createdAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    val projectName = entry.project
        ?.takeIf { it.isNotBlank() && !it.equals("Outside-project", true) && !it.equals("Inbox", true) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
                .clickable(interactionSource = noRipple, indication = null, onClick = requestDismiss),
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
                // Cleaned bullet (the headline of the entry) — editable inline (Phase B2b).
                if (editing) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.md))
                            .border(1.5.dp, palette.primary.copy(alpha = 0.5f), RoundedCornerShape(Radii.md))
                            .background(palette.surface2)
                            .padding(12.dp),
                    ) {
                        if (editText.isEmpty()) {
                            Text("Type the entry…", style = MaterialTheme.typography.titleMedium, color = palette.text3)
                        }
                        BasicTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            // Cap growth so a long edit scrolls inside the field, keeping Save reachable.
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                            textStyle = LocalTextStyle.current.merge(
                                TextStyle(color = palette.text1, fontSize = 16.sp, lineHeight = 22.sp),
                            ),
                            cursorBrush = SolidColor(palette.primary),
                        )
                    }
                } else {
                    Text(
                        cleaned ?: "Still filing this one…",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (cleaned != null) palette.text1 else palette.text3,
                    )
                }
                Spacer(Modifier.height(Spacing.s3))

                // Placement + meta chips.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(projectName ?: OUTSIDE_PROJECT_LABEL, palette.primarySoft, palette.primary)
                    entry.goalCategory?.takeIf { it.isNotBlank() }?.let { Pill(it, palette.surface2, palette.text2) }
                    if (entry.isExtra) Pill("★ Standout", palette.extraSoft, palette.extraInk)
                    if (entry.isPinned) Pill("Pinned", palette.primarySoft, palette.primary)
                    entry.metric?.takeIf { it.isNotBlank() }?.let { Pill(it, palette.surface2, palette.text2) }
                }
                if (entry.demonstrates.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.s2))
                    Text(
                        "Evidences ${entry.demonstrates.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(date, style = MaterialTheme.typography.bodySmall, color = palette.text3)

                // Raw transcript.
                Spacer(Modifier.height(Spacing.s4))
                Text("WHAT YOU SAID", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.s2))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.md)).background(palette.surface2).padding(13.dp),
                ) {
                    Text(entry.rawTranscript, style = MaterialTheme.typography.bodyMedium, color = palette.text2)
                }

                // Move picker (revealed on demand; hidden while editing the bullet).
                if (showMove && !editing) {
                    Spacer(Modifier.height(Spacing.s4))
                    Text("MOVE TO", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Spacing.s2))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        folders.forEach { f ->
                            ActionChip(f.name, palette.primarySoft, palette.primary) {
                                onMoveToProject(f.name); onDismiss()
                            }
                        }
                        ActionChip("Outside project", palette.surface2, palette.text2) { onMoveOutside(); onDismiss() }
                    }
                }

                // Actions — while editing the bullet, the pills give way to Save / Cancel (per-item Save).
                Spacer(Modifier.height(Spacing.s4))
                if (editing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SavePill(enabled = editText.isNotBlank(), palette = palette) { onSaveEdit(editText.trim()) }
                        Spacer(Modifier.width(8.dp))
                        ActionPill("Cancel", active = false, palette = palette) {
                            editText = cleaned ?: entry.rawTranscript
                            editing = false
                        }
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionPill("Edit", active = false, palette = palette) { showMove = false; editing = true }
                        ActionPill(if (showMove) "Cancel move" else "Move", active = showMove, palette = palette) { showMove = !showMove }
                        ActionPill(if (entry.isExtra) "★ Standout" else "☆ Standout", active = entry.isExtra, palette = palette) { onToggleExtra(!entry.isExtra) }
                        ActionPill(if (entry.isPinned) "Pinned" else "Pin", active = entry.isPinned, palette = palette) { onTogglePin(!entry.isPinned) }
                        DangerPill("Delete", palette) { onDelete() }
                    }
                }
            }

            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Spacer(Modifier.height(18.dp + bottomInset))
        }
    }
}

@Composable
private fun ActionPill(text: String, active: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (active) Color.White else palette.text2,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) palette.primary else palette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun SavePill(enabled: Boolean, palette: BragPalette, onClick: () -> Unit) {
    Text(
        text = "Save & re-file",
        style = MaterialTheme.typography.labelLarge,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.primary else palette.primary.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun DangerPill(text: String, palette: BragPalette, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ActionChip(text: String, fill: Color, ink: Color, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(Radii.sm))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun Pill(text: String, fill: Color, ink: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(Radii.sm)).background(fill).padding(horizontal = 9.dp, vertical = 4.dp),
    )
}
