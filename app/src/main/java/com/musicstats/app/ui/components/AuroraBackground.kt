package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.musicstats.app.ui.theme.AlbumPalette
import com.musicstats.app.ui.theme.LocalAlbumPalette

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    palette: AlbumPalette = LocalAlbumPalette.current,
    content: @Composable BoxScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.glowPrimary.copy(alpha = 0.20f),
                            Color.Transparent
                        ),
                        center = Offset(0.15f, 0.05f),
                        radius = 1800f
                    )
                )
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            palette.glowSecondary.copy(alpha = 0.15f),
                            Color.Transparent
                        ),
                        center = Offset(0.85f, 0.95f),
                        radius = 2200f
                    )
                ),
            content = content
        )
    }
}
