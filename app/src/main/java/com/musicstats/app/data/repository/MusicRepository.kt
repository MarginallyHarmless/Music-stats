package com.musicstats.app.data.repository

import android.util.Log
import com.musicstats.app.data.dao.ArtistListeningEvent
import com.musicstats.app.data.dao.ArtistPlayStats
import com.musicstats.app.data.dao.ArtistStats
import com.musicstats.app.data.dao.ArtistWithStats
import com.musicstats.app.data.dao.DailyListening
import com.musicstats.app.data.dao.HourlyListening
import com.musicstats.app.data.dao.SongPlayStats
import com.musicstats.app.data.dao.SongWithStats
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song
import com.musicstats.app.data.remote.ArtistImageFetcher
import com.musicstats.app.util.PaletteExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val eventDao: ListeningEventDao,
    private val artistImageFetcher: ArtistImageFetcher,
    private val paletteExtractor: PaletteExtractor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * Record a song play. Handles deduplication of songs and artists.
     */
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
            if (existingSong.albumArtUrl == null && albumArtUrl != null) {
                songDao.updateAlbumArtUrl(existingSong.id, albumArtUrl)
                extractAndSavePalette(existingSong.id, albumArtUrl)
            }
            existingSong.id
        } else {
            val newId = songDao.insert(Song(
                title = title,
                artist = artist,
                album = album,
                firstHeardAt = startedAt,
                albumArtUrl = albumArtUrl
            ))
            if (albumArtUrl != null) {
                extractAndSavePalette(newId, albumArtUrl)
            }
            newId
        }

        // If no album art from media session, try Deezer as fallback
        if (albumArtUrl == null && (existingSong == null || existingSong.albumArtUrl == null)) {
            fetchAlbumArt(songId, title, artist)
        }

        // Dedup: skip if a recent event for the same song already exists
        val existing = eventDao.findBySongNearTime(songId, startedAt)
        if (existing != null) {
            Log.d("MusicRepository", "Skipping duplicate event for songId=$songId near startedAt=$startedAt")
            return existing
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

    // --- Aggregate listening time ---

    fun getTotalListeningTime(): Flow<Long> = eventDao.getTotalListeningTimeMs()

    fun getListeningTimeSince(since: Long): Flow<Long> = eventDao.getListeningTimeSince(since)

    fun getListeningTimeBetween(from: Long, until: Long): Flow<Long> = eventDao.getListeningTimeBetween(from, until)

    fun getTotalPlayCount(since: Long): Flow<Int> = eventDao.getTotalPlayCount(since)

    fun getSongCountSince(since: Long): Flow<Int> = eventDao.getSongCountSince(since)

    // --- Top songs ---

    fun getTopSongsByDuration(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>> =
        eventDao.getTopSongsByDuration(since, limit)

    fun getTopSongsByPlayCount(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>> =
        eventDao.getTopSongsByPlayCount(since, limit)

    // --- Top artists ---

    fun getTopArtistsByDuration(since: Long = 0, limit: Int = 10): Flow<List<ArtistPlayStats>> =
        eventDao.getTopArtistsByDuration(since, limit)

    // --- Time-of-day / daily patterns ---

    fun getHourlyListening(since: Long = 0): Flow<List<HourlyListening>> =
        eventDao.getHourlyListening(since)

    fun getDailyListening(since: Long = 0): Flow<List<DailyListening>> =
        eventDao.getDailyListening(since)

    // --- Counts & discovery ---

    fun getNewSongsDiscoveredSince(since: Long): Flow<Int> =
        eventDao.getNewSongsDiscoveredSince(since)

    fun getNewArtistsDiscoveredSince(since: Long): Flow<Int> =
        eventDao.getNewArtistsDiscoveredSince(since)

    // --- Skip counts ---

    fun getSkipCountSince(since: Long): Flow<Int> = eventDao.getSkipCountSince(since)

    fun getSkipCountForSong(songId: Long): Flow<Int> = eventDao.getSkipCountForSong(songId)

    // --- Session stats ---

    fun getAverageSessionDuration(since: Long = 0): Flow<Long> =
        eventDao.getAverageSessionDuration(since)

    fun getLongestSession(since: Long = 0): Flow<Long> =
        eventDao.getLongestSession(since)

    // --- Per-song stats ---

    fun getDeepCuts(threshold: Int = 50): Flow<List<SongPlayStats>> =
        eventDao.getDeepCuts(threshold)

    fun getEventsForSong(songId: Long): Flow<List<ListeningEvent>> =
        eventDao.getEventsForSong(songId)

    suspend fun getSkipRate(songId: Long): Double = eventDao.getSkipRate(songId)

    suspend fun getSongStats(songId: Long): SongPlayStats? = eventDao.getSongStats(songId)

    // --- Song & artist queries ---

    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()

    fun getAllSongsWithStats(): Flow<List<SongWithStats>> = eventDao.getAllSongsWithStats()

    fun getSongByIdFlow(id: Long): Flow<Song?> = songDao.getSongByIdFlow(id)

    fun getTotalSongCount(): Flow<Int> = songDao.getTotalSongCount()

    fun getUniqueArtistCount(): Flow<Int> = eventDao.getUniqueArtistCount()

    fun getAllArtists(): Flow<List<Artist>> = artistDao.getAllArtists()

    fun getTotalArtistCount(): Flow<Int> = artistDao.getTotalArtistCount()

    fun getArtistImageUrl(name: String): Flow<String?> = artistDao.getArtistImageUrl(name)

    fun getAllArtistsWithStats(): Flow<List<ArtistWithStats>> = eventDao.getAllArtistsWithStats()

    suspend fun getArtistStats(artistName: String): ArtistStats? = eventDao.getArtistStats(artistName)

    fun getEventsForArtist(artistName: String): Flow<List<ArtistListeningEvent>> = eventDao.getEventsForArtist(artistName)

    private fun fetchAlbumArt(songId: Long, title: String, artist: String) {
        scope.launch {
            val url = artistImageFetcher.fetchAlbumArtUrl(title, artist)
            if (url != null) {
                songDao.updateAlbumArtUrl(songId, url)
            }
        }
    }

    private fun fetchArtistImage(artistName: String) {
        scope.launch {
            val url = artistImageFetcher.fetchImageUrl(artistName)
            if (url != null) {
                artistDao.updateImageUrl(artistName, url)
            }
        }
    }

    fun backfillAlbumArt() {
        scope.launch {
            val songs = songDao.getSongsWithoutAlbumArt()
            for (song in songs) {
                val url = artistImageFetcher.fetchAlbumArtUrl(song.title, song.artist)
                if (url != null) {
                    songDao.updateAlbumArtUrl(song.id, url)
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun getMostRecentEvent(): Flow<ListeningEvent?> = eventDao.getMostRecentEvent()

    private fun extractAndSavePalette(songId: Long, url: String) {
        scope.launch {
            val palette = paletteExtractor.extractFromUrl(url) ?: return@launch
            songDao.updatePaletteColors(
                songId = songId,
                dominant = palette.dominant,
                vibrant = palette.vibrant,
                muted = palette.muted,
                darkVibrant = palette.darkVibrant,
                darkMuted = palette.darkMuted,
                lightVibrant = palette.lightVibrant
            )
        }
    }

    fun backfillPaletteColors() {
        scope.launch {
            val songs = songDao.getSongsNeedingPaletteExtraction()
            for (song in songs) {
                val url = song.albumArtUrl ?: continue
                val palette = paletteExtractor.extractFromUrl(url) ?: continue
                songDao.updatePaletteColors(
                    songId = song.id,
                    dominant = palette.dominant,
                    vibrant = palette.vibrant,
                    muted = palette.muted,
                    darkVibrant = palette.darkVibrant,
                    darkMuted = palette.darkMuted,
                    lightVibrant = palette.lightVibrant
                )
                kotlinx.coroutines.yield()
            }
        }
    }

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

    fun upgradeArtworkToHighRes() {
        scope.launch {
            val songsUpgraded = songDao.upgradeDeezerArtToXl()
            val artistsUpgraded = artistDao.upgradeDeezerImagesToXl()
            if (songsUpgraded > 0) {
                songDao.clearPalettesForUpgradedArt()
                backfillPaletteColors()
            }
        }
    }
}
