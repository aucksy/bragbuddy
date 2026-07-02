package com.bragbuddy.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bragbuddy.app.ui.main.MainScaffold
import com.bragbuddy.app.ui.settings.SettingsScreen

/** The top-level navigation graph. The tabbed app shell lives in [MainScaffold]; Settings is a
 *  pushed screen (reached from the Home header for now). Capture is its own Activity. */
@Composable
fun BragNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            MainScaffold(onOpenSettings = { navController.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
