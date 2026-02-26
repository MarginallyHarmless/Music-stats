package com.musicstats.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musicstats.app.data.model.ListeningEvent
import kotlinx.coroutines.flow.Flow

data class SongWithStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val totalDurationMs: Long,
    val playCount: Int,
    val firstHeardAt: Long
)

data class SongPlayStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val totalDurationMs: Long,
    val playCount: Int
)

data class ArtistPlayStats(
    val artist: String,
    val totalDurationMs: Long,
    val playCount: Int
)

data class HourlyListening(
    val hour: Int,
    val totalDurationMs: Long,
    val eventCount: Int
)

data class DailyListening(
    val day: String,
    val totalDurationMs: Long,
    val eventCount: Int
)

@Dao
interface ListeningEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ListeningEvent): Long

    @Query(
        """
        SELECT * FROM listening_events
        WHERE songId = :songId AND startedAt = :startedAt
        LIMIT 1
        """
    )
    suspend fun findBySongAndTime(songId: Long, startedAt: Long): ListeningEvent?

    // --- Aggregate listening time ---

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events")
    fun getTotalListeningTimeMs(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(durationMs), 0) FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        """
    )
    suspend fun getListeningTimeSince(since: Long): Long

    // --- Top songs ---

    @Query(
        """
        SELECT le.songId, s.title, s.artist,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(le.id) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE le.startedAt >= :since OR :since = 0
        GROUP BY le.songId
        ORDER BY totalDurationMs DESC
        LIMIT :limit
        """
    )
    fun getTopSongsByDuration(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>

    @Query(
        """
        SELECT le.songId, s.title, s.artist,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(le.id) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE le.startedAt >= :since OR :since = 0
        GROUP BY le.songId
        ORDER BY playCount DESC
        LIMIT :limit
        """
    )
    fun getTopSongsByPlayCount(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>

    // --- Top artists ---

    @Query(
        """
        SELECT s.artist,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(le.id) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE le.startedAt >= :since OR :since = 0
        GROUP BY s.artist
        ORDER BY totalDurationMs DESC
        LIMIT :limit
        """
    )
    fun getTopArtistsByDuration(since: Long = 0, limit: Int = 10): Flow<List<ArtistPlayStats>>

    // --- Time-of-day / daily patterns ---

    @Query(
        """
        SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
               COALESCE(SUM(durationMs), 0) AS totalDurationMs,
               COUNT(id) AS eventCount
        FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        GROUP BY hour
        ORDER BY hour ASC
        """
    )
    fun getHourlyListening(since: Long = 0): Flow<List<HourlyListening>>

    @Query(
        """
        SELECT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(durationMs), 0) AS totalDurationMs,
               COUNT(id) AS eventCount
        FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        GROUP BY day
        ORDER BY day ASC
        """
    )
    fun getDailyListening(since: Long = 0): Flow<List<DailyListening>>

    // --- Counts & discovery ---

    @Query(
        """
        SELECT COUNT(DISTINCT songId) FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        """
    )
    suspend fun getSongCountSince(since: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM songs
        WHERE firstHeardAt >= :since
        """
    )
    suspend fun getNewSongsDiscoveredSince(since: Long): Int

    @Query(
        """
        SELECT COUNT(*) FROM artists
        WHERE firstHeardAt >= :since
        """
    )
    suspend fun getNewArtistsDiscoveredSince(since: Long): Int

    // --- Session stats ---

    @Query(
        """
        SELECT COALESCE(AVG(durationMs), 0) FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        """
    )
    suspend fun getAverageSessionDuration(since: Long = 0): Long

    @Query(
        """
        SELECT COALESCE(MAX(durationMs), 0) FROM listening_events
        WHERE startedAt >= :since OR :since = 0
        """
    )
    suspend fun getLongestSession(since: Long = 0): Long

    // --- Per-song stats ---

    @Query(
        """
        SELECT CASE
            WHEN COUNT(*) = 0 THEN 0.0
            ELSE CAST(SUM(CASE WHEN completed = 0 THEN 1 ELSE 0 END) AS REAL) / COUNT(*)
        END
        FROM listening_events
        WHERE songId = :songId
        """
    )
    suspend fun getSkipRate(songId: Long): Double

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

    @Query("SELECT * FROM listening_events WHERE songId = :songId ORDER BY startedAt DESC")
    fun getEventsForSong(songId: Long): Flow<List<ListeningEvent>>

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

    @Query("SELECT * FROM listening_events ORDER BY startedAt DESC")
    suspend fun getAllEventsSnapshot(): List<ListeningEvent>

    // --- Misc ---

    @Query("SELECT COUNT(DISTINCT artist) FROM songs")
    fun getUniqueArtistCount(): Flow<Int>

    @Query(
        """
        SELECT le.songId, s.title, s.artist,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(le.id) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        GROUP BY le.songId
        HAVING playCount <= :threshold
        ORDER BY playCount ASC
        """
    )
    fun getDeepCuts(threshold: Int = 2): Flow<List<SongPlayStats>>
}
