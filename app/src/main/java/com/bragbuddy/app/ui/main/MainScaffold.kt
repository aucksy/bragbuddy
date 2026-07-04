package com.bragbuddy.app.ui.main

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Mic
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bragbuddy.app.ui.capture.CaptureActivity
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
            onCapture = { context.startActivity(Intent(context, CaptureActivity::class.java)) },
        )

        if (showCatchup) {
            CatchupSheet(
                onAdd = {
                    viewModel.catchupHandled()
                    context.startActivity(Intent(context, CaptureActivity::class.java))
                },
                onSkip = { viewModel.catchupHandled() },
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
    onCapture: () -> Unit,
) {
    val palette = BragBuddyTheme.palette
    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(palette.surface)
                .align(Alignment.BottomCenter),
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
        // Raised capture FAB, centered and straddling the top edge of the bar.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp)
                .size(52.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.primary)
                .clickable(onClick = onCapture),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Mic, "Capture", tint = Color.White, modifier = Modifier.size(24.dp))
        }
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
