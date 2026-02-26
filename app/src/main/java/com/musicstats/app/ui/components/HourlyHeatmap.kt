package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.musicstats.app.data.dao.HourlyListening

@Composable
fun HourlyHeatmap(hourlyData: List<HourlyListening>, modifier: Modifier = Modifier) {
    val hourMap = remember(hourlyData) {
        hourlyData.associate { it.hour to it.totalDurationMs }
    }
    val maxDuration = remember(hourMap) {
        hourMap.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 4 rows of 6 hours
        for (rowStart in listOf(0, 6, 12, 18)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (hour in rowStart until rowStart + 6) {
                    val duration = hourMap[hour] ?: 0L
                    val intensity = (duration.toFloat() / maxDuration).coerceIn(0f, 1f)
                    val cellColor = primaryColor.copy(alpha = 0.08f + intensity * 0.82f)

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (duration > 0) cellColor else primaryColor.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = String.format("%02d", hour),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (intensity > 0.5f) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
