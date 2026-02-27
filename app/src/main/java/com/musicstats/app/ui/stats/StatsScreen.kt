package com.musicstats.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.ui.components.AppPillTabs
import com.musicstats.app.ui.components.AuroraBackground
import com.musicstats.app.ui.components.PillChip

@Composable
fun StatsScreen(viewModel: StatsViewModel = hiltViewModel()) {
    val selectedRange by viewModel.selectedTimeRange.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    AuroraBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Stats",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        AppPillTabs(
            tabs = listOf("Listening", "Discovery", "Top Lists"),
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(Modifier.height(12.dp))

        // Time range pills
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeRange.entries.forEach { range ->
                PillChip(
                    label = range.label,
                    selected = selectedRange == range,
                    onClick = { viewModel.selectTimeRange(range) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tab content
        when (selectedTab) {
            0 -> TimeStatsTab(viewModel)
            1 -> DiscoveryStatsTab(viewModel)
            2 -> TopListsTab(viewModel)
        }
    }
    }
}
