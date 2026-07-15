package com.bragbuddy.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.foundation.layout.Box
import com.bragbuddy.app.ui.backup.BackupScreen
import com.bragbuddy.app.ui.common.BragSnackbarHost
import com.bragbuddy.app.ui.common.LocalSnackbarController
import com.bragbuddy.app.ui.common.rememberSnackbarController
import com.bragbuddy.app.ui.legal.PrivacyScreen
import com.bragbuddy.app.ui.main.MainScaffold
import com.bragbuddy.app.ui.onboarding.OnboardingScreen
import com.bragbuddy.app.ui.pillar.PillarDetailScreen
import com.bragbuddy.app.ui.reliability.ReliabilityScreen
import com.bragbuddy.app.ui.settings.AdvancedScreen
import com.bragbuddy.app.ui.settings.SettingsScreen
import com.bragbuddy.app.ui.theme.BragBuddyTheme

/** The top-level navigation graph. The tabbed app shell lives in [MainScaffold]; Settings, the deep
 *  pillar view, and the privacy screen are pushed screens. Capture is its own Activity. The first-run
 *  onboarding wizard gates the start destination via [RootGateViewModel]. */
@Composable
fun BragNavHost(
    openCaptureSignal: Int = 0,
    gateViewModel: RootGateViewModel = hiltViewModel(),
) {
    val gate by gateViewModel.gate.collectAsStateWithLifecycle()

    // Still loading the onboarding flag — hold on a blank splash-coloured frame (a frame or two).
    val resolved = gate ?: run {
        Box(Modifier.fillMaxSize().background(BragBuddyTheme.palette.bg))
        return
    }

    val navController = rememberNavController()
    // Snapshot once: DataStore may re-emit (e.g. when we flip the flag on finish), but the NavHost
    // start destination must not change under us — we navigate away manually instead.
    val startDestination = remember { if (resolved.showOnboarding) Routes.ONBOARDING else Routes.HOME }
    val reacceptOnly = remember { resolved.reacceptOnly }

    // A daily-reminder tap ([MainActivity.openCaptureSignal]) must open the capture radial regardless of
    // which pushed route (Settings, a pillar detail, …) is on top — the radial lives in [MainScaffold]
    // (the HOME destination), so bring HOME to the front here on each NEW signal; MainScaffold's own
    // effect then fans the radial out. Its own last-handled tracker keeps a plain recomposition from
    // re-navigating. (A no-op when HOME is already current, or still absent during onboarding.)
    var lastNavCaptureSignal by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(openCaptureSignal) {
        if (openCaptureSignal > 0 && openCaptureSignal != lastNavCaptureSignal) {
            lastNavCaptureSignal = openCaptureSignal
            navController.popBackStack(Routes.HOME, inclusive = false)
        }
    }

    // M2 · one app-wide themed snackbar host (replaces scattered system toasts), available to every
    // destination via [LocalSnackbarController] and floated above the whole nav graph.
    val snackbar = rememberSnackbarController()
    CompositionLocalProvider(LocalSnackbarController provides snackbar) {
        Box(Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                reacceptOnly = reacceptOnly,
                onFinished = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.HOME) {
            MainScaffold(
                openCaptureSignal = openCaptureSignal,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPillar = { pillarId -> navController.navigate(Routes.pillar(pillarId)) },
                onOpenFolder = { pillarId, folder -> navController.navigate(Routes.folder(pillarId, folder)) },
                onOpenReliability = { navController.navigate(Routes.RELIABILITY) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
                onOpenReliability = { navController.navigate(Routes.RELIABILITY) },
                onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                onOpenAdvanced = { navController.navigate(Routes.ADVANCED) },
            )
        }
        composable(Routes.ADVANCED) {
            AdvancedScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.RELIABILITY) {
            ReliabilityScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PRIVACY) {
            PrivacyScreen(onBack = { navController.popBackStack() })
        }
        composable(
            Routes.PILLAR,
            arguments = listOf(
                navArgument("pillarId") { type = NavType.StringType },
                navArgument("folder") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            PillarDetailScreen(onBack = { navController.popBackStack() })
        }
            }
            BragSnackbarHost(controller = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
