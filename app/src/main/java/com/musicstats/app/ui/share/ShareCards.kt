package com.musicstats.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.musicstats.app.util.formatDuration

@Composable
fun DailyRecapCard(
    listeningTimeMs: Long,
    topArtistName: String?,
    topArtistImageUrl: String?,
    topSongs: List<Pair<String, String>> // title to artist, max 3
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .width(360.dp)
            .height(640.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryContainer, tertiaryContainer),
                    start = Offset.Zero,
                    end = Offset(360f * 3, 640f * 3)
                )
            )
            .padding(32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: listening time hero
            Column {
                Text(
                    text = "Today I listened to",
                    style = MaterialTheme.typography.titleMedium,
                    color = onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = formatDuration(listeningTimeMs),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = onPrimaryContainer
                )
                Text(
                    text = "of music",
                    style = MaterialTheme.typography.titleMedium,
                    color = onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            // Middle: top artist
            if (topArtistName != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    if (topArtistImageUrl != null) {
                        AsyncImage(
                            model = topArtistImageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = topArtistName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Top Artist",
                        style = MaterialTheme.typography.bodySmall,
                        color = onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }

            // Bottom: top songs + watermark
            Column {
                if (topSongs.isNotEmpty()) {
                    Text(
                        text = "TOP SONGS",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = onPrimaryContainer.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    topSongs.take(3).forEachIndexed { index, (title, artist) ->
                        Text(
                            text = "${index + 1}. $title",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = onPrimaryContainer.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (index < topSongs.lastIndex) Spacer(Modifier.height(4.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Music Stats",
                    style = MaterialTheme.typography.labelSmall,
                    color = onPrimaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun SongSpotlightCard(
    title: String,
    artist: String,
    albumArtUrl: String?,
    playCount: Int,
    totalTimeMs: Long
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .width(360.dp)
            .height(640.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryContainer, tertiaryContainer),
                    start = Offset.Zero,
                    end = Offset(360f * 3, 640f * 3)
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "ON REPEAT",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = onPrimaryContainer.copy(alpha = 0.5f)
            )
            if (albumArtUrl != null) {
                AsyncImage(
                    model = albumArtUrl,
                    contentDescription = null,
                    modifier = Modifier.size(180.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = onPrimaryContainer,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = onPrimaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$playCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onPrimaryContainer)
                    Text(text = "plays", style = MaterialTheme.typography.bodySmall, color = onPrimaryContainer.copy(alpha = 0.6f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = formatDuration(totalTimeMs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onPrimaryContainer)
                    Text(text = "listened", style = MaterialTheme.typography.bodySmall, color = onPrimaryContainer.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "Music Stats", style = MaterialTheme.typography.labelSmall, color = onPrimaryContainer.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun ArtistSpotlightCard(
    name: String,
    imageUrl: String?,
    playCount: Int,
    totalTimeMs: Long
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .width(360.dp)
            .height(640.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(primaryContainer, tertiaryContainer),
                    start = Offset.Zero,
                    end = Offset(360f * 3, 640f * 3)
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "MY TOP ARTIST",
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                color = onPrimaryContainer.copy(alpha = 0.5f)
            )
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(160.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$playCount", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onPrimaryContainer)
                    Text(text = "plays", style = MaterialTheme.typography.bodySmall, color = onPrimaryContainer.copy(alpha = 0.6f))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = formatDuration(totalTimeMs), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = onPrimaryContainer)
                    Text(text = "listened", style = MaterialTheme.typography.bodySmall, color = onPrimaryContainer.copy(alpha = 0.6f))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(text = "Music Stats", style = MaterialTheme.typography.labelSmall, color = onPrimaryContainer.copy(alpha = 0.3f))
        }
    }
}
