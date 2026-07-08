package com.bragbuddy.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bragbuddy.app.ui.theme.BragBuddyTheme

/**
 * Protects a text editor / overlay from losing unsaved input on ANY exit path. Returns a
 * `requestDismiss` lambda — wire it to EVERY way out of the surface (the scrim tap, the close ✕, and,
 * via the built-in [BackHandler], the system Back gesture/button). When [dirty] is true it raises a
 * "Discard changes?" confirmation; otherwise it dismisses immediately.
 *
 * This also closes the app-wide gap where system Back on a composed overlay (not a nav destination) fell
 * through to the NavController and **exited the app** (or popped the wrong screen / discarded an edit)
 * instead of closing the overlay — with this installed, Back always closes the overlay first.
 *
 * [message] lets a surface tailor the body (e.g. "This note hasn't been added yet.").
 */
@Composable
fun rememberDiscardGuard(
    dirty: Boolean,
    onDismiss: () -> Unit,
    message: String = "You have unsaved changes. Discard them?",
): () -> Unit {
    var showConfirm by remember { mutableStateOf(false) }

    // Back always closes the overlay (never the app); confirm first when there are unsaved edits.
    // Disabled while our own confirm dialog is up — that dialog owns Back then (Keep editing).
    BackHandler(enabled = !showConfirm) { if (dirty) showConfirm = true else onDismiss() }

    if (showConfirm) {
        val palette = BragBuddyTheme.palette
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onDismiss() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Keep editing") } },
            title = { Text("Discard changes?", color = palette.text1) },
            text = { Text(message, color = palette.text3) },
            containerColor = palette.surface,
        )
    }

    return { if (dirty) showConfirm = true else onDismiss() }
}
