package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.model.MomentTier
import com.musicstats.app.ui.theme.LocalAlbumPalette
import java.util.Locale

/**
 * A 360×640dp shareable card for a Moment.
 * Matches the in-app MomentCard look: full-bleed image, scrim, bottom-anchored text with pill chips.
 */
@Composable
fun MomentShareCard(
    moment: Moment,
    dominantColor: Color? = null,
    darkMutedColor: Color? = null,
    imageUrl: String? = null
) {
    val palette = LocalAlbumPalette.current
    val tier = remember(moment.tier) {
        try { MomentTier.valueOf(moment.tier) } catch (_: Exception) { MomentTier.BRONZE }
    }
    val dateStr = remember(moment.triggeredAt) {
        java.time.Instant.ofEpochMilli(moment.triggeredAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }
    val backgroundModel: Any? = imageUrl ?: moment.imageUrl ?: momentBackgroundDrawable(moment.type)
    val borderModifier = tier.borderColors?.let { colors ->
        val borderWidth = if (tier == MomentTier.GOLD) 3.dp else 2.dp
        Modifier.border(borderWidth, Brush.linearGradient(colors), RoundedCornerShape(20.dp))
    } ?: Modifier

    Box(
        modifier = Modifier
            .size(360.dp)
            .then(borderModifier)
            .clip(RoundedCornerShape(20.dp))
    ) {
        // Layer 1: image or dark placeholder
        // allowHardware(false) is required because ShareCardRenderer draws to a software Canvas
        if (backgroundModel != null) {
            val imgContext = androidx.compose.ui.platform.LocalContext.current
            val softwareModel = remember(backgroundModel) {
                ImageRequest.Builder(imgContext)
                    .data(backgroundModel)
                    .allowHardware(false)
                    .size(Size.ORIGINAL)
                    .build()
            }
            AsyncImage(
                model = softwareModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp)
                    .drawWithContent {
                        drawContent()
                        // Darken the blurred image so it reads as ambient backdrop
                        drawRect(Color.Black.copy(alpha = 0.30f))
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A28))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                palette.accent.copy(alpha = tier.glowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 900f
                        )
                    )
            )
        }

        // Layer 2: scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        // RARE badge for Gold cards
        if (tier == MomentTier.GOLD) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFFD700).copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "RARE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }

        // Layer 3: text anchored to bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 28.dp, vertical = 32.dp)
        ) {
            if (moment.entityName != null) {
                Text(
                    text = moment.entityName,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            val titleColor = if (tier == MomentTier.GOLD) Color.Unspecified else Color.White
            val titleStyle = if (tier == MomentTier.GOLD) {
                MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFD700)))
                )
            } else {
                MaterialTheme.typography.headlineMedium
            }
            Text(
                text = moment.title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = moment.description,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.70f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (moment.statLines.isNotEmpty()) {
                val pillBackground = when (tier) {
                    MomentTier.GOLD -> Color(0xFFFFD700).copy(alpha = 0.20f)
                    MomentTier.SILVER -> palette.accent.copy(alpha = 0.20f)
                    MomentTier.BRONZE -> Color.White.copy(alpha = 0.15f)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    moment.statLines.forEach { stat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBackground)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stat,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.40f)
                )
                Text(
                    text = "vibes",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}
