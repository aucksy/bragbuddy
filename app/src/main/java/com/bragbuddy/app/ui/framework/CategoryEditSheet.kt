package com.bragbuddy.app.ui.framework

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.framework.Pillar
import com.bragbuddy.app.data.framework.PillarKind
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/** Local editable project row (wraps [ProjectDraft] with a stable key for the voice target). */
private class ProjRowState(val key: Int, val id: Long?, name: String, summary: String) {
    var name by mutableStateOf(name)
    var summary by mutableStateOf(summary)
}

/**
 * Full-screen editor for one category (Design System §3, revised): its **summary** (voice or text)
 * and its **projects**, each with a name and its own **summary** (voice or text — "add your
 * performance metrics for this project"). Voice dictation uses the Groq key; without one the fields
 * are type-only. Save persists the category + all project add/edit/deletes via the ViewModel.
 */
@Composable
fun CategoryEditSheet(
    pillar: Pillar,
    subFolders: List<ProjectEntity>,
    takenNames: Set<String>, // other categories' names (lowercased) — this one can't duplicate them
    viewModel: FrameworkViewModel,
    onClose: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val voiceEnabled by viewModel.voiceEnabled.collectAsStateWithLifecycle()
    val fieldVoice by viewModel.fieldVoice.collectAsStateWithLifecycle()

    var name by remember(pillar.id) { mutableStateOf(pillar.name) }
    var summary by remember(pillar.id) { mutableStateOf(pillar.blurb) }
    var kind by remember(pillar.id) { mutableStateOf(pillar.kind) }
    var nextKey by remember(pillar.id) { mutableStateOf(subFolders.size + 1) }
    val rows = remember(pillar.id) {
        mutableStateListOf<ProjRowState>().apply {
            subFolders.forEachIndexed { i, p -> add(ProjRowState(i, p.id, p.name, p.description.orEmpty())) }
        }
    }

    // Which field is the active dictation target: "summary" or "proj-<key>".
    var activeField by remember(pillar.id) { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.fieldTranscript.collect { text ->
            when (val f = activeField) {
                "summary" -> summary = appendText(summary, text)
                else -> if (f != null && f.startsWith("proj-")) {
                    val k = f.removePrefix("proj-").toIntOrNull()
                    rows.firstOrNull { it.key == k }?.let { it.summary = appendText(it.summary, text) }
                }
            }
            activeField = null
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { viewModel.cancelFieldVoice() } }

    val busy = fieldVoice != FieldVoice.IDLE
    fun startVoice(target: String) {
        if (busy) return
        activeField = target
        viewModel.startFieldVoice()
    }

    val nameTaken = name.isNotBlank() && takenNames.contains(name.trim().lowercase())
    val projectNames = rows.map { it.name.trim().lowercase() }.filter { it.isNotEmpty() }
    val dupProject = projectNames.size != projectNames.toSet().size
    val canSave = name.isNotBlank() && !busy && !nameTaken && !dupProject

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.bg)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.cancelFieldVoice(); onClose() }) {
                Icon(Icons.Outlined.Close, "Cancel", tint = palette.text2)
            }
            Text("Edit category", style = MaterialTheme.typography.titleMedium, color = palette.text1, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (canSave) palette.primary else palette.primary.copy(alpha = 0.4f))
                    .clickable(enabled = canSave) {
                        viewModel.saveCategory(pillar.id, name, summary, kind, rows.map { com.bragbuddy.app.ui.framework.ProjectDraft(it.id, it.name, it.summary) })
                        onClose()
                    }
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) { Text("Save", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall) }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(Spacing.s2))
            FieldLabel("CATEGORY NAME", palette)
            PlainField(name, { name = it }, "e.g. Performance Goals", palette)
            if (nameTaken) {
                Spacer(Modifier.height(Spacing.s1))
                Text("A category with this name already exists.", style = MaterialTheme.typography.labelSmall, color = palette.extraInk)
            }

            Spacer(Modifier.height(Spacing.s4))
            FieldLabel("AXIS", palette)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.surface2).padding(4.dp)) {
                KindSeg("Goal", kind == PillarKind.GOAL_AREA, { kind = PillarKind.GOAL_AREA }, Modifier.weight(1f), palette)
                KindSeg("Behaviour", kind == PillarKind.BEHAVIOUR, { kind = PillarKind.BEHAVIOUR }, Modifier.weight(1f), palette)
                KindSeg("Growth", kind == PillarKind.DEVELOPMENT, { kind = PillarKind.DEVELOPMENT }, Modifier.weight(1f), palette)
            }

            Spacer(Modifier.height(Spacing.s4))
            FieldLabel("CATEGORY SUMMARY", palette)
            VoiceField(
                value = summary, onValueChange = { summary = it },
                placeholder = "What this category covers — say it or type it.",
                palette = palette, voiceEnabled = voiceEnabled,
                recording = busy && activeField == "summary" && fieldVoice == FieldVoice.RECORDING,
                transcribing = busy && activeField == "summary" && fieldVoice == FieldVoice.TRANSCRIBING,
                enabled = !busy || activeField == "summary",
                onStartVoice = { startVoice("summary") },
                onStopVoice = { viewModel.stopFieldVoice() },
            )

            Spacer(Modifier.height(Spacing.s5))
            FieldLabel(if (kind == PillarKind.GOAL_AREA) "PROJECTS" else "FOCUS AREAS", palette)
            if (dupProject) {
                Text("Two projects can't share the same name.", style = MaterialTheme.typography.labelSmall, color = palette.extraInk)
                Spacer(Modifier.height(Spacing.s2))
            }

            rows.forEachIndexed { index, row ->
                val target = "proj-${row.key}"
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = Spacing.s3)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(palette.surface)
                        .border(1.dp, palette.border, RoundedCornerShape(Radii.lg))
                        .padding(Spacing.card),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            PlainField(row.name, { row.name = it }, "Project name", palette)
                        }
                        IconButton(onClick = { if (!busy) rows.remove(row) }, enabled = !busy) {
                            Icon(Icons.Outlined.Close, "Remove project", tint = palette.text3, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(Spacing.s2))
                    VoiceField(
                        value = row.summary, onValueChange = { row.summary = it },
                        placeholder = "Add your performance metrics for this project…",
                        palette = palette, voiceEnabled = voiceEnabled,
                        recording = busy && activeField == target && fieldVoice == FieldVoice.RECORDING,
                        transcribing = busy && activeField == target && fieldVoice == FieldVoice.TRANSCRIBING,
                        enabled = !busy || activeField == target,
                        onStartVoice = { startVoice(target) },
                        onStopVoice = { viewModel.stopFieldVoice() },
                    )
                }
            }

            AddRowButton("Add project", palette, enabled = !busy) {
                rows.add(ProjRowState(nextKey, null, "", "")); nextKey++
            }
            Spacer(Modifier.height(Spacing.s8))
        }
    }
}

private fun appendText(base: String, extra: String): String {
    val add = extra.trim()
    if (add.isBlank()) return base
    val b = base.trim()
    return if (b.isEmpty()) add else "$b $add"
}

@Composable
private fun FieldLabel(text: String, palette: BragPalette) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(Spacing.s2))
}

@Composable
private fun PlainField(value: String, onValueChange: (String) -> Unit, placeholder: String, palette: BragPalette) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
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
private fun VoiceField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: BragPalette,
    voiceEnabled: Boolean,
    recording: Boolean,
    transcribing: Boolean,
    enabled: Boolean,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .border(1.5.dp, palette.primary.copy(alpha = if (recording) 0.6f else 0.35f), RoundedCornerShape(Radii.md))
            .background(palette.surface)
            .padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.text3)
            BasicTextField(
                value = value, onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 14.sp, lineHeight = 20.sp)),
                cursorBrush = SolidColor(palette.primary),
            )
        }
        if (voiceEnabled) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    transcribing -> {
                        CircularProgressIndicator(color = palette.primary, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Adding…", style = MaterialTheme.typography.labelSmall, color = palette.text3)
                    }
                    recording -> {
                        Text("Recording…", style = MaterialTheme.typography.labelSmall, color = palette.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(999.dp)).background(palette.primary).clickable(onClick = onStopVoice),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Stop, "Stop", tint = Color.White, modifier = Modifier.size(16.dp)) }
                    }
                    else -> {
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(999.dp))
                                .background(if (enabled) palette.primarySoft else palette.surface2)
                                .clickable(enabled = enabled, onClick = onStartVoice),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Outlined.Mic, "Dictate", tint = if (enabled) palette.primary else palette.text3, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun KindSeg(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier, palette: BragPalette) {
    Row(
        modifier.clip(RoundedCornerShape(999.dp)).background(if (selected) palette.surface else Color.Transparent).clickable(onClick = onClick).padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) palette.primary else palette.text3, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AddRowButton(text: String, palette: BragPalette, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .border(1.5.dp, palette.primary.copy(alpha = if (enabled) 0.4f else 0.2f), RoundedCornerShape(Radii.lg))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+ $text", style = MaterialTheme.typography.titleSmall, color = palette.primary, fontWeight = FontWeight.Bold)
    }
}
