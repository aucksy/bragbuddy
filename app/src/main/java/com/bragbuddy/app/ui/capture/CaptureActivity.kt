package com.bragbuddy.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay

/**
 * The light, overlay-style capture surface (Build Brief: "a light overlay-style screen that doesn't
 * fully open the app"). Launched from the Home mic FAB or the daily reminder; opens straight into
 * the last-used capture mode. On save it shows a brief "Saved" confirmation and dismisses — the AI
 * step (Phase 2) runs later, so the user is never blocked.
 */
@AndroidEntryPoint
class CaptureActivity : ComponentActivity() {

    private val vm: CaptureViewModel by viewModels()

    private val requestMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startListening() else vm.setMode(CaptureMode.TYPE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BragBuddyTheme {
                val state by vm.state.collectAsStateWithLifecycle()
                var justSaved by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    vm.saved.collect {
                        justSaved = true
                        delay(850)
                        finish()
                    }
                }

                // Enter voice mode → ensure mic permission, then listen. Re-runs if the user toggles.
                LaunchedEffect(state.mode) {
                    if (state.mode == CaptureMode.SPEAK && state.phase == VoicePhase.IDLE) {
                        if (hasMic()) vm.startListening() else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                if (justSaved) {
                    SavedConfirmation()
                } else {
                    CaptureScreen(
                        state = state,
                        onSetMode = vm::setMode,
                        onStopSubmit = vm::stopAndSubmitVoice,
                        onRetry = vm::retryVoice,
                        onTypedChange = vm::onTypedChange,
                        onSubmitTyped = vm::submitTyped,
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

/** The design-system "Saved" toast, shown briefly over the scrim before the surface dismisses. */
@Composable
private fun SavedConfirmation() {
    val palette = BragBuddyTheme.palette
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0E0F1A).copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(palette.surface)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(999.dp)).background(palette.positiveSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = palette.positive, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Spacing.s3))
            androidx.compose.foundation.layout.Column {
                Text("Saved", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = palette.text1)
                Text("I'll sort this out.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = palette.text3)
            }
        }
    }
}
