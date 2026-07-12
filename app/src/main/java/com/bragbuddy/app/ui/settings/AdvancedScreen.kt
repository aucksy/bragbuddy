package com.bragbuddy.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.ai.AiEndpointConfig
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * Phase M1 · "AI engine" (reached from Settings). Shows how the AI runs right now — **managed** by
 * BragBuddy's relay (the default once the owner has deployed it) or **direct-Groq** with the user's own
 * key — and hosts the BYOK key field. BYOK is the test-mode setup and the power-user escape hatch: a
 * key here always wins and sends requests straight to Groq, bypassing the relay. Reuses
 * [SettingsViewModel] (no new VM). [AiEndpointConfig.proxyConfigured] reflects the baked build config.
 */
@Composable
fun AdvancedScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val palette = BragBuddyTheme.palette
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hasKey = settings.groqApiKey.isNotBlank()
    val managed = AiEndpointConfig.proxyConfigured
    val uriHandler = LocalUriHandler.current
    // The key is masked by default (it's a secret); a show/hide eye reveals it to verify a paste.
    var revealKey by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = palette.bg,
        topBar = {
            TopAppBar(
                title = { Text("AI engine", style = MaterialTheme.typography.headlineMedium, color = palette.text1) },
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
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            // Current-mode card.
            Card(palette) {
                Text("How your AI runs", style = MaterialTheme.typography.titleMedium, color = palette.text1)
                Spacer(Modifier.height(Spacing.s2))
                val status = when {
                    hasKey -> "Your own Groq key ✓"
                    managed -> "Managed by BragBuddy ✓"
                    else -> "Not set up yet"
                }
                Text(status, style = MaterialTheme.typography.titleSmall, color = palette.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(Spacing.s2))
                Text(
                    when {
                        hasKey ->
                            "Requests go straight to Groq using your key — cleaning, filing and transcribing " +
                                "your notes. Remove the key below to switch back to managed AI."
                        managed ->
                            "BragBuddy handles the AI for you — nothing to set up. Your notes are sent to Groq " +
                                "through BragBuddy's own relay, which forwards each request and stores nothing."
                        else ->
                            "Add your Groq key below to turn on voice transcription and AI filing. Until then, " +
                                "typing always works and entries wait in the Inbox."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
            }

            Spacer(Modifier.height(Spacing.s4))

            // BYOK key field (Advanced when managed is available; the primary setup otherwise).
            Card(palette) {
                Text(
                    if (managed) "Use your own Groq key (Advanced)" else "Your Groq key",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.text1,
                )
                Text(
                    if (managed)
                        "Prefer to send requests straight to Groq with your own key instead of our relay? " +
                            "Paste it here — it bypasses the relay entirely. Leave empty to use managed AI."
                    else
                        "Powers both AI filing and cloud Whisper transcription. One free key at " +
                            "console.groq.com → API Keys. Stored on this device only, never uploaded to us.",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.text3,
                )
                if (!managed) {
                    Spacer(Modifier.height(Spacing.s2))
                    Text(
                        "Open console.groq.com ↗",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { uriHandler.openUri("https://console.groq.com/keys") }
                            .padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.s3))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .border(1.5.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(Radii.md))
                        .background(palette.surface)
                        .padding(start = Spacing.s4, end = Spacing.s2, top = Spacing.s3, bottom = Spacing.s3),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
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
                            // Masked by default so the secret isn't shoulder-surfable; the eye reveals it.
                            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                        )
                    }
                    if (hasKey) {
                        IconButton(onClick = { revealKey = !revealKey }) {
                            Icon(
                                if (revealKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                if (revealKey) "Hide key" else "Show key",
                                tint = palette.text3,
                            )
                        }
                    }
                }
                if (hasKey) {
                    Spacer(Modifier.height(Spacing.s3))
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { viewModel.setGroqApiKey("") }
                            .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (managed) "Remove key & use managed AI" else "Remove key",
                            style = MaterialTheme.typography.titleSmall,
                            color = palette.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
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
