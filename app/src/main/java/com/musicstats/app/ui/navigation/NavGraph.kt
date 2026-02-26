package com.musicstats.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.musicstats.app.ui.onboarding.OnboardingScreen

@Composable
fun NavGraph(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable("onboarding") {
            OnboardingScreen(onFinished = {
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            })
        }
        composable("home") {
            // Placeholder — Task 9
            androidx.compose.material3.Text("Home Dashboard")
        }
        composable("stats") {
            // Placeholder — Task 10
            androidx.compose.material3.Text("Stats")
        }
        composable("library") {
            // Placeholder — Task 11
            androidx.compose.material3.Text("Library")
        }
        composable("settings") {
            // Placeholder — Task 12
            androidx.compose.material3.Text("Settings")
        }
        composable(
            "song/{songId}",
            arguments = listOf(navArgument("songId") { type = NavType.LongType })
        ) { backStackEntry ->
            val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
            // Placeholder — Task 11
            androidx.compose.material3.Text("Song Detail: $songId")
        }
    }
}
