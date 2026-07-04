package com.bragbuddy.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bragbuddy.app.ui.backup.BackupScreen
import com.bragbuddy.app.ui.main.MainScaffold
import com.bragbuddy.app.ui.pillar.PillarDetailScreen
import com.bragbuddy.app.ui.settings.SettingsScreen

/** The top-level navigation graph. The tabbed app shell lives in [MainScaffold]; Settings and the
 *  deep pillar view are pushed screens. Capture is its own Activity. */
@Composable
fun BragNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            MainScaffold(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenPillar = { pillarId -> navController.navigate(Routes.pillar(pillarId)) },
                onOpenFolder = { pillarId, folder -> navController.navigate(Routes.folder(pillarId, folder)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenBackup = { navController.navigate(Routes.BACKUP) },
            )
        }
        composable(Routes.BACKUP) {
            BackupScreen(onBack = { navController.popBackStack() })
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
