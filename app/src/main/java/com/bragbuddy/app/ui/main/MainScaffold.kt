package com.bragbuddy.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.data.prefs.CaptureMode
import com.bragbuddy.app.notification.NotificationPrimer
import com.bragbuddy.app.ui.capture.CaptureLauncher
import com.bragbuddy.app.ui.framework.FrameworkScreen
import com.bragbuddy.app.ui.home.HomeScreen
import com.bragbuddy.app.ui.inbox.InboxScreen
import com.bragbuddy.app.ui.summary.SummaryScreen
import com.bragbuddy.app.ui.theme.BragBuddyTheme

private enum class HomeTab(val label: String) { HOME("Home"), SUMMARY("Summary"), FRAMEWORK("Framework"), INBOX("Inbox") }

/**
 * The app shell: the design's bottom tab bar with the raised central **mic FAB** as the capture
 * trigger. Phase 2 wires Home (categorized entries), Framework (editor + refine-by-voice), and the
 * Inbox (read + retry). Summary stays a placeholder until Phase 5.
 */
@Composable
fun MainScaffold(
    onOpenSettings: () -> Unit,
    onOpenPillar: (String) -> Unit,
    onOpenFolder: (String, String) -> Unit,
    onOpenReliability: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val palette = BragBuddyTheme.palette
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(HomeTab.HOME) }
    // The Home "+" radial (Phase B): tapping the FAB fans Speak / Type / Scan; tap again or the scrim
    // to close. Always the deliberate chooser — independent of the Default capture method.
    var radialOpen by rememberSaveable { mutableStateOf(false) }
    val fabRotation by animateFloatAsState(if (radialOpen) 45f else 0f, label = "fabRotation")
    // Set by Home's early-preview banner: land on Summary and generate right away (the tap is the
    // consent for the one metered call). Consumed by SummaryScreen once its state is ready.
    var summaryAutoGenerate by rememberSaveable { mutableStateOf(false) }
    val inboxCount by viewModel.inboxCount.collectAsStateWithLifecycle()
    val showCatchup by viewModel.showCatchup.collectAsStateWithLifecycle()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottom = 74.dp + navInset

    // One evaluation per shell composition = one per app open (Design §7: the weekly catch-up
    // shows on open in its window, max once a week — never re-prompts while the app sits open).
    LaunchedEffect(Unit) { viewModel.maybeShowCatchup() }

    // Phase 3 · first-run notification-rationale popup. Replaces the old naked OS dialog that raced
    // Welcome: we explain WHY on first Home, then the popup's "Allow" launches the real request.
    val notifPrimerHandled by viewModel.notifPrimerHandled.collectAsStateWithLifecycle()
    var primerVisible by remember { mutableStateOf(false) }
    val notifAlreadyGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    // Result of the real OS request (launched from the popup's "Allow"). Either way just mark it
    // handled — NOT declined: if the OS denied (incl. a permanently-denied upgrader, where launch()
    // returns denied without a dialog), we deliberately leave the risk UN-acknowledged so the Home
    // reliability card can still surface and deep-link them to notification settings. Only the explicit
    // "Maybe later" / scrim acknowledges the risk (fully suppressing the card) — intent-based asymmetry.
    val requestNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        viewModel.markNotifPrimerHandled()
        primerVisible = false
    }
    LaunchedEffect(notifPrimerHandled) {
        val handled = notifPrimerHandled ?: return@LaunchedEffect // still loading — don't decide yet
        when (NotificationPrimer.decide(Build.VERSION.SDK_INT, notifAlreadyGranted, handled)) {
            NotificationPrimer.Decision.SHOW -> primerVisible = true
            NotificationPrimer.Decision.MARK_HANDLED -> viewModel.markNotifPrimerHandled()
            NotificationPrimer.Decision.NONE -> primerVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        when (tab) {
            HomeTab.HOME -> HomeScreen(
                onOpenSettings = onOpenSettings,
                onOpenPillar = onOpenPillar,
                onOpenFolder = onOpenFolder,
                onReviewInbox = { tab = HomeTab.INBOX },
                onOpenSummary = { summaryAutoGenerate = true; tab = HomeTab.SUMMARY },
                onOpenReliability = onOpenReliability,
                contentBottomPadding = contentBottom,
            )
            HomeTab.SUMMARY -> SummaryScreen(
                contentBottomPadding = contentBottom,
                onOpenSettings = onOpenSettings,
                autoGenerate = summaryAutoGenerate,
                onAutoGenerateConsumed = { summaryAutoGenerate = false },
            )
            HomeTab.FRAMEWORK -> FrameworkScreen(contentBottomPadding = contentBottom)
            HomeTab.INBOX -> InboxScreen(contentBottomPadding = contentBottom)
        }

        BottomBar(
            modifier = Modifier.align(Alignment.BottomCenter),
            selected = tab,
            inboxCount = inboxCount,
            onSelect = { tab = it },
        )

        // The radial overlay (scrim + fanned options) sits above the bar but below the FAB, so the
        // FAB stays tappable to close and its "+" → "×" rotation reads on top.
        if (radialOpen) {
            CaptureRadial(
                navInset = navInset,
                onPick = { mode -> radialOpen = false; CaptureLauncher.openMode(context, mode) },
                onDismiss = { radialOpen = false },
            )
        }
        CaptureFab(
            modifier = Modifier.align(Alignment.BottomCenter),
            navInset = navInset,
            rotation = fabRotation,
            open = radialOpen,
            onClick = { radialOpen = !radialOpen },
        )

        // The first-run notification primer takes precedence over the weekly catch-up so the two
        // custom scrims can never stack (the primer is rare and one-time).
        if (showCatchup && !primerVisible) {
            CatchupSheet(
                onAdd = {
                    viewModel.catchupHandled()
                    CaptureLauncher.openDefault(context)
                },
                onSkip = { viewModel.catchupHandled() },
            )
        }

        // Rendered last = topmost: a modal first-run ask that covers the bar + FAB until resolved.
        if (primerVisible) {
            NotificationPrimerSheet(
                onAllow = { requestNotif.launch(Manifest.permission.POST_NOTIFICATIONS) },
                onMaybeLater = { viewModel.markNotifPrimerDeclined(); primerVisible = false },
            )
        }
    }
}

@Composable
private fun BottomBar(
    modifier: Modifier,
    selected: HomeTab,
    inboxCount: Int,
    onSelect: (HomeTab) -> Unit,
) {
    val palette = BragBuddyTheme.palette
    // The bar itself; the raised "+" FAB is now rendered at the scaffold level (above the radial
    // overlay) — the central Spacer below still reserves its slot so the tab metrics are unchanged.
    Column(
        modifier
            .fillMaxWidth()
            .background(palette.surface),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabItem(HomeTab.HOME, selected, Icons.Filled.Home, Icons.Outlined.Home, onSelect, Modifier.weight(1f))
            TabItem(HomeTab.SUMMARY, selected, Icons.Outlined.Description, Icons.Outlined.Description, onSelect, Modifier.weight(1f))
            Spacer(Modifier.weight(1f)) // space for the FAB
            TabItem(HomeTab.FRAMEWORK, selected, Icons.Outlined.Dashboard, Icons.Outlined.Dashboard, onSelect, Modifier.weight(1f))
            TabItem(HomeTab.INBOX, selected, Icons.Outlined.Inbox, Icons.Outlined.Inbox, onSelect, Modifier.weight(1f), badge = inboxCount)
        }
    }
}

/**
 * The raised capture FAB — a "+" that straddles the top edge of the bar (Phase B). Tapping it opens
 * the radial; while open the "+" rotates 45° into an "×". Rendered at the scaffold level so it sits
 * above the radial scrim. Its bottom offset (navInset + 23dp) reproduces the pre-Phase-B geometry
 * exactly (it used to be `align(TopCenter).offset(y = -20dp)` inside the bar), so symmetry is intact.
 */
@Composable
private fun BoxScope.CaptureFab(modifier: Modifier, navInset: androidx.compose.ui.unit.Dp, rotation: Float, open: Boolean, onClick: () -> Unit) {
    val palette = BragBuddyTheme.palette
    Box(
        modifier
            .padding(bottom = navInset + 23.dp)
            .size(52.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            if (open) "Close" else "Capture",
            tint = Color.White,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * The three-option capture radial (Speak / Type / Scan) fanned up from the FAB. Not in the Design
 * System — built from tokens; look approved via the Phase B mockup. Options animate out from the FAB
 * (staggered scale + fade + travel); the scrim is the app's own capture scrim. Closing is instant
 * (unmount), so the full-screen scrim never lingers to swallow taps.
 */
@Composable
private fun BoxScope.CaptureRadial(navInset: androidx.compose.ui.unit.Dp, onPick: (CaptureMode) -> Unit, onDismiss: () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scrimAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "radialScrim")

    // Scrim (tap to dismiss).
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = scrimAlpha }
            .background(Color(0xFF0E0F1A).copy(alpha = 0.42f))
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
    )
    // Option cluster anchored at the FAB centre; each option offsets out along the upward arc.
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = navInset + 23.dp)
            .size(52.dp),
    ) {
        RadialOption(shown, 0, -84f, -66f, Icons.Outlined.Mic, "Speak") { onPick(CaptureMode.SPEAK) }
        RadialOption(shown, 1, 0f, -112f, Icons.Outlined.Keyboard, "Type") { onPick(CaptureMode.TYPE) }
        RadialOption(shown, 2, 84f, -66f, Icons.Outlined.PhotoCamera, "Scan") { onPick(CaptureMode.IMAGE) }
    }
}

@Composable
private fun BoxScope.RadialOption(
    shown: Boolean,
    index: Int,
    dx: Float,
    dy: Float,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    val p by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(durationMillis = 260, delayMillis = index * 45, easing = FastOutSlowInEasing),
        label = "radialOption$index",
    )
    Column(
        Modifier
            .align(Alignment.Center)
            .offset(x = (dx * p).dp, y = (dy * p).dp)
            .graphicsLayer { alpha = p; scaleX = 0.6f + 0.4f * p; scaleY = 0.6f + 0.4f * p },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(999.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = palette.primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFF0E0F1A).copy(alpha = 0.5f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun TabItem(
    tab: HomeTab,
    selected: HomeTab,
    activeIcon: ImageVector,
    inactiveIcon: ImageVector,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier,
    badge: Int = 0,
) {
    val palette = BragBuddyTheme.palette
    val isActive = tab == selected
    val tint = if (isActive) palette.primary else palette.text3
    val noRipple = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.clickable(interactionSource = noRipple, indication = null) { onSelect(tab) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(if (isActive) activeIcon else inactiveIcon, tab.label, tint = tint, modifier = Modifier.size(21.dp))
            if (badge > 0) {
                Box(
                    Modifier
                        .offset(x = 7.dp, y = (-5).dp)
                        .size(15.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.inbox),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (badge > 9) "9+" else badge.toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                    )
                }
            }
        }
        Text(
            tab.label,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            color = tint,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}
