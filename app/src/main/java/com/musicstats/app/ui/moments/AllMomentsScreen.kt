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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.data.model.Moment
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
    var showShareSheet by remember { mutableStateOf(false) }

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

            if (moments.isEmpty()) {
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
                    items(moments, key = { it.id }) { moment ->
                        MomentListCard(
                            moment = moment,
                            onTap = {
                                viewModel.markSeen(moment.id)
                                selectedMoment = moment
                                showShareSheet = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showShareSheet) {
        selectedMoment?.let { moment ->
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
            showShareSheet = false
            selectedMoment = null
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
            if (isArchetype) {
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
