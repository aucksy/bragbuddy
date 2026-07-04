package com.bragbuddy.app.ui.backup

import android.text.format.DateUtils
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * Google Drive backup (Design System §6). Connect Drive, see backup health + size, back up now,
 * restore, and a local export/import fallback. "+ Voice notes" is shown disabled — BragBuddy keeps no
 * audio on-device (transcribed then discarded), so there's nothing to back up there; flagged as a
 * deviation from the mockup.
 */
@Composable
fun BackupScreen(
    onBack: () -> Unit,
    viewModel: BackupViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    LaunchedEffect(message) {
        message?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onSignInResult(result.data)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportToUri(it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importFromUri(it) }
    }

    val connected = state.connectedEmail != null

    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = { Text("Backup", style = MaterialTheme.typography.headlineMedium, color = palette.text1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = palette.text2)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.bg, titleContentColor = palette.text1),
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screen),
        ) {
            // Health / connection
            HealthCard(state, palette)
            Spacer(Modifier.height(Spacing.s4))

            // What gets backed up
            Card(palette) {
                Label("WHAT GETS BACKED UP", palette)
                Spacer(Modifier.height(Spacing.s3))
                OptionRow(
                    icon = Icons.Outlined.Description,
                    title = "Transcriptions & data",
                    subtitle = "Your entries, projects, framework, settings and summaries. Small and private.",
                    trailing = state.sizeLabel ?: "…",
                    selected = true,
                    enabled = true,
                    palette = palette,
                )
                Spacer(Modifier.height(Spacing.s2))
                OptionRow(
                    icon = Icons.Outlined.Mic,
                    title = "+ Voice notes",
                    subtitle = "Not stored on this device — audio is transcribed then discarded.",
                    trailing = "n/a",
                    selected = false,
                    enabled = false,
                    palette = palette,
                )
            }
            Spacer(Modifier.height(Spacing.s4))

            if (connected) {
                Card(palette) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Automatic backup", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                            Text("Back up quietly whenever your record changes.", style = MaterialTheme.typography.bodySmall, color = palette.text3)
                        }
                        Switch(
                            checked = state.autoBackup,
                            onCheckedChange = { viewModel.setAutoBackup(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = palette.primary,
                                uncheckedTrackColor = palette.surface2,
                                uncheckedBorderColor = palette.border,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(Spacing.s4))

                PrimaryButton("Back up now", palette, enabled = state.busy == null, busy = state.busy == BackupViewModel.Busy.BACKING_UP) {
                    viewModel.backupNow()
                }
                if (state.backupExists) {
                    Spacer(Modifier.height(Spacing.s2))
                    SecondaryButton("Restore from Drive", palette, enabled = state.busy == null) { viewModel.restoreFromDrive() }
                }
                Spacer(Modifier.height(Spacing.s2))
                TextAction("Disconnect Google Drive", palette) { viewModel.disconnect() }
            } else {
                PrimaryButton("Connect Google Drive", palette, enabled = state.busy == null && state.configured, busy = state.busy == BackupViewModel.Busy.CONNECTING) {
                    signInLauncher.launch(viewModel.signInIntent())
                }
                if (!state.configured) {
                    Spacer(Modifier.height(Spacing.s2))
                    Text(
                        "Drive backup isn't set up on this build yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.text3,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.s5))
            Card(palette) {
                Label("ON YOUR DEVICE", palette)
                Spacer(Modifier.height(Spacing.s2))
                Text(
                    "A local copy is the hard fallback — save a backup file anywhere, or restore from one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                Spacer(Modifier.height(Spacing.s3))
                SecondaryButton("Export a copy to my device", palette, enabled = state.busy == null) {
                    exportLauncher.launch(viewModel.exportFileName())
                }
                Spacer(Modifier.height(Spacing.s2))
                TextAction("Restore from a file", palette) { importLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
            }
            Spacer(Modifier.height(Spacing.s6))
        }
    }
}

@Composable
private fun HealthCard(state: BackupViewModel.UiState, palette: BragPalette) {
    val connected = state.connectedEmail != null
    when {
        connected && state.lastBackupAt > 0L -> {
            val rel = DateUtils.getRelativeTimeSpanString(
                state.lastBackupAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
            ).toString()
            StatusCard(palette.positiveSoft, palette.positive, Icons.Outlined.CheckCircle, "Backed up · $rel", state.connectedEmail!!, palette)
        }
        connected -> StatusCard(palette.primarySoft, palette.primary, Icons.Outlined.CloudUpload, "Connected", state.connectedEmail!!, palette)
        else -> StatusCard(palette.surface2, palette.text2, Icons.Outlined.CloudUpload, "Not backed up", "Connect Google Drive to keep your record safe.", palette)
    }
}

@Composable
private fun StatusCard(fill: Color, ink: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, palette: BragPalette) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.lg)).background(fill).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(Radii.md)).background(palette.surface), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ink, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        }
    }
}

@Composable
private fun OptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    trailing: String,
    selected: Boolean,
    enabled: Boolean,
    palette: BragPalette,
) {
    val ink = if (enabled) palette.text1 else palette.text3
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radii.md))
            .border(1.5.dp, if (selected) palette.primary else palette.border, RoundedCornerShape(Radii.md))
            .background(if (selected) palette.primarySoft else palette.surface)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) palette.primary else palette.text3, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(Spacing.s3))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ink, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = palette.text3)
        }
        Spacer(Modifier.size(Spacing.s2))
        Text(
            trailing,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) palette.primary else palette.text3,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (enabled) palette.surface else palette.surface2).padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PrimaryButton(text: String, palette: BragPalette, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp))
            .background(if (enabled) palette.primary else palette.surface2)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(Spacing.s2))
        }
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (enabled) Color.White else palette.text3, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SecondaryButton(text: String, palette: BragPalette, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(999.dp)).background(palette.primarySoft)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = palette.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TextAction(text: String, palette: BragPalette, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = palette.text3, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Label(text: String, palette: BragPalette) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = palette.text3, fontWeight = FontWeight.Bold)
}

@Composable
private fun Card(palette: BragPalette, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radii.lg)).background(palette.surface).padding(Spacing.card),
        content = content,
    )
}
