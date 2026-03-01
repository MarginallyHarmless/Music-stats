package com.musicstats.app.ui.share

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import com.musicstats.app.ui.components.StatCard
import com.musicstats.app.util.formatDuration

private val CardBackground = Color(0xFF0A0A0F)

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
    album: String? = null,
    playCount: Int,
    totalTimeMs: Long,
    skipCount: Int = 0,
    skipRate: Int = 0
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .width(360.dp)
            .height(640.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
    ) {
        // Hero image with title overlay
        Box(modifier = Modifier.fillMaxWidth()) {
            if (albumArtUrl != null) {
                val softwareModel = remember(albumArtUrl) {
                    ImageRequest.Builder(context)
                        .data(albumArtUrl)
                        .allowHardware(false)
                        .size(Size.ORIGINAL)
                        .build()
                }
                AsyncImage(
                    model = softwareModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, CardBackground),
                                startY = 500f
                            )
                        )
                )
            } else {
                Spacer(modifier = Modifier.height(200.dp))
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (!album.isNullOrBlank()) {
                    Text(
                        text = album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 2×2 stat grid
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(label = "Plays", value = "$playCount", modifier = Modifier.weight(1f))
                StatCard(label = "Total Time", value = formatDuration(totalTimeMs), modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(label = "Skips", value = "$skipCount", modifier = Modifier.weight(1f))
                StatCard(label = "Skip Rate", value = "$skipRate%", modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.weight(1f))

        // Watermark
        Text(
            text = "vibes",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}

@Composable
fun ArtistSpotlightCard(
    name: String,
    imageUrl: String?,
    playCount: Int,
    totalTimeMs: Long,
    skipCount: Int = 0,
    skipRate: Int = 0
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .width(360.dp)
            .height(640.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardBackground)
    ) {
        // Hero image with title overlay
        Box(modifier = Modifier.fillMaxWidth()) {
            if (imageUrl != null) {
                val softwareModel = remember(imageUrl) {
                    ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false)
                        .size(Size.ORIGINAL)
                        .build()
                }
                AsyncImage(
                    model = softwareModel,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, CardBackground),
                                startY = 500f
                            )
                        )
                )
            } else {
                Spacer(modifier = Modifier.height(200.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        // 2×2 stat grid
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(label = "Plays", value = "$playCount", modifier = Modifier.weight(1f))
                StatCard(label = "Total Time", value = formatDuration(totalTimeMs), modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(label = "Skips", value = "$skipCount", modifier = Modifier.weight(1f))
                StatCard(label = "Skip Rate", value = "$skipRate%", modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.weight(1f))

        // Watermark
        Text(
            text = "vibes",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier
                .align(Alignment.End)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        )
    }
}
