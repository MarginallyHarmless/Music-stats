package com.musicstats.app.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musicstats.app.service.TrackingService
import com.musicstats.app.ui.navigation.BottomNavBar
import com.musicstats.app.ui.navigation.NavGraph
import com.musicstats.app.ui.onboarding.OnboardingViewModel
import com.musicstats.app.ui.theme.AlbumPalette
import com.musicstats.app.ui.theme.MusicStatsTheme
import com.musicstats.app.ui.theme.PaletteViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val paletteViewModel: PaletteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        val onboardingComplete = OnboardingViewModel.isOnboardingComplete(this)
        if (onboardingComplete) {
            startService(Intent(this, TrackingService::class.java))
            // Request rebind of notification listener in case Android disconnected it
            NotificationListenerService.requestRebind(
                ComponentName(this, com.musicstats.app.service.MusicNotificationListener::class.java)
            )
        }

        setContent {
            val palette by paletteViewModel.currentPalette.collectAsState()
            val animDuration = 600

            val animatedPalette = AlbumPalette(
                dominant = animateColorAsState(palette.dominant, tween(animDuration), label = "dominant").value,
                vibrant = animateColorAsState(palette.vibrant, tween(animDuration), label = "vibrant").value,
                muted = animateColorAsState(palette.muted, tween(animDuration), label = "muted").value,
                darkVibrant = animateColorAsState(palette.darkVibrant, tween(animDuration), label = "darkVibrant").value,
                darkMuted = animateColorAsState(palette.darkMuted, tween(animDuration), label = "darkMuted").value,
                lightVibrant = animateColorAsState(palette.lightVibrant, tween(animDuration), label = "lightVibrant").value
            )

            MusicStatsTheme(albumPalette = animatedPalette) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val startDest = if (onboardingComplete) "home" else "onboarding"
                val showBottomBar = currentRoute in listOf("home", "stats", "library", "settings")

                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(currentRoute) { route ->
                                navController.navigate(route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    }
                ) { padding ->
                    NavGraph(
                        navController = navController,
                        startDestination = startDest,
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}
