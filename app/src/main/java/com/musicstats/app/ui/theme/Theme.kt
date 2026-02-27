package com.musicstats.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val MusicStatsShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val DeepBackground = Color(0xFF0A0A0F)
private val DeepSurface = Color(0xFF0F0F18)
private val ElevatedSurface = Color(0xFF1A1A28)

@Composable
fun MusicStatsTheme(
    albumPalette: AlbumPalette = AlbumPalette(),
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = albumPalette.accent,
        onPrimary = Color.White,
        primaryContainer = albumPalette.darkVibrant,
        onPrimaryContainer = Color.White,
        secondary = albumPalette.muted,
        onSecondary = Color.White,
        secondaryContainer = albumPalette.darkMuted,
        onSecondaryContainer = Color.White,
        tertiary = albumPalette.lightVibrant,
        onTertiary = Color.White,
        tertiaryContainer = albumPalette.muted.copy(alpha = 0.5f),
        onTertiaryContainer = Color.White,
        background = DeepBackground,
        onBackground = Color.White,
        surface = DeepSurface,
        onSurface = Color.White,
        surfaceVariant = ElevatedSurface,
        onSurfaceVariant = Color(0xFFD0D0E0),
        surfaceContainerLowest = DeepBackground,
        surfaceContainerLow = Color(0xFF0D0D15),
        surfaceContainer = DeepSurface,
        surfaceContainerHigh = ElevatedSurface,
        surfaceContainerHighest = Color(0xFF222235),
        outline = Color(0xFF3A3A50),
        outlineVariant = Color(0xFF2A2A3A)
    )

    CompositionLocalProvider(LocalAlbumPalette provides albumPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MusicStatsTypography,
            shapes = MusicStatsShapes,
            content = content
        )
    }
}
