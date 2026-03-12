package com.musicstats.app.ui.challenges

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.data.model.Challenge
import com.musicstats.app.ui.components.ChallengeCard
import com.musicstats.app.ui.home.ChallengeDetailSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeHistoryScreen(
    onBack: () -> Unit,
    viewModel: ChallengeHistoryViewModel = hiltViewModel()
) {
    val allChallenges by viewModel.allChallenges.collectAsState(initial = emptyList())
    var selectedChallenge by remember { mutableStateOf<Challenge?>(null) }

    val grouped = allChallenges.groupBy { it.weekStart }
        .toSortedMap(compareByDescending { it })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Challenge History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            grouped.forEach { (weekStart, challenges) ->
                item {
                    Text(
                        text = formatWeekLabel(weekStart),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(challenges, key = { it.id }) { challenge ->
                    ChallengeCard(
                        challenge = challenge,
                        onTap = { selectedChallenge = challenge }
                    )
                }
            }
        }
    }

    selectedChallenge?.let { challenge ->
        ChallengeDetailSheet(
            challenge = challenge,
            onDismiss = { selectedChallenge = null }
        )
    }
}

private fun formatWeekLabel(weekStartMs: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(weekStartMs).atZone(zone).toLocalDate()
    val end = start.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    return "${start.format(fmt)} – ${end.format(fmt)}"
}
