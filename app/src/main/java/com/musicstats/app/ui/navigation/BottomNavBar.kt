package com.musicstats.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.musicstats.app.ui.theme.LocalAlbumPalette

enum class BottomNavItem(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Default.Home),
    Stats("stats", "Stats", Icons.Default.BarChart),
    Library("library", "Library", Icons.Default.LibraryMusic),
    Settings("settings", "Settings", Icons.Default.Settings),
    Debug("debug", "Debug", Icons.Default.BugReport)
}

@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val palette = LocalAlbumPalette.current
    NavigationBar(
        containerColor = Color(0xFF0A0A0F).copy(alpha = 0.95f)
    ) {
        BottomNavItem.entries.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = palette.accent,
                    selectedTextColor = palette.accent,
                    indicatorColor = palette.accent.copy(alpha = 0.15f),
                    unselectedIconColor = Color(0xFF808090),
                    unselectedTextColor = Color(0xFF808090)
                )
            )
        }
    }
}
