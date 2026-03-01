package com.musicstats.app.ui.components

import androidx.compose.foundation.background
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
    val dateStr = remember(moment.triggeredAt) {
        java.time.Instant.ofEpochMilli(moment.triggeredAt)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault()))
    }
    val backgroundModel: Any? = imageUrl ?: moment.imageUrl ?: momentBackgroundDrawable(moment.type)

    Box(
        modifier = Modifier
            .size(360.dp)
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
                    .blur(16.dp)
                    .drawWithContent {
                        drawContent()
                        // Darken the blurred image so it reads as ambient backdrop
                        drawRect(Color.Black.copy(alpha = 0.35f))
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
                                palette.accent.copy(alpha = 0.30f),
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
                            palette.accent.copy(alpha = 0.10f),
                            Color.Black.copy(alpha = 0.80f)
                        )
                    )
                )
        )

        // Layer 3: text anchored to bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(horizontal = 32.dp, vertical = 36.dp)
        ) {
            if (moment.entityName != null) {
                Text(
                    text = moment.entityName,
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.5.sp),
                    color = Color.White.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = moment.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = moment.description,
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp),
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            if (moment.statLines.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    moment.statLines.forEach { stat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(palette.accent.copy(alpha = 0.18f))
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
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.30f)
                )
                Text(
                    text = "vibes",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                    color = Color.White.copy(alpha = 0.25f)
                )
            }
        }
    }
}
