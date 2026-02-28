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
    val albumArtUrl: String?,
    val totalDurationMs: Long,
    val playCount: Int,
    val firstHeardAt: Long
)

data class SongPlayStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val albumArtUrl: String?,
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

data class ArtistWithStats(
    val name: String,
    val imageUrl: String?,
    val firstHeardAt: Long,
    val totalDurationMs: Long,
    val playCount: Int
)

data class ArtistStats(
    val totalDurationMs: Long,
    val totalEvents: Int,
    val playCount: Int,
    val skipCount: Int
)

data class ArtistListeningEvent(
    val id: Long,
    val songTitle: String,
    val startedAt: Long,
    val durationMs: Long,
    val sourceApp: String,
    val completed: Boolean
)

data class SongArtistEvent(
    val songId: Long,
    val artist: String,
    val startedAt: Long,
    val durationMs: Long
)

data class DayNightListening(
    val day: String,
    val nightMs: Long
)

data class WeeklyListening(
    val weekKey: String,
    val totalMs: Long
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

    @Query(
        """
        SELECT * FROM listening_events
        WHERE songId = :songId AND ABS(startedAt - :startedAt) < :windowMs
        LIMIT 1
        """
    )
    suspend fun findBySongNearTime(songId: Long, startedAt: Long, windowMs: Long = 5000): ListeningEvent?

    // --- Aggregate listening time ---

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events")
    fun getTotalListeningTimeMs(): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(durationMs), 0) FROM listening_events
        WHERE startedAt >= :since
        """
    )
    fun getListeningTimeSince(since: Long): Flow<Long>

    @Query(
        """
        SELECT COALESCE(SUM(durationMs), 0) FROM listening_events
        WHERE startedAt >= :from AND startedAt < :until
        """
    )
    fun getListeningTimeBetween(from: Long, until: Long): Flow<Long>

    // --- Top songs ---

    @Query(
        """
        SELECT le.songId, s.title, s.artist, s.albumArtUrl,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE le.startedAt >= :since
        GROUP BY le.songId
        ORDER BY totalDurationMs DESC
        LIMIT :limit
        """
    )
    fun getTopSongsByDuration(since: Long = 0, limit: Int = 10): Flow<List<SongPlayStats>>

    @Query(
        """
        SELECT le.songId, s.title, s.artist, s.albumArtUrl,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE le.startedAt >= :since
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
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE le.startedAt >= :since
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
        WHERE startedAt >= :since
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
        WHERE startedAt >= :since
        GROUP BY day
        ORDER BY day ASC
        """
    )
    fun getDailyListening(since: Long = 0): Flow<List<DailyListening>>

    // --- Total play count ---

    @Query("SELECT COUNT(*) FROM listening_events WHERE startedAt >= :since AND completed = 1")
    fun getTotalPlayCount(since: Long): Flow<Int>

    // --- Counts & discovery ---

    @Query(
        """
        SELECT COUNT(DISTINCT songId) FROM listening_events
        WHERE startedAt >= :since AND completed = 1
        """
    )
    fun getSongCountSince(since: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM songs
        WHERE firstHeardAt >= :since
        """
    )
    fun getNewSongsDiscoveredSince(since: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM artists
        WHERE firstHeardAt >= :since
        """
    )
    fun getNewArtistsDiscoveredSince(since: Long): Flow<Int>

    // --- Skip counts ---

    @Query(
        """
        SELECT COUNT(*) FROM listening_events
        WHERE completed = 0 AND startedAt >= :since
        """
    )
    fun getSkipCountSince(since: Long): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM listening_events
        WHERE completed = 0 AND songId = :songId
        """
    )
    fun getSkipCountForSong(songId: Long): Flow<Int>

    // --- Session stats ---

    @Query(
        """
        SELECT COALESCE(AVG(durationMs), 0) FROM listening_events
        WHERE startedAt >= :since
        """
    )
    fun getAverageSessionDuration(since: Long = 0): Flow<Long>

    @Query(
        """
        SELECT COALESCE(MAX(durationMs), 0) FROM listening_events
        WHERE startedAt >= :since
        """
    )
    fun getLongestSession(since: Long = 0): Flow<Long>

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
        SELECT le.songId, s.title, s.artist, s.albumArtUrl,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount
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
        SELECT s.id AS songId, s.title, s.artist, s.album, s.albumArtUrl,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount,
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

    // --- Artist list & detail ---

    @Query("""
        SELECT a.name, a.imageUrl, a.firstHeardAt,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount
        FROM artists a
        LEFT JOIN songs s ON s.artist = a.name
        LEFT JOIN listening_events le ON le.songId = s.id
        GROUP BY a.name
        ORDER BY a.name ASC
    """)
    fun getAllArtistsWithStats(): Flow<List<ArtistWithStats>>

    @Query("""
        SELECT COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(le.id) AS totalEvents,
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount,
               COUNT(CASE WHEN le.completed = 0 THEN 1 END) AS skipCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE s.artist = :artistName
    """)
    suspend fun getArtistStats(artistName: String): ArtistStats?

    @Query("""
        SELECT le.id, s.title AS songTitle, le.startedAt, le.durationMs, le.sourceApp, le.completed
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        WHERE s.artist = :artistName
        ORDER BY le.startedAt DESC
    """)
    fun getEventsForArtist(artistName: String): Flow<List<ArtistListeningEvent>>

    // --- Misc ---

    @Query("SELECT * FROM listening_events ORDER BY startedAt DESC LIMIT 1")
    fun getMostRecentEvent(): Flow<ListeningEvent?>

    @Query("SELECT COUNT(DISTINCT artist) FROM songs")
    fun getUniqueArtistCount(): Flow<Int>

    @Query(
        """
        SELECT le.songId, s.title, s.artist, s.albumArtUrl,
               COALESCE(SUM(le.durationMs), 0) AS totalDurationMs,
               COUNT(CASE WHEN le.completed = 1 THEN 1 END) AS playCount
        FROM listening_events le
        INNER JOIN songs s ON s.id = le.songId
        GROUP BY le.songId
        HAVING playCount >= :threshold
        ORDER BY playCount ASC
        """
    )
    fun getDeepCuts(threshold: Int = 50): Flow<List<SongPlayStats>>

    // --- Suspend queries for MomentDetector ---

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events WHERE completed = 1")
    suspend fun getTotalListeningTimeMsSuspend(): Long

    @Query("SELECT COUNT(*) FROM listening_events WHERE completed = 1")
    suspend fun getTotalPlayCountSuspend(): Long

    @Query("SELECT COUNT(*) FROM listening_events WHERE completed = 0")
    suspend fun getTotalSkipCountSuspend(): Long

    @Query("SELECT COUNT(DISTINCT songId) FROM listening_events")
    suspend fun getUniqueSongCountSuspend(): Int

    @Query("SELECT COUNT(DISTINCT s.artist) FROM listening_events e JOIN songs s ON e.songId = s.id")
    suspend fun getUniqueArtistCountSuspend(): Int

    @Query("""
        SELECT COUNT(DISTINCT s.artist) FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE e.startedAt >= :since
        AND s.firstHeardAt >= :since
    """)
    suspend fun getNewArtistsSinceSuspend(since: Long): Int

    @Query("""
        SELECT e.songId, s.title, s.artist, s.albumArtUrl,
               SUM(e.durationMs) as totalDurationMs,
               COUNT(*) as playCount
        FROM listening_events e JOIN songs s ON e.songId = s.id
        WHERE e.completed = 1
        GROUP BY e.songId
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun getTopSongsByPlayCountSuspend(limit: Int): List<SongPlayStats>

    @Query("""
        SELECT e.songId, s.title, s.artist, s.album, s.albumArtUrl, s.firstHeardAt,
               SUM(e.durationMs) as totalDurationMs,
               COUNT(*) as playCount
        FROM listening_events e JOIN songs s ON e.songId = s.id
        WHERE e.completed = 1
        GROUP BY e.songId
        HAVING playCount >= :minPlays
    """)
    suspend fun getSongsWithMinPlays(minPlays: Int): List<SongWithStats>

    @Query("""
        SELECT s.artist, SUM(e.durationMs) as totalDurationMs, COUNT(*) as playCount
        FROM listening_events e JOIN songs s ON e.songId = s.id
        WHERE e.startedAt >= :since
        GROUP BY s.artist
        ORDER BY totalDurationMs DESC
        LIMIT :limit
    """)
    suspend fun getTopArtistsByDurationSuspend(since: Long, limit: Int): List<ArtistPlayStats>

    @Query("""
        SELECT s.artist, SUM(e.durationMs) as totalDurationMs, COUNT(*) as playCount
        FROM listening_events e JOIN songs s ON e.songId = s.id
        GROUP BY s.artist
        ORDER BY totalDurationMs DESC
    """)
    suspend fun getAllArtistsWithDurationSuspend(): List<ArtistPlayStats>

    @Query("""
        SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) as hour,
               SUM(durationMs) as totalDurationMs,
               COUNT(*) as eventCount
        FROM listening_events
        WHERE startedAt >= :since AND completed = 1
        GROUP BY hour
    """)
    suspend fun getHourlyListeningSuspend(since: Long): List<HourlyListening>

    @Query("SELECT COALESCE(MAX(durationMs), 0) FROM listening_events WHERE completed = 1")
    suspend fun getLongestSessionMs(): Long

    @Query("""
        SELECT COUNT(*) FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE s.artist = :artist AND e.completed = 0 AND e.startedAt >= :since
    """)
    suspend fun getArtistSkipCountSince(artist: String, since: Long): Int

    @Query("""
        SELECT e.songId, s.title, s.artist, s.albumArtUrl,
               SUM(e.durationMs) as totalDurationMs,
               COUNT(*) as playCount
        FROM listening_events e JOIN songs s ON e.songId = s.id
        WHERE e.startedAt >= :dayStart AND e.startedAt < :dayEnd AND e.completed = 1
        GROUP BY e.songId
        ORDER BY playCount DESC
    """)
    suspend fun getSongsPlayedOnDay(dayStart: Long, dayEnd: Long): List<SongPlayStats>

    @Query("""
        SELECT DISTINCT date(startedAt / 1000, 'unixepoch', 'localtime') as day
        FROM listening_events
        WHERE songId = :songId AND completed = 1 AND startedAt >= :since
        ORDER BY day DESC
    """)
    suspend fun getDistinctDaysForSong(songId: Long, since: Long): List<String>

    // Weekend listening total (Sat=6, Sun=0 in SQLite strftime %w)
    @Query("""
        SELECT COALESCE(SUM(durationMs), 0) FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
        AND CAST(strftime('%w', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) IN (0, 6)
    """)
    suspend fun getWeekendListeningMsSuspend(since: Long): Long

    // Total completed listening ms since a timestamp (for statline calculations — counts only completed plays)
    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events WHERE startedAt >= :since AND completed = 1")
    suspend fun getListeningTimeSinceSuspend(since: Long): Long

    // Unique artist count in a period (for Wide Taste)
    @Query("""
        SELECT COUNT(DISTINCT s.artist) FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE e.completed = 1 AND e.startedAt >= :since
    """)
    suspend fun getUniqueArtistCountSinceSuspend(since: Long): Int

    // Top N songs by play count in a time window (for Repeat Offender, Comfort Zone)
    @Query("""
        SELECT e.songId, s.title, s.artist, s.albumArtUrl,
               COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
               COUNT(CASE WHEN e.completed = 1 THEN 1 END) AS playCount
        FROM listening_events e JOIN songs s ON e.songId = s.id
        WHERE e.startedAt >= :since AND e.completed = 1
        GROUP BY e.songId
        ORDER BY playCount DESC
        LIMIT :limit
    """)
    suspend fun getTopSongsInPeriodByPlayCountSuspend(since: Long, limit: Int): List<SongPlayStats>

    // Total completed play count in a period (for Repeat Offender, Comfort Zone)
    @Query("""
        SELECT COUNT(*) FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
    """)
    suspend fun getTotalPlayCountInPeriodSuspend(since: Long): Long

    // Ordered events for consecutive-run detection (for Album Listener)
    @Query("""
        SELECT e.songId, s.artist, e.startedAt, e.durationMs
        FROM listening_events e JOIN songs s ON e.songId = s.id
        WHERE e.completed = 1 AND e.startedAt >= :since
        ORDER BY e.startedAt ASC
    """)
    suspend fun getOrderedSongArtistEventsSuspend(since: Long): List<SongArtistEvent>

    // Last completed play timestamp for a song before a cutoff (for Resurrection)
    @Query("""
        SELECT MAX(startedAt) FROM listening_events
        WHERE songId = :songId AND completed = 1 AND startedAt < :before
    """)
    suspend fun getSongLastPlayedBeforeSuspend(songId: Long, before: Long): Long?

    // Night listening totals grouped by calendar day, hours 0-3 (for Night Binge)
    @Query("""
        SELECT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(durationMs), 0) AS nightMs
        FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
        AND CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) < 4
        GROUP BY day
    """)
    suspend fun getNightListeningByDaySuspend(since: Long): List<DayNightListening>

    // Last completed play timestamp for an artist before a cutoff (for Rediscovery)
    @Query("""
        SELECT MAX(e.startedAt) FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE s.artist = :artist AND e.completed = 1 AND e.startedAt < :before
    """)
    suspend fun getArtistLastPlayedBeforeSuspend(artist: String, before: Long): Long?

    // Artist play count in a time period (for Rediscovery, Breakup plays chip)
    @Query("""
        SELECT COUNT(*) FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE s.artist = :artist AND e.completed = 1 AND e.startedAt >= :since
    """)
    suspend fun getArtistPlayCountSinceSuspend(artist: String, since: Long): Int

    // Song play count before a cutoff (for Slow Burn - plays before this week)
    @Query("""
        SELECT COUNT(*) FROM listening_events
        WHERE songId = :songId AND completed = 1 AND startedAt < :before
    """)
    suspend fun getSongPlayCountBeforeSuspend(songId: Long, before: Long): Int

    // Song play count since a timestamp (for Slow Burn - plays this week)
    @Query("""
        SELECT COUNT(*) FROM listening_events
        WHERE songId = :songId AND completed = 1 AND startedAt >= :since
    """)
    suspend fun getSongPlayCountSinceSuspend(songId: Long, since: Long): Int

    // Weekly listening totals (ISO week, for Marathon Week)
    @Query("""
        SELECT strftime('%Y-W%W', startedAt / 1000, 'unixepoch', 'localtime') AS weekKey,
               COALESCE(SUM(durationMs), 0) AS totalMs
        FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
        GROUP BY weekKey
        ORDER BY weekKey ASC
    """)
    suspend fun getWeeklyListeningTotalsSuspend(since: Long): List<WeeklyListening>

    // Song rank by all-time play count (1 = most played)
    @Query("""
        SELECT COUNT(*) + 1 FROM (
            SELECT songId, COUNT(*) AS playCount
            FROM listening_events WHERE completed = 1
            GROUP BY songId
        ) WHERE playCount > (
            SELECT COUNT(*) FROM listening_events
            WHERE songId = :songId AND completed = 1
        )
    """)
    suspend fun getSongRankByPlayCountSuspend(songId: Long): Int

    // Unique song count for an artist (for Artist Hour Milestones chip)
    @Query("""
        SELECT COUNT(DISTINCT e.songId) FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE s.artist = :artist AND e.completed = 1
    """)
    suspend fun getUniqueSongCountForArtistSuspend(artist: String): Int

    // Average daily listening in ms over a period (for Streak chips)
    @Query("""
        SELECT COALESCE(
            SUM(durationMs) / NULLIF(COUNT(DISTINCT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime')), 0),
            0
        )
        FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
    """)
    suspend fun getAvgDailyListeningMsSuspend(since: Long): Long

    // Unique song count in a time period (for Streak and Total Hours chips)
    @Query("""
        SELECT COUNT(DISTINCT songId) FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
    """)
    suspend fun getUniqueSongCountInPeriodSuspend(since: Long): Int

    // New songs discovered since a timestamp (for Explorer and Discovery Week chips)
    @Query("""
        SELECT COUNT(*) FROM songs WHERE firstHeardAt >= :since
    """)
    suspend fun getNewSongsSinceSuspend(since: Long): Int

    // Count days in a period with commute-hour listening (for Commute Listener chip)
    @Query("""
        SELECT COUNT(DISTINCT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime'))
        FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
        AND CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) IN (7, 8, 17, 18)
    """)
    suspend fun getCommuteDaysCountSuspend(since: Long): Int
}
