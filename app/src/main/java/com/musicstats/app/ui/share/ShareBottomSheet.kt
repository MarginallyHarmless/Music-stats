package com.musicstats.app.ui.share

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    onDismiss: () -> Unit,
    onDailyRecap: () -> Unit,
    showSongSpotlight: Boolean = false,
    onSongSpotlight: () -> Unit = {},
    showArtistSpotlight: Boolean = false,
    onArtistSpotlight: () -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Share",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            ShareOption(icon = Icons.Default.CalendarToday, title = "Daily Recap", subtitle = "Today's listening summary", onClick = { onDailyRecap(); onDismiss() })
            if (showSongSpotlight) {
                ShareOption(icon = Icons.Default.MusicNote, title = "Song Spotlight", subtitle = "Share this song", onClick = { onSongSpotlight(); onDismiss() })
            }
            if (showArtistSpotlight) {
                ShareOption(icon = Icons.Default.Person, title = "Artist Spotlight", subtitle = "Share this artist", onClick = { onArtistSpotlight(); onDismiss() })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(imageVector = icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
