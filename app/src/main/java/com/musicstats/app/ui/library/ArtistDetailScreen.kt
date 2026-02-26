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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.musicstats.app.data.dao.ArtistListeningEvent
import com.musicstats.app.data.dao.ArtistStats
import com.musicstats.app.ui.components.SectionHeader
import com.musicstats.app.ui.components.StatCard
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

    val totalEvents = stats?.totalEvents ?: 0
    val totalTime = stats?.totalDurationMs ?: 0L
    val skipCount = stats?.skipCount ?: 0
    val skipRate = if (totalEvents > 0) (skipCount * 100) / totalEvents else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        // Share button
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { /* wired in Task 8 */ }) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Artist image",
                        modifier = Modifier
                            .size(200.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Artist image",
                        modifier = Modifier.size(200.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = viewModel.artistName,
                    style = MaterialTheme.typography.headlineMedium
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Stats row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard(
                    label = "Plays",
                    value = "$totalEvents",
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Total Time",
                    value = formatDuration(totalTime),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Skips",
                    value = "$skipCount",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Skip Rate",
                    value = "$skipRate%",
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(history, key = { it.id }) { event ->
                val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mini thumbnail fallback
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.songTitle,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = friendlyArtistAppName(event.sourceApp),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text(
                                text = Instant.ofEpochMilli(event.startedAt)
                                    .atZone(ZoneId.systemDefault())
                                    .format(formatter),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
