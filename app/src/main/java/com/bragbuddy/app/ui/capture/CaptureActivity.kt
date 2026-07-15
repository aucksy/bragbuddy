package com.bragbuddy.app.ui.capture

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BragBuddyThemedApp
import com.bragbuddy.app.ui.theme.Spacing
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.io.File

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

    // Image scanning (Phase A). Gallery = the modern photo picker (no storage permission). Camera =
    // the system camera writing to a FileProvider Uri (no CAMERA permission needed for that path).
    private var pendingPhotoUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onImageChosen(uri)
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (success && uri != null) vm.onImageChosen(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Restore an in-flight camera capture Uri across a config change / process death so the
        // returning photo isn't dropped; on a genuinely fresh launch, sweep any orphaned temp scans
        // left by a prior killed session (privacy + bounded cache).
        pendingPhotoUri = savedInstanceState?.let { BundleCompat.getParcelable(it, KEY_PENDING_PHOTO, Uri::class.java) }
        if (savedInstanceState == null) clearCaptureTempDir()

        // How this launch wants the surface to open: an explicit Speak/Type/Scan (Home radial pick),
        // ASK (in-context "+" chooser), DEFAULT (notification / nudge → the user's default method), or
        // absent (Redo → last-used mode). Applied synchronously before the sheet composes.
        vm.applyStart(intent.getStringExtra(EXTRA_START_MODE))
        // Redo (from Home) re-records over an existing entry instead of creating a new one.
        intent.getLongExtra(EXTRA_REPLACE_ID, 0L).let { if (it > 0L) vm.setReplaceId(it) }
        // Folder tap → capture straight into that project (no spoken prefix needed).
        intent.getStringExtra(EXTRA_PROJECT)?.let { vm.setAnchorProject(it) }

        setContent {
            // Translucent overlay → hold the surface until the theme resolves (no splash to mask a
            // forced-theme cold-start flash); the transparent window shows nothing for that brief moment.
            BragBuddyThemedApp(holdUntilLoaded = true) {
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
                // mode resolves, and holds while the "Ask each time" chooser is up (awaitingChoice).
                // Re-runs when the mode / initialized / awaitingChoice flips — so picking "Speak" from
                // the chooser (which clears awaitingChoice) auto-starts recording.
                LaunchedEffect(state.mode, state.initialized, state.awaitingChoice) {
                    if (state.initialized && !state.awaitingChoice &&
                        state.mode == CaptureMode.SPEAK && state.phase == VoicePhase.IDLE) {
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
                        onPickMode = vm::pickStartMode,
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
                        onTakePhoto = ::launchCamera,
                        onChooseImage = {
                            pickImage.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        onRetryImage = vm::retryImage,
                        onDismiss = { finish() },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingPhotoUri?.let { outState.putParcelable(KEY_PENDING_PHOTO, it) }
    }

    override fun onDestroy() {
        // Only when the sheet is truly finishing (NOT a config-change recreation, which still needs
        // the pending camera Uri): clear the temp scans so images aren't left on the device.
        if (isFinishing) clearCaptureTempDir()
        super.onDestroy()
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Delete the camera capture temp scans (privacy: images aren't kept; also bounds the cache). */
    private fun clearCaptureTempDir() {
        runCatching { File(cacheDir, "capture_images").listFiles()?.forEach { it.delete() } }
    }

    /** Open the system camera to a FileProvider Uri; on capture the VM reads + files it. */
    private fun launchCamera() {
        val uri = runCatching { createImageCaptureUri() }.getOrNull()
        if (uri == null) {
            vm.onImageError("Couldn't open the camera — choose an image or type it instead")
            return
        }
        pendingPhotoUri = uri
        runCatching { takePhoto.launch(uri) }.onFailure {
            pendingPhotoUri = null
            vm.onImageError("No camera available — choose an image or type it instead")
        }
    }

    private fun createImageCaptureUri(): Uri {
        val dir = File(cacheDir, "capture_images").apply { mkdirs() }
        val file = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    }

    companion object {
        private const val KEY_PENDING_PHOTO = "pending_photo_uri"

        /** Long extra: the id of an entry to re-record over (Home → Redo). */
        const val EXTRA_REPLACE_ID = "replace_entry_id"

        /** String extra: a project name to anchor this capture to (Home folder tap). */
        const val EXTRA_PROJECT = "anchor_project"

        /** String extra: how to open (Phase B). A [CaptureMode] name (explicit pick) or [START_ASK]
         *  (show the 3-choice chooser). Absent = last-used. [START_DEFAULT] is legacy (the "default
         *  capture method" was removed in v0.29.1) — kept only so a stale pre-upgrade reminder still in
         *  the tray opens the chooser instead of auto-recording. */
        const val EXTRA_START_MODE = "start_mode"
        const val START_ASK = "ASK"
        const val START_DEFAULT = "DEFAULT"
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
