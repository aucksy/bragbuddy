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
import androidx.compose.material.icons.outlined.CloudOff
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
        if (granted) vm.startVoice() else vm.setMode(CaptureMode.TYPE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Redo (from Home) re-records over an existing entry instead of creating a new one.
        intent.getLongExtra(EXTRA_REPLACE_ID, 0L).let { if (it > 0L) vm.setReplaceId(it) }
        // Folder tap → capture straight into that project (no spoken prefix needed).
        intent.getStringExtra(EXTRA_PROJECT)?.let { vm.setAnchorProject(it) }

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

                // Enter voice mode → ensure mic permission, then listen. Waits for settings to load
                // (initialized) so a Type-preferring user isn't hit with the mic before their last
                // mode resolves. Re-runs when the mode or initialized flips.
                LaunchedEffect(state.mode, state.initialized) {
                    if (state.initialized && state.mode == CaptureMode.SPEAK && state.phase == VoicePhase.IDLE) {
                        if (hasMic()) vm.startVoice() else requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                when {
                    justSaved -> SavedConfirmation()
                    // Offline voice capture → the clip is queued (never lost) and the surface
                    // confirms + dismisses, mirroring the normal save.
                    state.queuedOffline -> {
                        LaunchedEffect(Unit) { delay(1400); finish() }
                        QueuedOfflineConfirmation()
                    }
                    // Post-save "add a number?" nudge (entry already saved; never blocks).
                    state.savedNudge -> SavedNudgeSheet(
                        state = state,
                        onAddNumber = vm::startAddNumber,
                        onNumberChange = vm::onNumberDraftChange,
                        onConfirmNumber = { vm.confirmNumber(); finish() },
                        onSkip = { finish() },
                        onDismiss = { finish() },
                    )
                    else -> CaptureScreen(
                        state = state,
                        onSetMode = vm::setMode,
                        onStopSubmit = vm::stopAndSubmitVoice,
                        onRetry = vm::retryVoice,
                        onSaveForLater = vm::saveForLater,
                        onTypedChange = vm::onTypedChange,
                        onSubmitTyped = vm::submitTyped,
                        onReviewChange = vm::onReviewTextChange,
                        onConfirmAdd = vm::confirmAdd,
                        onReRecord = vm::reRecord,
                        onStartVoiceNumber = vm::startVoiceNumber,
                        onStopVoiceNumber = vm::stopVoiceNumber,
                        onStartTypeNumber = vm::startTypeNumber,
                        onNumberDraftChange = vm::onNumberDraftChange,
                        onConfirmTypeNumber = vm::confirmTypeNumber,
                        onCancelNumber = vm::cancelNumber,
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    companion object {
        /** Long extra: the id of an entry to re-record over (Home → Redo). */
        const val EXTRA_REPLACE_ID = "replace_entry_id"

        /** String extra: a project name to anchor this capture to (Home folder tap). */
        const val EXTRA_PROJECT = "anchor_project"
    }
}

/** Offline-queue confirmation: the clip is kept and will be transcribed when the network is back. */
@Composable
private fun QueuedOfflineConfirmation() {
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
                Modifier.size(32.dp).clip(RoundedCornerShape(999.dp)).background(palette.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.CloudOff, null, tint = palette.primary, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(Spacing.s3))
            androidx.compose.foundation.layout.Column {
                Text("Saved for later", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = palette.text1)
                Text("I'll transcribe it when you're back online.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = palette.text3)
            }
        }
    }
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
