package com.musicstats.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
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
import com.musicstats.app.ui.components.HourlyHeatmap
import com.musicstats.app.ui.components.ListeningTimeChart
import com.musicstats.app.ui.components.StatCard
import com.musicstats.app.util.formatDuration

@Composable
fun TimeStatsTab(viewModel: StatsViewModel) {
    val totalTime by viewModel.totalListeningTime.collectAsState()
    val avgSession by viewModel.avgSessionDuration.collectAsState()
    val longest by viewModel.longestSession.collectAsState()
    val hourlyData by viewModel.hourlyListening.collectAsState()
    val dailyData by viewModel.dailyListening.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero: total listening time
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "Total listening time",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDuration(totalTime),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "total listening time",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Session stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Avg Session",
                value = formatDuration(avgSession),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Longest Session",
                value = formatDuration(longest),
                icon = Icons.Default.Timer,
                modifier = Modifier.weight(1f)
            )
        }

        // Hourly heatmap
        Text(
            text = "Listening by Hour",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        HourlyHeatmap(hourlyData = hourlyData)

        // Daily chart
        Text(
            text = "Daily Listening",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ListeningTimeChart(
            dailyData = dailyData,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}
