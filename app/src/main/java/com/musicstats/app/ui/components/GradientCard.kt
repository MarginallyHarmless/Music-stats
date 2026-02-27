package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.musicstats.app.ui.theme.LocalAlbumPalette

@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalAlbumPalette.current
    val shape = MaterialTheme.shapes.large

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.glassBackground)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        palette.accent.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    endY = 150f
                )
            )
            .border(1.dp, palette.glassBorder, shape)
            .padding(24.dp),
        content = content
    )
}
