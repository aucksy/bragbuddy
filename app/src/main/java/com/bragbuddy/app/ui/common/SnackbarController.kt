package com.bragbuddy.app.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bragbuddy.app.ui.theme.BragBuddyTheme
import com.bragbuddy.app.ui.theme.Spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A tiny app-wide snackbar seam (M2) — themed snackbars replace the app's scattered
 * `android.widget.Toast` calls. Provided once at the nav root ([com.bragbuddy.app.ui.navigation]) so
 * every screen (tab content AND pushed destinations) can post a message with one line:
 * `LocalSnackbarController.current.show("…")`. Reads far calmer than a system toast and matches the
 * design tokens.
 */
class SnackbarController(
    val hostState: SnackbarHostState,
    private val scope: CoroutineScope,
) {
    /** Post a short message. A blank string is ignored; a new message replaces one already showing. */
    fun show(message: String) {
        if (message.isBlank()) return
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            hostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }
}

val LocalSnackbarController = staticCompositionLocalOf<SnackbarController> {
    error("No SnackbarController provided — wrap the UI in the nav root's provider.")
}

@Composable
fun rememberSnackbarController(): SnackbarController {
    val hostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    return remember(hostState, scope) { SnackbarController(hostState, scope) }
}

/** The themed host — a calm dark pill (light text) that adapts to the theme, pinned above the nav bar.
 *  Place it as the last child of a [Box] so it floats over the current screen. */
@Composable
fun BragSnackbarHost(controller: SnackbarController, modifier: Modifier = Modifier) {
    val palette = BragBuddyTheme.palette
    SnackbarHost(
        hostState = controller.hostState,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.screen, vertical = 12.dp),
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(14.dp),
            containerColor = palette.text1,
            contentColor = if (palette.isDark) palette.bg else Color.White,
        )
    }
}
