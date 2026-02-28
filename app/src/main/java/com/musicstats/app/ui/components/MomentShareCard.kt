package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.musicstats.app.data.model.Moment

/**
 * A 360×640dp shareable card for a Moment.
 * Uses album art palette colors when available, otherwise themed gradients per archetype.
 */
@Composable
fun MomentShareCard(
    moment: Moment,
    dominantColor: Color? = null,
    darkMutedColor: Color? = null,
    imageUrl: String? = null
) {
    val isArchetype = moment.type.startsWith("ARCHETYPE_")

    val (gradientStart, gradientEnd) = if (dominantColor != null && darkMutedColor != null) {
        dominantColor to darkMutedColor
    } else {
        archetypeGradient(moment.type)
    }

    Box(
        modifier = Modifier
            .width(360.dp)
            .height(640.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(gradientStart, gradientEnd),
                    start = Offset.Zero,
                    end = Offset(360f * 2, 640f * 2)
                )
            )
            .padding(32.dp)
    ) {
        // Album art or artist image — top right (only for non-archetype cards)
        if (!isArchetype && imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .align(Alignment.TopEnd),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(8.dp))

            // Hero section
            Column {
                if (isArchetype) {
                    Text(
                        text = archetypeEmoji(moment.type),
                        fontSize = 72.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                Text(
                    text = moment.title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 52.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = moment.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Watermark
            Text(
                text = "vibes",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun archetypeGradient(type: String): Pair<Color, Color> = when {
    type.contains("NIGHT_OWL") -> Color(0xFF1A1A2E) to Color(0xFF16213E)
    type.contains("MORNING") -> Color(0xFFFF6B35) to Color(0xFFFF8C42)
    type.contains("COMMUTE") -> Color(0xFF2D6A4F) to Color(0xFF1B4332)
    type.contains("COMPLETIONIST") -> Color(0xFF6B2D8B) to Color(0xFF4A1E6E)
    type.contains("SKIPPER") -> Color(0xFFD62828) to Color(0xFF9B1B1B)
    type.contains("DEEP_CUT") -> Color(0xFF0D3B66) to Color(0xFF051E3E)
    type.contains("LOYAL_FAN") -> Color(0xFFE63946) to Color(0xFFA4262E)
    type.contains("EXPLORER") -> Color(0xFF2B9348) to Color(0xFF1A5C2A)
    else -> Color(0xFF333333) to Color(0xFF111111)
}

private fun archetypeEmoji(type: String): String = when {
    type.contains("NIGHT_OWL") -> "🌙"
    type.contains("MORNING") -> "☀️"
    type.contains("COMMUTE") -> "🎧"
    type.contains("COMPLETIONIST") -> "✅"
    type.contains("SKIPPER") -> "⏭️"
    type.contains("DEEP_CUT") -> "💿"
    type.contains("LOYAL_FAN") -> "❤️"
    type.contains("EXPLORER") -> "🧭"
    else -> "🎵"
}
