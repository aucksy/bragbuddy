package com.bragbuddy.app.ui.role

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bragbuddy.app.data.speech.SpeechToText
import com.bragbuddy.app.data.speech.SttEvent
import com.bragbuddy.app.ui.theme.BragPalette
import com.bragbuddy.app.ui.theme.Radii
import com.bragbuddy.app.ui.theme.Spacing

/**
 * A low-friction "type or speak" text field, reused for the job role (first-run prompt + Settings).
 * Voice uses the free on-device [SpeechToText] (no key needed) to fill the field; typing is always
 * available. Single line — the role is short.
 */
@Composable
fun RoleInput(
    value: String,
    onValueChange: (String) -> Unit,
    palette: BragPalette,
    modifier: Modifier = Modifier,
    placeholder: String = "e.g. Product Owner",
    onImeDone: () -> Unit = {},
) {
    val context = LocalContext.current
    val stt = remember { SpeechToText(context) }
    var listening by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { stt.cancelAndRelease() } }

    val startListening = {
        listening = true
        stt.start { event ->
            when (event) {
                is SttEvent.Partial -> onValueChange(event.text)
                is SttEvent.Final -> { if (event.text.isNotBlank()) onValueChange(event.text); listening = false }
                is SttEvent.Error -> listening = false
                else -> {}
            }
        }
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening()
    }

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(Radii.md))
            .border(1.5.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(Radii.md))
            .background(palette.surface)
            .padding(start = Spacing.s4, end = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = palette.text3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = LocalTextStyle.current.merge(TextStyle(color = palette.text1, fontSize = 15.sp)),
                cursorBrush = SolidColor(palette.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onImeDone() }),
            )
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(if (listening) palette.primaryPress else palette.primarySoft)
                .clickable {
                    when {
                        listening -> { stt.stop(); listening = false }
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> startListening()
                        else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (listening) Icons.Outlined.Stop else Icons.Outlined.Mic,
                contentDescription = if (listening) "Stop" else "Speak",
                tint = palette.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
