package com.musicstats.app.ui.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.musicstats.app.service.TrackingService
import com.musicstats.app.ui.home.HomeScreen
import com.musicstats.app.ui.library.ArtistDetailScreen
import com.musicstats.app.ui.library.LibraryScreen
import com.musicstats.app.ui.library.SongDetailScreen
import com.musicstats.app.ui.onboarding.OnboardingScreen
import com.musicstats.app.ui.settings.SettingsScreen
import com.musicstats.app.ui.stats.StatsScreen

@Composable
fun NavGraph(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable("onboarding") {
            val context = LocalContext.current
            OnboardingScreen(onFinished = {
                context.startService(Intent(context, TrackingService::class.java))
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
            LibraryScreen(
                onSongClick = { songId -> navController.navigate("song/$songId") },
                onArtistClick = { artistName -> navController.navigate("artist/${Uri.encode(artistName)}") }
            )
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
        composable(
            "artist/{artistName}",
            arguments = listOf(navArgument("artistName") { type = NavType.StringType })
        ) {
            ArtistDetailScreen()
        }
    }
}
