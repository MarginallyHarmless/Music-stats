package com.musicstats.app.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.musicstats.app.service.TrackingService
import com.musicstats.app.ui.navigation.BottomNavBar
import com.musicstats.app.ui.navigation.NavGraph
import com.musicstats.app.ui.onboarding.OnboardingViewModel
import com.musicstats.app.ui.theme.MusicStatsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
            MusicStatsTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route
                val startDest = if (onboardingComplete) "home" else "onboarding"
                val showBottomBar = currentRoute in listOf("home", "stats", "library", "settings")

                Scaffold(
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
