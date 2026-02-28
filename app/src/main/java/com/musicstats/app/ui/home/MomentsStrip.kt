package com.musicstats.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.musicstats.app.data.model.Moment

@Composable
fun MomentsStrip(
    moments: List<Moment>,
    onMomentTap: (Moment) -> Unit,
    onSeeAll: () -> Unit
) {
    if (moments.isEmpty()) return

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Moments",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSeeAll) {
                Text("See all", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(moments.take(5), key = { it.id }) { moment ->
                MomentFeedCard(
                    moment = moment,
                    onTap = { onMomentTap(moment) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MomentFeedCard(
    moment: Moment,
    onTap: () -> Unit
) {
    val isUnseen = moment.seenAt == null
    val isArchetype = moment.type.startsWith("ARCHETYPE_")

    Box(
        modifier = Modifier
            .width(180.dp)
            .height(100.dp)
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
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (moment.artistId != null && moment.imageUrl != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AsyncImage(
                            model = moment.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                        )
                        if (moment.entityName != null) {
                            Text(
                                text = moment.entityName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else if (moment.imageUrl != null) {
                    AsyncImage(
                        model = moment.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else if (isArchetype) {
                    Text(archetypeEmojiSmall(moment.type), fontSize = 20.sp)
                } else {
                    Spacer(Modifier.size(20.dp))
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
            Column {
                Text(
                    text = moment.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = moment.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun archetypeEmojiSmall(type: String): String = when {
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
