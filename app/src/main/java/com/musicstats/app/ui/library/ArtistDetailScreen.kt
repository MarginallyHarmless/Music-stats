package com.musicstats.app.ui.library

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.musicstats.app.data.dao.ArtistListeningEvent
import com.musicstats.app.data.dao.ArtistStats
import com.musicstats.app.util.formatDuration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ArtistDetailScreen(
    viewModel: ArtistDetailViewModel = hiltViewModel()
) {
    val imageUrl by viewModel.imageUrl.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val history by viewModel.listeningHistory.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Artist image",
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(80.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Artist image",
                    modifier = Modifier.size(160.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(
                text = viewModel.artistName,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Stats row
        item {
            ArtistStatsRow(stats)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // History header
        item {
            Text(
                text = "Listening History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "No listening events yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(history, key = { it.id }) { event ->
                ArtistEventItem(event)
                HorizontalDivider()
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ArtistStatsRow(stats: ArtistStats?) {
    val totalEvents = stats?.totalEvents ?: 0
    val totalTime = stats?.totalDurationMs ?: 0L
    val skipCount = stats?.skipCount ?: 0
    val skipRate = if (totalEvents > 0) (skipCount * 100) / totalEvents else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ArtistStatBox(label = "Plays", value = "$totalEvents", modifier = Modifier.weight(1f))
        ArtistStatBox(label = "Total Time", value = formatDuration(totalTime), modifier = Modifier.weight(1f))
        ArtistStatBox(label = "Skips", value = "$skipCount", modifier = Modifier.weight(1f))
        ArtistStatBox(label = "Skip Rate", value = "$skipRate%", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ArtistStatBox(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtistEventItem(event: ArtistListeningEvent) {
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
                text = event.songTitle,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = friendlyArtistAppName(event.sourceApp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Instant.ofEpochMilli(event.startedAt).atZone(ZoneId.systemDefault()).format(formatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatDuration(event.durationMs),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun friendlyArtistAppName(packageName: String): String {
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
