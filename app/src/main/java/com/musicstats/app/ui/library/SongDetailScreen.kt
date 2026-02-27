package com.musicstats.app.ui.library

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import androidx.compose.runtime.CompositionLocalProvider
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.ui.components.AuroraBackground
import com.musicstats.app.ui.components.SectionHeader
import com.musicstats.app.ui.components.StatCard
import com.musicstats.app.ui.theme.AlbumPalette
import com.musicstats.app.ui.theme.LocalAlbumPalette
import com.musicstats.app.ui.theme.toAlbumPalette
import com.musicstats.app.ui.share.ShareCardRenderer
import com.musicstats.app.ui.share.SongSpotlightCard
import com.musicstats.app.ui.theme.MusicStatsTheme
import com.musicstats.app.util.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel = hiltViewModel()
) {
    val song by viewModel.song.collectAsState()
    val totalPlayCount by viewModel.totalPlayCount.collectAsState()
    val totalListeningTime by viewModel.totalListeningTime.collectAsState()
    val skipRate by viewModel.skipRate.collectAsState()
    val skipCount by viewModel.skipCount.collectAsState()
    val history by viewModel.listeningHistory.collectAsState()

    val context = LocalContext.current

    if (song == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Loading...", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val currentSong = song!!
    val songPalette = currentSong.toAlbumPalette()

    CompositionLocalProvider(LocalAlbumPalette provides songPalette) {
    AuroraBackground(palette = songPalette) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Share button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = {
                    val density = context.resources.displayMetrics.density
                    val w = (360 * density).toInt()
                    val h = (640 * density).toInt()
                    ShareCardRenderer.renderComposable(context, w, h, {
                        MusicStatsTheme {
                            SongSpotlightCard(
                                title = currentSong.title,
                                artist = currentSong.artist,
                                albumArtUrl = currentSong.albumArtUrl,
                                playCount = totalPlayCount,
                                totalTimeMs = totalListeningTime
                            )
                        }
                    }) { bitmap ->
                        ShareCardRenderer.shareBitmap(context, bitmap)
                    }
                }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Hero header
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (currentSong.albumArtUrl != null) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(230.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            LocalAlbumPalette.current.accent.copy(alpha = 0.15f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )
                        AsyncImage(
                            model = currentSong.albumArtUrl,
                            contentDescription = "Album art",
                            modifier = Modifier
                                .size(200.dp)
                                .shadow(8.dp, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = currentSong.title,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = currentSong.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (!currentSong.album.isNullOrBlank()) {
                    Text(
                        text = currentSong.album,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Stats row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Plays",
                        value = "$totalPlayCount",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Total Time",
                        value = formatDuration(totalListeningTime),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        label = "Skips",
                        value = "$skipCount",
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        label = "Skip Rate",
                        value = "${(skipRate * 100).toInt()}%",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // History header
        item {
            SectionHeader("Listening History")
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "No listening events yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        } else {
            items(history, key = { it.id }) { event ->
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = Instant.ofEpochMilli(event.startedAt)
                                .atZone(ZoneId.systemDefault())
                                .format(formatter),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .background(
                                    color = Color.White.copy(alpha = 0.10f),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = friendlyAppName(event.sourceApp),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                    Text(
                        text = formatDuration(event.durationMs),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
    }
    }
}

private fun friendlyAppName(packageName: String): String {
    return when {
        packageName.contains("spotify", ignoreCase = true) -> "Spotify"
        packageName.contains("music.youtube", ignoreCase = true) -> "YouTube Music"
        packageName.contains("youtube", ignoreCase = true) -> "YouTube"
        packageName.contains("apple.android.music", ignoreCase = true) -> "Apple Music"
        packageName.contains("tidal", ignoreCase = true) -> "Tidal"
        packageName.contains("deezer", ignoreCase = true) -> "Deezer"
        packageName.contains("pandora", ignoreCase = true) -> "Pandora"
        packageName.contains("soundcloud", ignoreCase = true) -> "SoundCloud"
        packageName.contains("amazon.mp3", ignoreCase = true) -> "Amazon Music"
        else -> packageName.substringAfterLast('.')
            .replaceFirstChar { it.uppercaseChar() }
    }
}
