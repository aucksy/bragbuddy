package com.bragbuddy.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.bragbuddy.app.ui.legal.PrivacyScreen
import com.bragbuddy.app.ui.main.MainScaffold
import com.bragbuddy.app.ui.onboarding.OnboardingScreen
import com.bragbuddy.app.ui.pillar.PillarDetailScreen
import com.bragbuddy.app.ui.reliability.ReliabilityScreen
import com.bragbuddy.app.ui.settings.SettingsScreen
import com.bragbuddy.app.ui.theme.BragBuddyTheme

/** The top-level navigation graph. The tabbed app shell lives in [MainScaffold]; Settings, the deep
 *  pillar view, and the privacy screen are pushed screens. Capture is its own Activity. The first-run
 *  onboarding wizard gates the start destination via [RootGateViewModel]. */
@Composable
fun BragNavHost(gateViewModel: RootGateViewModel = hiltViewModel()) {
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
            )
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
}
