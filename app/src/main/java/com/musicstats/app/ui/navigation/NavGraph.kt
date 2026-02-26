package com.musicstats.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.musicstats.app.ui.home.HomeScreen
import com.musicstats.app.ui.library.LibraryScreen
import com.musicstats.app.ui.library.SongDetailScreen
import com.musicstats.app.ui.onboarding.OnboardingScreen
import com.musicstats.app.ui.settings.SettingsScreen
import com.musicstats.app.ui.stats.StatsScreen

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
            HomeScreen()
        }
        composable("stats") {
            StatsScreen()
        }
        composable("library") {
            LibraryScreen(onSongClick = { songId ->
                navController.navigate("song/$songId")
            })
        }
        composable("settings") {
            SettingsScreen()
        }
        composable(
            "song/{songId}",
            arguments = listOf(navArgument("songId") { type = NavType.LongType })
        ) {
            SongDetailScreen()
        }
    }
}
