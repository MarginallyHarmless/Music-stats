package com.musicstats.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicstats.app.ui.components.GradientCard
import com.musicstats.app.ui.components.HourlyHeatmap
import com.musicstats.app.ui.components.ListeningTimeChart
import com.musicstats.app.ui.components.SectionHeader
import com.musicstats.app.ui.components.StatCard
import com.musicstats.app.util.formatDuration

@Composable
fun TimeStatsTab(viewModel: StatsViewModel) {
    val totalTime by viewModel.totalListeningTime.collectAsState()
    val totalPlays by viewModel.totalPlayCount.collectAsState()
    val totalSkips by viewModel.totalSkips.collectAsState()
    val avgSession by viewModel.avgSessionDuration.collectAsState()
    val longest by viewModel.longestSession.collectAsState()
    val hourlyData by viewModel.hourlyListening.collectAsState()
    val dailyData by viewModel.dailyListening.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Hero: total listening time + total plays
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GradientCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatDuration(totalTime),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "listening time",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
            GradientCard(modifier = Modifier.weight(1f)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$totalPlays",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "total plays",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
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
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Longest",
                value = formatDuration(longest),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Skips",
                value = "$totalSkips",
                modifier = Modifier.weight(1f)
            )
        }

        // Hourly heatmap
        SectionHeader("Listening by Hour")
        HourlyHeatmap(hourlyData = hourlyData)

        // Daily chart
        SectionHeader("Daily Listening")
        ListeningTimeChart(
            dailyData = dailyData,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}
