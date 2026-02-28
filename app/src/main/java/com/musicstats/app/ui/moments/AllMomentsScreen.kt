package com.musicstats.app.ui.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.data.model.Moment
import com.musicstats.app.ui.components.AppPillTabs
import com.musicstats.app.ui.components.AuroraBackground
import com.musicstats.app.ui.components.MomentShareCard
import com.musicstats.app.ui.share.ShareCardRenderer
import com.musicstats.app.ui.theme.MusicStatsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AllMomentsScreen(
    viewModel: AllMomentsViewModel = hiltViewModel()
) {
    val moments by viewModel.moments.collectAsState()
    val context = LocalContext.current
    var selectedMoment by remember { mutableStateOf<Moment?>(null) }
    var showMomentDetail by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val displayMoments = if (selectedTab == 1) previewMoments else moments

    AuroraBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Moments",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )

            AppPillTabs(
                tabs = listOf("All", "Preview"),
                selectedIndex = selectedTab,
                onTabSelected = { selectedTab = it },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (displayMoments.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No moments yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(displayMoments, key = { it.id }) { moment ->
                        MomentListCard(
                            moment = moment,
                            onTap = {
                                if (selectedTab == 0) viewModel.markSeen(moment.id)
                                selectedMoment = moment
                                showMomentDetail = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showMomentDetail) {
        selectedMoment?.let { moment ->
            MomentDetailBottomSheet(
                moment = moment,
                onShare = {
                    val density = context.resources.displayMetrics.density
                    val w = (360 * density).toInt()
                    val h = (640 * density).toInt()
                    ShareCardRenderer.renderComposable(context, w, h, {
                        MusicStatsTheme {
                            MomentShareCard(moment = moment)
                        }
                    }) { bitmap ->
                        ShareCardRenderer.shareBitmap(context, bitmap)
                        viewModel.markShared(moment.id)
                    }
                    showMomentDetail = false
                    selectedMoment = null
                },
                onDismiss = {
                    showMomentDetail = false
                    selectedMoment = null
                }
            )
        }
    }
}

@Composable
private fun MomentListCard(
    moment: Moment,
    onTap: () -> Unit
) {
    val isUnseen = moment.seenAt == null
    val isArchetype = moment.type.startsWith("ARCHETYPE_")
    val dateStr = remember(moment.triggeredAt) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(moment.triggeredAt))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
            .clickable(onClick = onTap)
            .then(
                if (isUnseen) Modifier.border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val imageShape = if (moment.artistId != null) CircleShape else RoundedCornerShape(6.dp)
            if (moment.imageUrl != null) {
                AsyncImage(
                    model = moment.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(imageShape)
                )
            } else if (isArchetype) {
                Text(archetypeEmoji(moment.type), fontSize = 28.sp)
            } else {
                Text("🎵", fontSize = 28.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = moment.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = moment.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
            if (isUnseen) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

private fun archetypeEmoji(type: String): String = when {
    type.contains("NIGHT_OWL") -> "🌙"
    type.contains("MORNING") -> "☀️"
    type.contains("COMMUTE") -> "🎧"
    type.contains("COMPLETIONIST") -> "✅"
    type.contains("SKIPPER") -> "⏭️"
    type.contains("DEEP_CUT") -> "💿"
    type.contains("LOYAL_FAN") -> "❤️"
    type.contains("EXPLORER") -> "🧭"
    else -> "🎵"
}

private val previewMoments: List<Moment> = listOf(
    // Archetypes
    Moment(id = -1, type = "ARCHETYPE_NIGHT_OWL", entityKey = "preview", triggeredAt = 0,
        title = "Night Owl", description = "You do most of your listening after 10pm",
        statLine = "73% of your listening"),
    Moment(id = -2, type = "ARCHETYPE_MORNING_LISTENER", entityKey = "preview", triggeredAt = 0,
        title = "Morning Listener", description = "You do most of your listening before 9am",
        statLine = "61% of your listening"),
    Moment(id = -3, type = "ARCHETYPE_COMMUTE_LISTENER", entityKey = "preview", triggeredAt = 0,
        title = "Commute Listener", description = "Your listening peaks at 7–9am and 5–7pm",
        statLine = "34% during commute hours"),
    Moment(id = -4, type = "ARCHETYPE_COMPLETIONIST", entityKey = "preview", triggeredAt = 0,
        title = "Completionist", description = "You skip less than 5% of songs — truly dedicated",
        statLine = "2.3% skip rate"),
    Moment(id = -5, type = "ARCHETYPE_CERTIFIED_SKIPPER", entityKey = "preview", triggeredAt = 0,
        title = "Certified Skipper", description = "You skip more than 40% of songs. Nothing is good enough.",
        statLine = "47.8% skip rate"),
    Moment(id = -6, type = "ARCHETYPE_DEEP_CUT_DIGGER", entityKey = "preview", triggeredAt = 0,
        title = "Deep Cut Digger", description = "You've listened to Blinding Lights over 50 times",
        statLine = "87 plays"),
    Moment(id = -7, type = "ARCHETYPE_LOYAL_FAN", entityKey = "preview", triggeredAt = 0,
        title = "Loyal Fan", description = "Over 50% of your listening is The Weeknd",
        statLine = "62% of your listening"),
    Moment(id = -8, type = "ARCHETYPE_EXPLORER", entityKey = "preview", triggeredAt = 0,
        title = "Explorer", description = "You discovered 7 new artists this week"),
    // Song milestones
    Moment(id = -9, type = "SONG_PLAYS_100", entityKey = "preview", triggeredAt = 0,
        title = "100 plays", description = "You've played Blinding Lights 100 times",
        statLine = "8h 24m total"),
    // Artist milestones
    Moment(id = -10, type = "ARTIST_HOURS_10", entityKey = "preview", triggeredAt = 0,
        title = "10 hours of The Weeknd", description = "You've spent 10 hours listening to The Weeknd",
        statLine = "284 total plays"),
    // Streak
    Moment(id = -11, type = "STREAK_7", entityKey = "preview", triggeredAt = 0,
        title = "7-day streak", description = "7 days in a row — you're on fire"),
    // Total hours
    Moment(id = -12, type = "TOTAL_HOURS_100", entityKey = "preview", triggeredAt = 0,
        title = "100 hours", description = "You've listened to 100h of music in total"),
    // Discovery
    Moment(id = -13, type = "SONGS_DISCOVERED_100", entityKey = "preview", triggeredAt = 0,
        title = "100 songs", description = "You've discovered 100 unique songs"),
    // Behavioral
    Moment(id = -14, type = "OBSESSION_DAILY", entityKey = "preview", triggeredAt = 0,
        title = "8x in one day", description = "You played Blinding Lights 8 times today. Are you okay?"),
    Moment(id = -15, type = "DAILY_RITUAL", entityKey = "preview", triggeredAt = 0,
        title = "Daily ritual", description = "You've listened to Starboy every day for 7 days"),
    Moment(id = -16, type = "BREAKUP_CANDIDATE", entityKey = "preview", triggeredAt = 0,
        title = "Maybe break up?", description = "You've skipped Drake 12 times this week"),
    Moment(id = -17, type = "FAST_OBSESSION", entityKey = "preview", triggeredAt = 0,
        title = "31 plays in 12 days", description = "Blinding Lights came into your life 12 days ago. You've played it 31 times."),
    Moment(id = -18, type = "LONGEST_SESSION", entityKey = "preview", triggeredAt = 0,
        title = "New record: 3h 47m", description = "New personal best: 3h 47m in one sitting"),
    Moment(id = -19, type = "QUICK_OBSESSION", entityKey = "preview", triggeredAt = 0,
        title = "Fast obsession", description = "You discovered Starboy 5 days ago. It's already in your top 5."),
    Moment(id = -20, type = "DISCOVERY_WEEK", entityKey = "preview", triggeredAt = 0,
        title = "12 new artists", description = "You discovered 12 new artists this week"),
)
