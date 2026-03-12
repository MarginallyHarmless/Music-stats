package com.musicstats.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        val onboardingComplete = OnboardingViewModel.isOnboardingComplete(this)
        if (onboardingComplete) {
            startService(Intent(this, TrackingService::class.java))
            requestBatteryOptimizationExemption()
        }

        setContent {
            MusicStatsTheme {
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

    @Suppress("BatteryLife")
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
