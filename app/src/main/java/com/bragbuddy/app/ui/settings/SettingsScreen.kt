package com.bragbuddy.app.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.BuildConfig
import com.bragbuddy.app.R
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

            // About
            Card(palette) {
                InfoRow("AI engine", viewModel.aiProviderLabel, palette)
                Spacer(Modifier.height(Spacing.s3))
                InfoRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", palette)
            }
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

private fun formatTime(hour: Int, minute: Int): String =
    java.time.LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
