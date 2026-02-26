package com.musicstats.app.data.repository

import com.musicstats.app.data.dao.ArtistPlayStats
import com.musicstats.app.data.dao.DailyListening
import com.musicstats.app.data.dao.HourlyListening
import com.musicstats.app.data.dao.SongPlayStats
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val eventDao: ListeningEventDao
) {
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
        completed: Boolean
    ): ListeningEvent {
        // Upsert artist — insert is IGNORE so duplicates are silently skipped
        val existingArtist = artistDao.findByName(artist)
        if (existingArtist == null) {
            artistDao.insert(Artist(name = artist, firstHeardAt = startedAt))
        }

        // Upsert song — insert is IGNORE so duplicates are silently skipped
        val existingSong = songDao.findByTitleAndArtist(title, artist)
        val songId = if (existingSong != null) {
            existingSong.id
        } else {
            songDao.insert(Song(title = title, artist = artist, album = album, firstHeardAt = startedAt))
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

    suspend fun getListeningTimeSince(since: Long): Long = eventDao.getListeningTimeSince(since)

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

    suspend fun getNewSongsDiscoveredSince(since: Long): Int =
        eventDao.getNewSongsDiscoveredSince(since)

    suspend fun getNewArtistsDiscoveredSince(since: Long): Int =
        eventDao.getNewArtistsDiscoveredSince(since)

    // --- Session stats ---

    suspend fun getAverageSessionDuration(since: Long = 0): Long =
        eventDao.getAverageSessionDuration(since)

    suspend fun getLongestSession(since: Long = 0): Long =
        eventDao.getLongestSession(since)

    // --- Per-song stats ---

    fun getDeepCuts(threshold: Int = 2): Flow<List<SongPlayStats>> =
        eventDao.getDeepCuts(threshold)

    fun getEventsForSong(songId: Long): Flow<List<ListeningEvent>> =
        eventDao.getEventsForSong(songId)

    suspend fun getSkipRate(songId: Long): Double = eventDao.getSkipRate(songId)

    suspend fun getSongStats(songId: Long): SongPlayStats? = eventDao.getSongStats(songId)

    // --- Song & artist queries ---

    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs()

    fun getTotalSongCount(): Flow<Int> = songDao.getTotalSongCount()

    fun getUniqueArtistCount(): Flow<Int> = eventDao.getUniqueArtistCount()

    fun getAllArtists(): Flow<List<Artist>> = artistDao.getAllArtists()

    fun getTotalArtistCount(): Flow<Int> = artistDao.getTotalArtistCount()
}
