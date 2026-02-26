package com.musicstats.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.ui.components.ListeningTimeChart
import com.musicstats.app.ui.components.StatCard
import com.musicstats.app.util.formatDuration

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val todayMs by viewModel.todayListeningTimeMs.collectAsState()
    val songsToday by viewModel.songsToday.collectAsState()
    val skipsToday by viewModel.skipsToday.collectAsState()
    val topArtist by viewModel.topArtistToday.collectAsState()
    val weeklyData by viewModel.weeklyDailyListening.collectAsState()
    val topSongs by viewModel.topSongsThisWeek.collectAsState()
    val streak by viewModel.currentStreak.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero card — today's listening time
        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Listening time",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDuration(todayMs),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "listened today",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Stat cards row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Songs Today",
                value = songsToday.toString(),
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Top Artist",
                value = topArtist ?: "\u2014",
                icon = Icons.Default.Person,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Skips Today",
                value = skipsToday.toString(),
                icon = Icons.Default.SkipNext,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Streak",
                value = if (streak > 0) "${streak}d" else "\u2014",
                icon = Icons.Default.LocalFireDepartment,
                modifier = Modifier.weight(1f)
            )
        }

        // Weekly chart
        Text(
            text = "This Week",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ListeningTimeChart(
            dailyData = weeklyData,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        // Top songs this week
        Text(
            text = "Top Songs This Week",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (topSongs.isEmpty()) {
            Text(
                text = "No listening data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            topSongs.forEachIndexed { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = "${song.playCount} plays",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}
