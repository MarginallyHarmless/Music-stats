package com.musicstats.app.ui.library

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.musicstats.app.data.dao.ArtistWithStats
import com.musicstats.app.data.dao.SongWithStats
import com.musicstats.app.ui.components.AppPillTabs
import com.musicstats.app.ui.components.AuroraBackground
import com.musicstats.app.ui.components.PillChip
import com.musicstats.app.ui.theme.LocalAlbumPalette
import com.musicstats.app.util.formatDuration

@Composable
fun LibraryScreen(
    onSongClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()

    val artists by viewModel.artists.collectAsState()
    val artistSearchQuery by viewModel.artistSearchQuery.collectAsState()
    val artistSortMode by viewModel.artistSortMode.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    AuroraBackground {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        AppPillTabs(
            tabs = listOf("Songs", "Artists"),
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedTab) {
            0 -> SongsTab(
                songs = songs,
                searchQuery = searchQuery,
                sortMode = sortMode,
                onSearchChange = { viewModel.setSearchQuery(it) },
                onSortChange = { viewModel.setSortMode(it) },
                onSongClick = onSongClick
            )
            1 -> ArtistsTab(
                artists = artists,
                searchQuery = artistSearchQuery,
                sortMode = artistSortMode,
                onSearchChange = { viewModel.setArtistSearchQuery(it) },
                onSortChange = { viewModel.setArtistSortMode(it) },
                onArtistClick = onArtistClick
            )
        }
    }
    }
}

@Composable
private fun SongsTab(
    songs: List<SongWithStats>,
    searchQuery: String,
    sortMode: SortMode,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onSongClick: (Long) -> Unit
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search songs or artists") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.06f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SortMode.entries.forEach { mode ->
            PillChip(
                label = when (mode) {
                    SortMode.MostPlayed -> "Most Played"
                    SortMode.MostRecent -> "Recent"
                    SortMode.Alphabetical -> "A-Z"
                },
                selected = sortMode == mode,
                onClick = { onSortChange(mode) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (songs.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (searchQuery.isBlank()) "No songs tracked yet" else "No results",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(songs, key = { it.songId }) { song ->
                SongListItem(song = song, onClick = { onSongClick(song.songId) })
            }
        }
    }
}

@Composable
private fun ArtistsTab(
    artists: List<ArtistWithStats>,
    searchQuery: String,
    sortMode: SortMode,
    onSearchChange: (String) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onArtistClick: (String) -> Unit
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search artists") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.06f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        )
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SortMode.entries.forEach { mode ->
            PillChip(
                label = when (mode) {
                    SortMode.MostPlayed -> "Most Played"
                    SortMode.MostRecent -> "Recent"
                    SortMode.Alphabetical -> "A-Z"
                },
                selected = sortMode == mode,
                onClick = { onSortChange(mode) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (artists.isEmpty()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (searchQuery.isBlank()) "No artists tracked yet" else "No results",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(artists, key = { it.name }) { artist ->
                ArtistListItem(artist = artist, onClick = { onArtistClick(artist.name) })
            }
        }
    }
}

@Composable
private fun SongListItem(song: SongWithStats, onClick: () -> Unit) {
    val palette = LocalAlbumPalette.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.glassBorder, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = palette.glassBackground
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (song.albumArtUrl != null) {
                AsyncImage(
                    model = song.albumArtUrl,
                    contentDescription = "Album art",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
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
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${song.playCount} plays",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = formatDuration(song.totalDurationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun ArtistListItem(artist: ArtistWithStats, onClick: () -> Unit) {
    val palette = LocalAlbumPalette.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, palette.glassBorder, MaterialTheme.shapes.medium),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = palette.glassBackground
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (artist.imageUrl != null) {
                AsyncImage(
                    model = artist.imageUrl,
                    contentDescription = "Artist image",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Artist image",
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.5f)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${artist.playCount} plays",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = formatDuration(artist.totalDurationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}
