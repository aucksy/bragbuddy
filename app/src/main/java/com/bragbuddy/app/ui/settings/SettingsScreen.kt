package com.bragbuddy.app.ui.settings

import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.bragbuddy.app.data.prefs.TranscriptionEngine
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

            // Transcription
            Card(palette) {
                Text("Transcription", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "On-device is free & offline. Cloud Whisper is far more accurate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s3))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.surface2)
                        .padding(4.dp),
                ) {
                    EngineSegment(
                        "On-device",
                        settings.transcriptionEngine == TranscriptionEngine.ON_DEVICE,
                        { viewModel.setTranscriptionEngine(TranscriptionEngine.ON_DEVICE) },
                        Modifier.weight(1f), palette,
                    )
                    EngineSegment(
                        "Cloud Whisper",
                        settings.transcriptionEngine == TranscriptionEngine.CLOUD,
                        { viewModel.setTranscriptionEngine(TranscriptionEngine.CLOUD) },
                        Modifier.weight(1f), palette,
                    )
                }
                if (settings.transcriptionEngine == TranscriptionEngine.CLOUD) {
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
                            "Free key at console.groq.com — stored on this device only, never uploaded to us."
                        else "Key saved on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text3,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.s4))

            // AI brain (OpenRouter) — categorizer + framework refine. On-device key only.
            Card(palette) {
                Text("AI brain (OpenRouter)", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Text(
                    "Cleans and files each entry, and builds your framework by voice. Without a key, entries just wait in the Inbox.",
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
                    if (settings.openRouterApiKey.isEmpty()) {
                        Text("Paste your OpenRouter key (sk-or-…)", style = MaterialTheme.typography.bodyMedium, color = palette.text3)
                    }
                    BasicTextField(
                        value = settings.openRouterApiKey,
                        onValueChange = { viewModel.setOpenRouterApiKey(it) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 14.sp)),
                        cursorBrush = SolidColor(palette.primary),
                    )
                }
                Spacer(Modifier.height(Spacing.s2))
                Text(
                    if (settings.openRouterApiKey.isBlank())
                        "Free key at openrouter.ai → Keys. Stored on this device only, never uploaded to us."
                    else "Key saved on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
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

@Composable
private fun EngineSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    palette: BragPalette,
) {
    val content = if (selected) palette.primary else palette.text3
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = content, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(hour: Int, minute: Int): String =
    java.time.LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
