package com.musicstats.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SongDetailScreen(
    viewModel: SongDetailViewModel = hiltViewModel()
) {
    val song by viewModel.song.collectAsState()
    val totalPlayCount by viewModel.totalPlayCount.collectAsState()
    val totalListeningTime by viewModel.totalListeningTime.collectAsState()
    val skipRate by viewModel.skipRate.collectAsState()
    val history by viewModel.listeningHistory.collectAsState()

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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = currentSong.title,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = currentSong.artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!currentSong.album.isNullOrBlank()) {
                Text(
                    text = currentSong.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Stats row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatBox(
                    label = "Plays",
                    value = "$totalPlayCount",
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    label = "Total Time",
                    value = formatDuration(totalListeningTime),
                    modifier = Modifier.weight(1f)
                )
                StatBox(
                    label = "Skip Rate",
                    value = "${(skipRate * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
            }
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
                EventItem(event)
                HorizontalDivider()
            }
        }

        // Bottom padding
        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
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
private fun EventItem(event: ListeningEvent) {
    val dateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateFormat.format(Date(event.startedAt)),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = friendlyAppName(event.sourceApp),
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
