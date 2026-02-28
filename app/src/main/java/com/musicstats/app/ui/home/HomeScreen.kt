package com.musicstats.app.ui.home

import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.musicstats.app.service.MusicNotificationListener
import com.musicstats.app.ui.components.AuroraBackground
import com.musicstats.app.ui.components.GradientCard
import com.musicstats.app.ui.components.ListeningTimeChart
import com.musicstats.app.ui.components.SectionHeader
import com.musicstats.app.ui.components.StatCard
import com.musicstats.app.ui.share.DailyRecapCard
import com.musicstats.app.ui.share.ShareBottomSheet
import com.musicstats.app.ui.share.ShareCardRenderer
import com.musicstats.app.ui.theme.LocalAlbumPalette
import com.musicstats.app.ui.theme.MusicStatsTheme
import com.musicstats.app.util.formatDuration

@Composable
fun HomeScreen(
    onSongClick: (Long) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val todayMs by viewModel.todayListeningTimeMs.collectAsState()
    val yesterdayMs by viewModel.yesterdayListeningTimeMs.collectAsState()
    val isPlaying by viewModel.mediaSessionTracker.isPlayingFlow.collectAsState()
    val sessionStartMs by viewModel.mediaSessionTracker.currentSessionStartMs.collectAsState()
    val songsToday by viewModel.songsToday.collectAsState()
    val skipsToday by viewModel.skipsToday.collectAsState()
    val topArtistInfo by viewModel.topArtistToday.collectAsState()
    val weeklyData by viewModel.weeklyDailyListening.collectAsState()
    val topSongs by viewModel.topSongsThisWeek.collectAsState()
    val streak by viewModel.currentStreak.collectAsState()
    val recentMoments by viewModel.recentMoments.collectAsState()

    // Live ticker: adds current session elapsed time to DB total
    var liveElapsed by remember { mutableLongStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(isPlaying, sessionStartMs) {
        if (isPlaying && sessionStartMs > 0) {
            while (true) {
                liveElapsed = System.currentTimeMillis() - sessionStartMs
                kotlinx.coroutines.delay(1000)
            }
        } else {
            liveElapsed = 0L
        }
    }
    val displayMs = todayMs + liveElapsed

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var listenerEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    var showShareSheet by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            listenerEnabled = isNotificationListenerEnabled(context)
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.detectMomentsOnOpen()
    }

    AuroraBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(0.dp))

        // 1. Greeting header
        Column {
            Text(
                text = viewModel.greeting,
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                text = viewModel.todayFormatted.uppercase(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 1.sp
                ),
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        // 2. Notification warning banner
        if (!listenerEnabled) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tracking paused",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Notification access is disabled. Tap to re-enable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    FilledTonalButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) {
                        Text("Fix")
                    }
                }
            }
        }

        // 3. Hero gradient card + stat pills (grouped with 8dp spacing)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientCard {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = formatDuration(displayMs, showSeconds = true),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(0.3f)
                            .height(1.dp)
                            .background(LocalAlbumPalette.current.accent.copy(alpha = 0.4f))
                    )
                    Text(
                        text = "LISTENED TODAY",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // vs yesterday comparison
                    if (displayMs > 0 || yesterdayMs > 0) {
                        Spacer(Modifier.height(6.dp))
                        val vsText = when {
                            yesterdayMs == 0L && displayMs > 0 -> "First session today!"
                            displayMs == 0L -> ""
                            else -> {
                                val pct = ((displayMs - yesterdayMs) * 100 / yesterdayMs).toInt()
                                when {
                                    pct > 0 -> "\u2191 $pct% vs yesterday"
                                    pct < 0 -> "\u2193 ${-pct}% vs yesterday"
                                    else -> "Same as yesterday"
                                }
                            }
                        }
                        if (vsText.isNotEmpty()) {
                            val vsColor = when {
                                yesterdayMs == 0L -> Color(0xFF4DD0C8)
                                displayMs >= yesterdayMs -> Color(0xFF4DD0C8)
                                else -> Color.White.copy(alpha = 0.5f)
                            }
                            Text(
                                text = vsText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 0.5.sp
                                ),
                                color = vsColor
                            )
                        }
                    }
                }
            }

            // 4. Today's stat pills row
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
        }

        // 5. Top artist spotlight
        if (topArtistInfo != null) {
            Card(
                onClick = { topArtistInfo?.name?.let { onArtistClick(it) } },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box {
                    if (topArtistInfo?.imageUrl != null) {
                        AsyncImage(
                            model = topArtistInfo?.imageUrl,
                            contentDescription = "Artist photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF0A0A0F).copy(alpha = 0.85f)
                                        )
                                    )
                                )
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        Text(
                            text = topArtistInfo?.name ?: "",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Top artist today",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(50)
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = formatDuration(topArtistInfo?.totalDurationMs ?: 0L),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        Color.White.copy(alpha = 0.10f),
                                        RoundedCornerShape(50)
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${topArtistInfo?.playCount} plays",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Card(
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = LocalAlbumPalette.current.glassBackground
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No listening data yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // 6. Weekly chart section
        SectionHeader("This Week")
        ListeningTimeChart(
            dailyData = weeklyData,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        // 7. Top songs this week
        SectionHeader("Top Songs This Week")

        if (topSongs.isEmpty()) {
            Text(
                text = "No listening data yet",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.5f)
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                topSongs.forEachIndexed { index, song ->
                    val palette = LocalAlbumPalette.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, palette.glassBorder, MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = palette.glassBackground)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongClick(song.songId) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (song.albumArtUrl != null) {
                                AsyncImage(
                                    model = song.albumArtUrl,
                                    contentDescription = "Album art",
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = "Album art",
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.White.copy(alpha = 0.5f)
                                )
                            }
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
                                    color = Color.White.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatDuration(song.totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.6f),
                                    textAlign = TextAlign.End
                                )
                                Text(
                                    text = "${song.playCount} plays",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }

        // 8. Share FAB
        FloatingActionButton(
            onClick = { showShareSheet = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = "Share"
            )
        }

        if (showShareSheet) {
            ShareBottomSheet(
                onDismiss = { showShareSheet = false },
                onDailyRecap = {
                    val density = context.resources.displayMetrics.density
                    val w = (360 * density).toInt()
                    val h = (640 * density).toInt()
                    ShareCardRenderer.renderComposable(context, w, h, {
                        MusicStatsTheme {
                            DailyRecapCard(
                                listeningTimeMs = displayMs,
                                topArtistName = topArtistInfo?.name,
                                topArtistImageUrl = topArtistInfo?.imageUrl,
                                topSongs = topSongs.take(3).map { it.title to it.artist }
                            )
                        }
                    }) { bitmap ->
                        ShareCardRenderer.shareBitmap(context, bitmap)
                    }
                }
            )
        }

        // 9. Bottom spacer
        Spacer(Modifier.height(16.dp))
    }
    }
}

private fun isNotificationListenerEnabled(context: android.content.Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    val componentName = ComponentName(context, MusicNotificationListener::class.java).flattenToString()
    return flat.split(":").any { it == componentName }
}
