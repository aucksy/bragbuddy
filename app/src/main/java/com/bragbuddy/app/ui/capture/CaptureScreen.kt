package com.bragbuddy.app.ui.capture

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.BricolageGrotesque
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * The capture bottom sheet (Design System §4 "Capture — speak or type"): a sheet over a dimmed
 * backdrop, a Speak/Type toggle, and either the voice recorder or the text composer. After a voice
 * take it shows the transcript for **review + edit** — nothing is saved until the user taps **Add**
 * (they can Re-record or Cancel). A close ✕ is always available to cancel. Typed capture stays
 * instant. Stateless — the [CaptureViewModel] drives it; the host Activity owns permissions.
 */
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    onSetMode: (CaptureMode) -> Unit,
    onStopSubmit: () -> Unit,
    onRetry: () -> Unit,
    onTypedChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    onReviewChange: (String) -> Unit,
    onConfirmAdd: () -> Unit,
    onReRecord: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val noRipple = remember { MutableInteractionSource() }
    // The toggle is only relevant while choosing/recording — hide it once we're transcribing/reviewing.
    val showToggle = state.phase != VoicePhase.REVIEW && state.phase != VoicePhase.TRANSCRIBING

    Box(Modifier.fillMaxSize()) {
        // Scrim — tap outside the sheet to dismiss (cancel).
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
                .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = Radii.xl, topEnd = Radii.xl))
                .background(palette.surface)
                // Consume taps so they don't fall through to the scrim.
                .clickable(interactionSource = noRipple, indication = null, onClick = {})
                .padding(horizontal = 18.dp)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Handle (centered) + a close ✕ (cancel, always available).
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .width(42.dp).height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(palette.text3.copy(alpha = 0.35f)),
                )
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .size(30.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Close, "Cancel", tint = palette.text3, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(12.dp))

            state.anchorProject?.let { project ->
                AnchorBanner(project)
                Spacer(Modifier.height(Spacing.s3))
            }

            if (showToggle) {
                SpeakTypeToggle(mode = state.mode, onSetMode = onSetMode)
                Spacer(Modifier.height(Spacing.s4))
            }

            when (state.mode) {
                CaptureMode.SPEAK -> VoiceContent(
                    state, onStopSubmit, onRetry,
                    onSwitchToType = { onSetMode(CaptureMode.TYPE) },
                    onReviewChange = onReviewChange,
                    onConfirmAdd = onConfirmAdd,
                    onReRecord = onReRecord,
                    onCancel = onDismiss,
                )
                CaptureMode.TYPE -> TypeContent(state, onTypedChange, onSubmitTyped)
            }

            // Sit above the nav bar / keyboard.
            val bottomInset = maxOf(
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                WindowInsets.ime.asPaddingValues().calculateBottomPadding(),
            )
            Spacer(Modifier.height(18.dp + bottomInset))
        }
    }
}

/** Shown when capturing straight into a project folder — no spoken prefix needed. */
@Composable
private fun AnchorBanner(project: String) {
    val palette = BragBuddyTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(palette.primarySoft)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Folder, null, tint = palette.primary, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            "Filing into $project",
            style = MaterialTheme.typography.titleSmall,
            color = palette.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SpeakTypeToggle(mode: CaptureMode, onSetMode: (CaptureMode) -> Unit) {
    val palette = BragBuddyTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(palette.surface2)
            .padding(4.dp),
    ) {
        ToggleSegment(
            selected = mode == CaptureMode.SPEAK,
            icon = { Icon(Icons.Outlined.Mic, null, Modifier.size(15.dp)) },
            label = "Speak",
            onClick = { onSetMode(CaptureMode.SPEAK) },
            modifier = Modifier.weight(1f),
        )
        ToggleSegment(
            selected = mode == CaptureMode.TYPE,
            icon = { Icon(Icons.Outlined.Keyboard, null, Modifier.size(15.dp)) },
            label = "Type",
            onClick = { onSetMode(CaptureMode.TYPE) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ToggleSegment(
    selected: Boolean,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = BragBuddyTheme.palette
    val content = if (selected) palette.primary else palette.text3
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) palette.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides content,
        ) { icon() }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = content, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VoiceContent(
    state: CaptureUiState,
    onStopSubmit: () -> Unit,
    onRetry: () -> Unit,
    onSwitchToType: () -> Unit,
    onReviewChange: (String) -> Unit,
    onConfirmAdd: () -> Unit,
    onReRecord: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (state.phase == VoicePhase.REVIEW) {
            ReviewContent(state, onReviewChange, onConfirmAdd, onReRecord, onCancel)
            return
        }

        if (state.phase == VoicePhase.ERROR) {
            Spacer(Modifier.height(6.dp))
            Text(
                state.error ?: "Something went wrong",
                style = MaterialTheme.typography.titleMedium,
                color = palette.text1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton("Try again", primary = true, onClick = onRetry)
                PillButton("Type instead", primary = false, onClick = onSwitchToType)
            }
            Spacer(Modifier.height(8.dp))
            return
        }

        if (state.phase == VoicePhase.TRANSCRIBING) {
            Spacer(Modifier.height(10.dp))
            CircularProgressIndicator(color = palette.primary, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(14.dp))
            Text("Transcribing…", style = MaterialTheme.typography.titleMedium, color = palette.text1)
            Spacer(Modifier.height(10.dp))
            return
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (state.phase == VoicePhase.LISTENING) "Listening…" else "Getting ready…",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            formatElapsed(state.elapsedSec),
            fontFamily = BricolageGrotesque,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            color = palette.text1,
        )
        Spacer(Modifier.height(10.dp))
        Waveform(levels = state.levels, active = state.phase == VoicePhase.LISTENING)
        Spacer(Modifier.height(14.dp))
        // Stop = finish the take → review
        Box(
            Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.primary)
                .clickable(onClick = onStopSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color.White))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Tap to finish",
            style = MaterialTheme.typography.bodySmall,
            color = palette.text3,
        )
        if (state.anchorProject == null && state.partial.isBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Tip: start with a project name to file it fast",
                style = MaterialTheme.typography.bodySmall,
                color = palette.text3.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        if (state.partial.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                state.partial,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.text2,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun ReviewContent(
    state: CaptureUiState,
    onReviewChange: (String) -> Unit,
    onConfirmAdd: () -> Unit,
    onReRecord: () -> Unit,
    onCancel: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    Column(Modifier.fillMaxWidth()) {
        Text("Here's what I heard", style = MaterialTheme.typography.titleMedium, color = palette.text1)
        Text("Edit it if needed, then add it.", style = MaterialTheme.typography.bodySmall, color = palette.text3)
        Spacer(Modifier.height(Spacing.s3))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(RoundedCornerShape(Radii.md))
                .border(1.5.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(Radii.md))
                .background(palette.surface)
                .padding(14.dp),
        ) {
            if (state.reviewText.isEmpty()) {
                Text("Type your update…", style = MaterialTheme.typography.bodyLarge, color = palette.text3)
            }
            BasicTextField(
                value = state.reviewText,
                onValueChange = onReviewChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.merge(
                    TextStyle(color = palette.text1, fontSize = 14.sp, lineHeight = 21.sp),
                ),
                cursorBrush = SolidColor(palette.primary),
            )
        }
        Spacer(Modifier.height(Spacing.s4))
        // Add (primary, full-width)
        val canAdd = state.reviewText.isNotBlank()
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(if (canAdd) palette.primary else palette.primary.copy(alpha = 0.4f))
                .clickable(enabled = canAdd, onClick = onConfirmAdd)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Add", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(Spacing.s3))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextAction("Re-record", onReRecord)
            Spacer(Modifier.width(28.dp))
            TextAction("Cancel", onCancel)
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    val palette = BragBuddyTheme.palette
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = palette.text3,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Waveform(levels: List<Float>, active: Boolean) {
    val palette = BragBuddyTheme.palette
    val bars = 14
    Row(
        Modifier.height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            // Sample the tail of the level buffer; fall back to a gentle idle height.
            val raw = levels.getOrNull(levels.size - bars + i) ?: 0f
            val target = if (active) (0.15f + raw * 0.85f) else 0.18f
            val h by animateFloatAsState(targetValue = target, label = "bar$i")
            Box(
                Modifier
                    .width(3.dp)
                    .height((8 + h * 32).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.primary),
            )
        }
    }
}

@Composable
private fun TypeContent(
    state: CaptureUiState,
    onTypedChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .clip(RoundedCornerShape(Radii.md))
                .border(1.5.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(Radii.md))
                .background(palette.surface)
                .padding(14.dp),
        ) {
            if (state.typed.isEmpty()) {
                Text(
                    if (state.anchorProject == null)
                        "What did you get done?  e.g. “In my Onboarding project, shipped …”"
                    else "What did you get done?",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.text3,
                )
            }
            BasicTextField(
                value = state.typed,
                onValueChange = onTypedChange,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = LocalTextStyle.current.merge(
                    TextStyle(color = palette.text1, fontSize = 14.sp, lineHeight = 21.sp),
                ),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSubmitTyped() }),
            )
        }
        Spacer(Modifier.height(Spacing.s3))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "BragBuddy will clean & file it",
                style = MaterialTheme.typography.bodySmall,
                color = palette.text3,
            )
            val enabled = state.typed.isNotBlank()
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (enabled) palette.primary else palette.text3.copy(alpha = 0.4f))
                    .clickable(enabled = enabled, onClick = onSubmitTyped),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Save", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val palette = BragBuddyTheme.palette
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (primary) Color.White else palette.text2,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (primary) palette.primary else palette.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

private fun formatElapsed(sec: Int): String = "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
