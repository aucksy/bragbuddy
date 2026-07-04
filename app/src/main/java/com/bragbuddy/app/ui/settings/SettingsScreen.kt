package com.bragbuddy.app.ui.settings

import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.BuildConfig
import com.bragbuddy.app.R
import com.bragbuddy.app.data.local.ProjectEntity
import com.bragbuddy.app.ui.role.RoleInput
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val goalAreas by viewModel.goalAreas.collectAsStateWithLifecycle()

    var editFolder by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleteFolder by remember { mutableStateOf<ProjectEntity?>(null) }
    var showAddFolder by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.text1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.nav_home), tint = palette.text2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.text1),
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            // Daily reminder
            Card(palette) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Daily reminder", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                        Text(
                            "One gentle nudge a day to log a quick update.",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.text3,
                        )
                    }
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { viewModel.setReminderEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.primary,
                            uncheckedTrackColor = palette.surface2,
                            uncheckedBorderColor = palette.border,
                        ),
                    )
                }
                if (settings.reminderEnabled) {
                    Spacer(Modifier.height(Spacing.s3))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.md))
                            .background(palette.surface2)
                            .clickable {
                                TimePickerDialog(
                                    context,
                                    { _, h, m -> viewModel.setReminderTime(h, m) },
                                    settings.reminderHour,
                                    settings.reminderMinute,
                                    false,
                                ).show()
                            }
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Time", style = MaterialTheme.typography.bodyMedium, color = palette.text2, modifier = Modifier.weight(1f))
                        Text(
                            formatTime(settings.reminderHour, settings.reminderMinute),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.s4))

            // Review year — windows the summary's mid-year / year-end periods (Phase 5).
            ReviewYearCard(
                startMonth = settings.reviewYearStartMonth,
                palette = palette,
                onPick = { viewModel.setReviewYearStartMonth(it) },
            )

            Spacer(Modifier.height(Spacing.s4))

            // Your role — AI context (never the company name)
            RoleCard(
                savedRole = settings.jobRole,
                palette = palette,
                onSave = { viewModel.setJobRole(it) },
            )

            Spacer(Modifier.height(Spacing.s4))

            // Voice transcription — cloud Whisper only (on-device removed; too inaccurate).
            Card(palette) {
                Text("Voice transcription", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    if (settings.groqApiKey.isBlank())
                        "Voice notes are transcribed by cloud Whisper (Groq). Add your key under “AI brain (Groq)” below to enable voice — until then, typing always works."
                    else "Powered by cloud Whisper using your Groq key ✓ — accurate and fast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }

            Spacer(Modifier.height(Spacing.s4))

            // AI brain (Groq) — the ONE key, reused for categorizer + framework refine (and Cloud
            // Whisper above). On-device only.
            Card(palette) {
                Text("AI brain (Groq)", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "Cleans and files each entry, and builds your framework by voice. Uses your Groq key — the same one Cloud Whisper uses. Without it, entries just wait in the Inbox.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s3))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .border(1.5.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(Radii.md))
                        .background(palette.surface)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (settings.groqApiKey.isEmpty()) {
                        Text("Paste your Groq key (gsk_…)", style = MaterialTheme.typography.bodyMedium, color = palette.text3)
                    }
                    BasicTextField(
                        value = settings.groqApiKey,
                        onValueChange = { viewModel.setGroqApiKey(it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 14.sp)),
                        cursorBrush = SolidColor(palette.primary),
                    )
                }
                Spacer(Modifier.height(Spacing.s2))
                Text(
                    if (settings.groqApiKey.isBlank())
                        "One free key at console.groq.com → API Keys powers both AI and Cloud Whisper. Stored on this device only, never uploaded to us."
                    else "Key saved on this device — powering AI categorization.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }

            Spacer(Modifier.height(Spacing.s4))

            // Project folders
            Card(palette) {
                Text("Project folders", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "Tap a folder on Home to log straight into that project.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s3))
                if (folders.isEmpty()) {
                    Text("No folders yet.", style = MaterialTheme.typography.bodyMedium, color = palette.text3)
                } else {
                    folders.forEach { p ->
                        FolderRow(p, palette, onEdit = { editFolder = p }, onDelete = { deleteFolder = p })
                        Spacer(Modifier.height(Spacing.s2))
                    }
                }
                Spacer(Modifier.height(Spacing.s2))
                DashedAdd("Add a folder", palette) { showAddFolder = true }
            }

            Spacer(Modifier.height(Spacing.s4))

            // About
            Card(palette) {
                InfoRow("AI engine", viewModel.aiProviderLabel, palette)
                Spacer(Modifier.height(Spacing.s3))
                InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", palette)
            }
            Spacer(Modifier.height(Spacing.s6))
        }
    }

    if (showAddFolder) {
        FolderDialog(
            title = "New project folder",
            initialName = "",
            initialGoalArea = goalAreas.firstOrNull() ?: "",
            goalAreas = goalAreas,
            palette = palette,
            onConfirm = { name, area -> viewModel.createProject(name, area); showAddFolder = false },
            onDismiss = { showAddFolder = false },
        )
    }

    editFolder?.let { p ->
        FolderDialog(
            title = "Edit folder",
            initialName = p.name,
            initialGoalArea = p.goalArea,
            goalAreas = goalAreas,
            palette = palette,
            onConfirm = { name, area -> viewModel.updateProject(p.id, name, area); editFolder = null },
            onDismiss = { editFolder = null },
        )
    }

    deleteFolder?.let { p ->
        AlertDialog(
            onDismissRequest = { deleteFolder = null },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProject(p.id); deleteFolder = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteFolder = null }) { Text("Cancel") } },
            title = { Text("Delete “${p.name}”?", color = palette.text1) },
            text = { Text("Removes the folder. Entries already filed to it stay in your record.", color = palette.text3) },
            containerColor = palette.surface,
        )
    }
}

private fun monthName(month: Int): String =
    java.time.Month.of(month.coerceIn(1, 12))
        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())

@Composable
private fun ReviewYearCard(startMonth: Int, palette: BragPalette, onPick: (Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Card(palette) {
        Text("Review year", style = MaterialTheme.typography.titleMedium, color = palette.text1)
        Text(
            "When your appraisal year starts. Sets what “mid-year” and “year-end” summaries cover.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
        )
        Spacer(Modifier.height(Spacing.s3))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(palette.surface2)
                .clickable { showPicker = true }
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Starts in", style = MaterialTheme.typography.bodyMedium, color = palette.text2, modifier = Modifier.weight(1f))
            Text(monthName(startMonth), style = MaterialTheme.typography.titleMedium, color = palette.primary)
        }
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
            title = { Text("Review year starts in", color = palette.text1) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (1..12).forEach { m ->
                        val selected = m == startMonth
                        Text(
                            monthName(m),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) palette.primary else palette.text1,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radii.sm))
                                .clickable { onPick(m); showPicker = false }
                                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
                        )
                    }
                }
            },
            containerColor = palette.surface,
        )
    }
}

@Composable
private fun RoleCard(savedRole: String, palette: BragPalette, onSave: (String) -> Unit) {
    var draft by remember(savedRole) { mutableStateOf(savedRole) }
    Card(palette) {
        Text("Your role", style = MaterialTheme.typography.titleMedium, color = palette.text1)
        Text(
            "Context for the AI — sharpens what's core vs. standout for you. Never your company name.",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
        )
        Spacer(Modifier.height(Spacing.s3))
        RoleInput(value = draft, onValueChange = { draft = it }, palette = palette, onImeDone = { if (draft.isNotBlank()) onSave(draft) })
        Spacer(Modifier.height(Spacing.s2))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Product Owner", "Software Engineer", "Designer", "Manager", "Analyst").forEach { ex ->
                ExampleChip(ex, palette) { draft = ex }
            }
        }
        if (draft.trim() != savedRole && draft.isNotBlank()) {
            Spacer(Modifier.height(Spacing.s3))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.primary)
                    .clickable { onSave(draft) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Save role", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun ExampleChip(label: String, palette: BragPalette, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = palette.text2,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun FolderRow(project: ProjectEntity, palette: BragPalette, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .background(palette.surface2)
            .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Folder, null, tint = palette.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(project.name, style = MaterialTheme.typography.titleSmall, color = palette.text1, fontWeight = FontWeight.SemiBold)
            Text(project.goalArea, style = MaterialTheme.typography.labelSmall, color = palette.text3)
        }
        SettingsIconTap(Icons.Outlined.Edit, palette.primary, onEdit)
        Spacer(Modifier.size(4.dp))
        SettingsIconTap(Icons.Outlined.Close, palette.text3, onDelete)
    }
}

@Composable
private fun SettingsIconTap(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(30.dp).clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp)) }
}

@Composable
private fun DashedAdd(text: String, palette: BragPalette, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .border(1.5.dp, palette.primary.copy(alpha = 0.4f), RoundedCornerShape(Radii.md))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Add, null, tint = palette.primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = palette.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FolderDialog(
    title: String,
    initialName: String,
    initialGoalArea: String,
    goalAreas: List<String>,
    palette: BragPalette,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var area by remember { mutableStateOf(initialGoalArea.ifBlank { goalAreas.firstOrNull() ?: "" }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onConfirm(name, area) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title, color = palette.text1) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Folder name") },
                    placeholder = { Text("e.g. Raven Migration", color = palette.text3) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (goalAreas.isNotEmpty()) {
                    Spacer(Modifier.height(Spacing.s3))
                    Text("Rolls up to", style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(Spacing.s2))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        goalAreas.forEach { g ->
                            val selected = g == area
                            Text(
                                g,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) Color.White else palette.text2,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) palette.primary else palette.surface2)
                                    .clickable { area = g }
                                    .padding(horizontal = 11.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
            }
        },
        containerColor = palette.surface,
    )
}

@Composable
private fun Card(palette: BragPalette, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.lg))
            .background(palette.surface)
            .padding(Spacing.card),
        content = content,
    )
}

@Composable
private fun InfoRow(label: String, value: String, palette: BragPalette) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        Text(value, style = MaterialTheme.typography.titleMedium, color = palette.text1)
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    java.time.LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
