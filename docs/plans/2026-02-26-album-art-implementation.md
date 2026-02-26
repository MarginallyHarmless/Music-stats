# Album Art + Artist Photos Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add album art to all songs (captured from media sessions + Deezer fallback) and backfill missing artist photos.

**Architecture:** 3-tier album art sourcing: (1) URI from Android MediaMetadata, (2) bitmap saved to internal storage, (3) Deezer API fallback. Artist photo backfill runs on app startup for artists with missing images. All images displayed via Coil 3 AsyncImage.

**Tech Stack:** Kotlin, Room, Coil 3, Deezer API (free, no key), Jetpack Compose

---

### Task 1: Add INTERNET permission

The manifest is missing INTERNET permission, which means the existing `ArtistImageFetcher` Deezer calls silently fail at runtime.

**Files:**
- Modify: `app/src/main/AndroidManifest.xml:3-6`

**Step 1: Add the permission**

In `app/src/main/AndroidManifest.xml`, add after the existing permissions (line 6):

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

The full permissions block becomes:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "fix: add INTERNET permission for Deezer API calls"
```

---

### Task 2: Add `updateAlbumArtUrl` to SongDao

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/SongDao.kt:47`

**Step 1: Add the query**

Add before the closing `}` of `SongDao` (after `getTopSongsByTime` method, line 47):

```kotlin
@Query("UPDATE songs SET albumArtUrl = :url WHERE id = :songId")
suspend fun updateAlbumArtUrl(songId: Long, url: String)
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/SongDao.kt
git commit -m "feat: add updateAlbumArtUrl query to SongDao"
```

---

### Task 3: Add `albumArtUrl` to `SongWithStats` and `SongPlayStats`

The UI needs album art URLs when displaying song lists. The DAO query result classes need the field, and the SQL queries need to select it.

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt`

**Step 1: Update `SongWithStats` data class (line 10-18)**

Replace:

```kotlin
data class SongWithStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val totalDurationMs: Long,
    val playCount: Int,
    val firstHeardAt: Long
)
```

With:

```kotlin
data class SongWithStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val albumArtUrl: String?,
    val totalDurationMs: Long,
    val playCount: Int,
    val firstHeardAt: Long
)
```

**Step 2: Update `SongPlayStats` data class (line 20-26)**

Replace:

```kotlin
data class SongPlayStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val totalDurationMs: Long,
    val playCount: Int
)
```

With:

```kotlin
data class SongPlayStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
    val totalDurationMs: Long,
    val playCount: Int
)
```

**Step 3: Update `getAllSongsWithStats` SQL query (line 243-255)**

Replace the query:

```kotlin
@Query(
    """
    SELECT s.id AS songId, s.title, s.artist, s.album,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount,
           s.firstHeardAt
    FROM songs s
    LEFT JOIN listening_events le ON le.songId = s.id
    GROUP BY s.id
    ORDER BY s.title ASC
    """
)
fun getAllSongsWithStats(): Flow<List<SongWithStats>>
```

With:

```kotlin
@Query(
    """
    SELECT s.id AS songId, s.title, s.artist, s.album, s.albumArtUrl,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount,
           s.firstHeardAt
    FROM songs s
    LEFT JOIN listening_events le ON le.songId = s.id
    GROUP BY s.id
    ORDER BY s.title ASC
    """
)
fun getAllSongsWithStats(): Flow<List<SongWithStats>>
```

**Step 4: Update `getTopSongsByDuration` SQL query (line 76-89)**

Replace:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    WHERE le.startedAt >= :since
    GROUP BY le.songId
    ORDER BY totalDurationMs DESC
    LIMIT :limit
    """
)
fun getTopSongsByDuration(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>
```

With:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    WHERE le.startedAt >= :since
    GROUP BY le.songId
    ORDER BY totalDurationMs DESC
    LIMIT :limit
    """
)
fun getTopSongsByDuration(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>
```

**Step 5: Update `getTopSongsByPlayCount` SQL query (line 91-104)**

Replace:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    WHERE le.startedAt >= :since
    GROUP BY le.songId
    ORDER BY playCount DESC
    LIMIT :limit
    """
)
fun getTopSongsByPlayCount(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>
```

With:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    WHERE le.startedAt >= :since
    GROUP BY le.songId
    ORDER BY playCount DESC
    LIMIT :limit
    """
)
fun getTopSongsByPlayCount(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>
```

**Step 6: Update `getSongStats` SQL query (line 227-238)**

Replace:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    WHERE le.songId = :songId
    GROUP BY le.songId
    """
)
suspend fun getSongStats(songId: Long): SongPlayStats?
```

With:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    WHERE le.songId = :songId
    GROUP BY le.songId
    """
)
suspend fun getSongStats(songId: Long): SongPlayStats?
```

**Step 7: Update `getDeepCuts` SQL query (line 265-277)**

Replace:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    GROUP BY le.songId
    HAVING playCount >= :threshold
    ORDER BY playCount ASC
    """
)
fun getDeepCuts(threshold: Int = 50): Flow<List<SongPlayStats>>
```

With:

```kotlin
@Query(
    """
    SELECT le.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
           COUNT(le.id) AS playCount
    FROM listening_events le
    INNER JOIN songs s ON s.id = le.songId
    GROUP BY le.songId
    HAVING playCount >= :threshold
    ORDER BY playCount ASC
    """
)
fun getDeepCuts(threshold: Int = 50): Flow<List<SongPlayStats>>
```

**Step 8: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 9: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt
git commit -m "feat: add albumArtUrl to SongWithStats and SongPlayStats queries"
```

---

### Task 4: Add album art fetching to ArtistImageFetcher

Expand the existing fetcher to also look up album/track art from Deezer.

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/remote/ArtistImageFetcher.kt`

**Step 1: Add album art fetch method**

Add a new method after `fetchImageUrl`:

```kotlin
/**
 * Fetches album art URL from the Deezer API by searching for a track.
 * Returns the album cover URL, or null if not found.
 */
suspend fun fetchAlbumArtUrl(title: String, artist: String): String? = withContext(Dispatchers.IO) {
    try {
        val query = URLEncoder.encode("$artist $title", "UTF-8")
        val url = URL("https://api.deezer.com/search/track?q=$query&limit=1")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", "MusicStats/1.0")

        if (connection.responseCode != 200) return@withContext null

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val response = json.decodeFromString<DeezerTrackSearchResponse>(body)
        response.data.firstOrNull()?.album?.coverMedium
    } catch (_: Exception) {
        null
    }
}
```

**Step 2: Add the response models**

Add at the bottom of the file (after the existing private data classes):

```kotlin
@Serializable
private data class DeezerTrackSearchResponse(
    val data: List<DeezerTrack> = emptyList()
)

@Serializable
private data class DeezerTrack(
    val title: String = "",
    val album: DeezerAlbum? = null
)

@Serializable
private data class DeezerAlbum(
    @kotlinx.serialization.SerialName("cover_medium")
    val coverMedium: String? = null
)
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/remote/ArtistImageFetcher.kt
git commit -m "feat: add album art lookup via Deezer track search"
```

---

### Task 5: Extract album art from MediaMetadata + wire through to repository

This is the core change: `MediaSessionTracker` extracts album art from the media session and passes it through to `recordPlay()`, which stores it. For bitmap-only sources, save to internal storage.

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt`

**Step 1: Update MediaSessionTracker to extract album art**

Replace the entire `MediaSessionTracker.kt` with:

```kotlin
package com.musicstats.app.service

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class MediaSessionTracker @Inject constructor(
    private val repository: MusicRepository,
    @ApplicationContext private val context: Context
) {
    private var currentTitle: String? = null
    private var currentArtist: String? = null
    private var currentAlbum: String? = null
    private var currentAlbumArtUri: String? = null
    private var currentAlbumArtBitmap: Bitmap? = null
    private var currentSourceApp: String? = null
    private var playStartTime: Long? = null
    private var isPlaying: Boolean = false

    @Synchronized
    fun onMetadataChanged(metadata: MediaMetadata?, sourceApp: String, scope: CoroutineScope) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)

        if (title == null || artist == null) return

        if (title != currentTitle || artist != currentArtist) {
            saveCurrentIfPlaying(scope)
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentSourceApp = sourceApp

            // Extract album art: prefer URI, fall back to bitmap
            currentAlbumArtUri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
            currentAlbumArtBitmap = if (currentAlbumArtUri == null) {
                metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            } else null

            if (isPlaying) {
                playStartTime = System.currentTimeMillis()
            }
        }
    }

    @Synchronized
    fun onPlaybackStateChanged(state: PlaybackState?, sourceApp: String, scope: CoroutineScope) {
        val wasPlaying = isPlaying
        isPlaying = state?.state == PlaybackState.STATE_PLAYING
        currentSourceApp = sourceApp

        if (isPlaying && !wasPlaying) {
            playStartTime = System.currentTimeMillis()
        } else if (!isPlaying && wasPlaying) {
            saveCurrentIfPlaying(scope)
        } else if (isPlaying && wasPlaying && playStartTime != null) {
            val positionMs = state?.position ?: return
            if (positionMs < REWIND_POSITION_THRESHOLD_MS) {
                val elapsed = System.currentTimeMillis() - playStartTime!!
                if (elapsed >= 5_000) {
                    saveCurrentIfPlaying(scope)
                    playStartTime = System.currentTimeMillis()
                }
            }
        }
    }

    companion object {
        private const val REWIND_POSITION_THRESHOLD_MS = 3_000L
    }

    @Synchronized
    private fun saveCurrentIfPlaying(scope: CoroutineScope) {
        val title = currentTitle ?: return
        val artist = currentArtist ?: return
        val startTime = playStartTime ?: return
        val duration = System.currentTimeMillis() - startTime

        if (duration < 5_000) return

        val albumArtUrl = currentAlbumArtUri ?: saveBitmapToFile(currentAlbumArtBitmap, title, artist)

        scope.launch {
            repository.recordPlay(
                title = title,
                artist = artist,
                album = currentAlbum,
                sourceApp = currentSourceApp ?: "unknown",
                startedAt = startTime,
                durationMs = duration,
                completed = duration > 30_000,
                albumArtUrl = albumArtUrl
            )
        }
        playStartTime = null
    }

    private fun saveBitmapToFile(bitmap: Bitmap?, title: String, artist: String): String? {
        bitmap ?: return null
        return try {
            val dir = File(context.filesDir, "album_art")
            dir.mkdirs()
            val safeName = "$artist-$title".replace(Regex("[^a-zA-Z0-9-]"), "_").take(100)
            val file = File(dir, "$safeName.jpg")
            if (!file.exists()) {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            }
            file.toURI().toString()
        } catch (_: Exception) {
            null
        }
    }
}
```

**Step 2: Update `MusicRepository.recordPlay()` to accept and store album art**

In `app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt`, update `recordPlay`:

Add parameter `albumArtUrl: String? = null` to the method signature.

After the song is upserted, if the song has no `albumArtUrl` and we have one, update it. Also add Deezer fallback. Replace the full `recordPlay` method:

```kotlin
suspend fun recordPlay(
    title: String,
    artist: String,
    album: String?,
    sourceApp: String,
    startedAt: Long,
    durationMs: Long,
    completed: Boolean,
    albumArtUrl: String? = null
): ListeningEvent {
    // Upsert artist — insert is IGNORE so duplicates are silently skipped
    val existingArtist = artistDao.findByName(artist)
    if (existingArtist == null) {
        artistDao.insert(Artist(name = artist, firstHeardAt = startedAt))
        fetchArtistImage(artist)
    } else if (existingArtist.imageUrl == null) {
        fetchArtistImage(artist)
    }

    // Upsert song — insert is IGNORE so duplicates are silently skipped
    val existingSong = songDao.findByTitleAndArtist(title, artist)
    val songId = if (existingSong != null) {
        // Update album art if we have a new one and the existing song doesn't
        if (existingSong.albumArtUrl == null && albumArtUrl != null) {
            songDao.updateAlbumArtUrl(existingSong.id, albumArtUrl)
        }
        existingSong.id
    } else {
        songDao.insert(Song(
            title = title,
            artist = artist,
            album = album,
            firstHeardAt = startedAt,
            albumArtUrl = albumArtUrl
        ))
    }

    // If still no album art, try Deezer as fallback
    val finalSong = songDao.findByTitleAndArtist(title, artist)
    if (finalSong != null && finalSong.albumArtUrl == null) {
        fetchAlbumArt(finalSong.id, title, artist)
    }

    // Insert listening event
    val event = ListeningEvent(
        songId = songId,
        startedAt = startedAt,
        durationMs = durationMs,
        sourceApp = sourceApp,
        completed = completed
    )
    val eventId = eventDao.insert(event)
    return event.copy(id = eventId)
}
```

Add the `fetchAlbumArt` helper method next to the existing `fetchArtistImage`:

```kotlin
private fun fetchAlbumArt(songId: Long, title: String, artist: String) {
    scope.launch {
        val url = artistImageFetcher.fetchAlbumArtUrl(title, artist)
        if (url != null) {
            songDao.updateAlbumArtUrl(songId, url)
        }
    }
}
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt
git commit -m "feat: extract album art from media sessions with Deezer fallback"
```

---

### Task 6: Add artist photo backfill

Run a background job on repository init to fill in missing artist photos.

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/dao/ArtistDao.kt`

**Step 1: Add DAO query for artists without images**

In `ArtistDao.kt`, add:

```kotlin
@Query("SELECT * FROM artists WHERE imageUrl IS NULL")
suspend fun getArtistsWithoutImage(): List<Artist>
```

**Step 2: Add backfill method to MusicRepository**

Add after `fetchAlbumArt`:

```kotlin
fun backfillArtistImages() {
    scope.launch {
        val artists = artistDao.getArtistsWithoutImage()
        for (artist in artists) {
            val url = artistImageFetcher.fetchImageUrl(artist.name)
            if (url != null) {
                artistDao.updateImageUrl(artist.name, url)
            }
            kotlinx.coroutines.delay(500) // ~2 req/sec to be respectful
        }
    }
}
```

**Step 3: Expose in MusicRepository and trigger from HomeViewModel**

In `app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt`, add to the `init` block or as a separate init call. Add at the end of the class body (before the closing `}`):

```kotlin
init {
    repository.backfillArtistImages()
}
```

**Note:** HomeViewModel already doesn't have an `init` block. Add this one.

**Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/ArtistDao.kt app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt
git commit -m "feat: backfill missing artist photos from Deezer on startup"
```

---

### Task 7: Add album art to Home screen Top Songs list

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Step 1: Update the top songs list section**

Replace the top songs `forEachIndexed` block (the `Row` with index, title, artist, play count) with a version that includes album art.

Find this block (around line 150-175):

```kotlin
topSongs.forEachIndexed { index, song ->
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = "${song.playCount} plays",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

Replace with:

```kotlin
topSongs.forEachIndexed { index, song ->
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${index + 1}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (song.albumArtUrl != null) {
            AsyncImage(
                model = song.albumArtUrl,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Album art",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text(
            text = "${song.playCount} plays",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt
git commit -m "feat: add album art thumbnails to Home top songs list"
```

---

### Task 8: Add album art to Library screen

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/library/LibraryScreen.kt`

**Step 1: Add imports**

Add these imports to the top of `LibraryScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
```

**Step 2: Update `SongListItem` composable**

Replace the `SongListItem` function (lines 110-148):

```kotlin
@Composable
private fun SongListItem(song: SongWithStats, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
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
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${song.playCount} plays",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(song.totalDurationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/library/LibraryScreen.kt
git commit -m "feat: add album art thumbnails to Library song list"
```

---

### Task 9: Add album art to Song Detail screen

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/library/SongDetailScreen.kt`

**Step 1: Add imports**

Add to the top of `SongDetailScreen.kt`:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
```

**Step 2: Add album art to the header section**

Replace the header `item` block (lines 61-79):

```kotlin
// Header
item {
    Spacer(modifier = Modifier.height(16.dp))
    if (currentSong.albumArtUrl != null) {
        AsyncImage(
            model = currentSong.albumArtUrl,
            contentDescription = "Album art",
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
    Text(
        text = currentSong.title,
        style = MaterialTheme.typography.headlineMedium
    )
    Text(
        text = currentSong.artist,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (!currentSong.album.isNullOrBlank()) {
        Text(
            text = currentSong.album,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(16.dp))
}
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/library/SongDetailScreen.kt
git commit -m "feat: add large album art to Song Detail header"
```

---

### Task 10: Fix tests

The `MediaSessionTracker` constructor now takes a `Context` parameter, and `MusicRepository.recordPlay()` has a new parameter. Update all affected tests.

**Files:**
- Modify: `app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt`
- Modify: `app/src/test/java/com/musicstats/app/data/repository/MusicRepositoryTest.kt`

**Step 1: Update MediaSessionTrackerTest**

In `MediaSessionTrackerTest.kt`, add a mock Context and update setup:

Add import:

```kotlin
import android.content.Context
import java.io.File
```

Update setup:

```kotlin
private lateinit var context: Context

@Before
fun setup() {
    repository = mockk(relaxed = true)
    context = mockk(relaxed = true)
    every { context.filesDir } returns File(System.getProperty("java.io.tmpdir"))
    tracker = MediaSessionTracker(repository, context)
    scope = TestScope(UnconfinedTestDispatcher())
}
```

Also update `createMetadata` to include album art keys:

```kotlin
private fun createMetadata(title: String, artist: String, album: String? = null): MediaMetadata {
    val metadata = mockk<MediaMetadata>()
    every { metadata.getString(MediaMetadata.METADATA_KEY_TITLE) } returns title
    every { metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) } returns artist
    every { metadata.getString(MediaMetadata.METADATA_KEY_ALBUM) } returns album
    every { metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI) } returns null
    every { metadata.getString(MediaMetadata.METADATA_KEY_ART_URI) } returns null
    every { metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) } returns null
    every { metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) } returns null
    every { metadata.getBitmap(MediaMetadata.METADATA_KEY_ART) } returns null
    every { metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) } returns null
    return metadata
}
```

**Step 2: Update MusicRepositoryTest**

The `recordPlay` calls in `MusicRepositoryTest.kt` should still work since `albumArtUrl` has a default value of `null`. But the `coVerify` calls that match all 7 parameters need to be updated to match 8.

Find all `coVerify` lines matching `repository.recordPlay(any(), any(), any(), any(), any(), any(), any())` and add one more `any()`:

```kotlin
coVerify(exactly = 0) { repository.recordPlay(any(), any(), any(), any(), any(), any(), any(), any()) }
```

Also update the specific match in the deduplication test:

```kotlin
coVerify(exactly = 0) { repository.recordPlay(eq("Song 1"), any(), any(), any(), any(), any(), any(), any()) }
```

**Step 3: Run tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 4: Commit**

```bash
git add app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt app/src/test/java/com/musicstats/app/data/repository/MusicRepositoryTest.kt
git commit -m "fix: update tests for album art changes"
```

---

### Task 11: Final verification

**Step 1: Full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Run all tests**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass

**Step 3: Install on device**

Run: `./gradlew installDebug`
Expected: Installed on device

**Step 4: Manual verification checklist**

- [ ] Top Artist card shows artist photo (or Person icon fallback)
- [ ] Top Songs This Week list shows album art thumbnails
- [ ] Library screen shows album art next to each song
- [ ] Song Detail screen shows large album art at the top
- [ ] Fallback icons show when no art available
- [ ] App doesn't crash on fresh install (no data)

**Step 5: Final commit (if any fixes needed)**
