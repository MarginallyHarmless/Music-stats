package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import coil3.compose.AsyncImage
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.model.MomentTier
import com.musicstats.app.ui.theme.LocalAlbumPalette
import java.util.Locale

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun MomentCard(
    moment: Moment,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUnseen = moment.seenAt == null
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
    val backgroundModel: Any? = moment.imageUrl ?: momentBackgroundDrawable(moment.type)
    val borderModifier = tier.borderColors?.let { colors ->
        val borderWidth = if (tier == MomentTier.GOLD) 2.dp else 1.5.dp
        Modifier.border(borderWidth, Brush.linearGradient(colors), CardShape)
    } ?: Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .then(borderModifier)
            .clip(CardShape)
            .clickable(onClick = onTap)
    ) {
        // Layer 1: image or dark placeholder
        if (backgroundModel != null) {
            AsyncImage(
                model = backgroundModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF1A1A28))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                palette.accent.copy(alpha = tier.glowAlpha),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = 600f
                        )
                    )
            )
        }

        // Layer 2: scrim
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.70f)
                        )
                    )
                )
        )

        // RARE badge for Gold cards
        if (tier == MomentTier.GOLD) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFFFD700).copy(alpha = 0.85f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
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
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            if (moment.entityName != null) {
                Text(
                    text = moment.entityName,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            val titleColor = if (tier == MomentTier.GOLD) Color.Unspecified else Color.White
            val titleStyle = if (tier == MomentTier.GOLD) {
                MaterialTheme.typography.titleLarge.copy(
                    brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFD700)))
                )
            } else {
                MaterialTheme.typography.titleLarge
            }
            Text(
                text = moment.title,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = moment.description,
                style = MaterialTheme.typography.bodyMedium,
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
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    moment.statLines.forEach { stat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBackground)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stat,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.40f),
                    modifier = Modifier.weight(1f)
                )
                if (isUnseen) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }
    }
}

/**
 * Returns a local drawable resource ID for the given moment type, or null if none is
 * configured yet. Add entries here when custom background images are provided.
 */
internal fun momentBackgroundDrawable(type: String): Int? = null
