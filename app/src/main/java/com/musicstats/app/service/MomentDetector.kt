package com.musicstats.app.service

import com.musicstats.app.util.formatDuration
import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.model.Moment
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentDetector @Inject constructor(
    private val eventDao: ListeningEventDao,
    private val artistDao: ArtistDao,
    private val momentDao: MomentDao
) {

    companion object {
        val SONG_PLAY_THRESHOLDS = listOf(10, 25, 50, 100, 250, 500)
        val ARTIST_HOUR_THRESHOLDS_MS = listOf(1L, 5L, 10L, 24L).map { it * 3_600_000L }
        val STREAK_THRESHOLDS = listOf(3, 7, 14, 30, 100)
        val TOTAL_HOUR_THRESHOLDS_MS = listOf(24L, 100L, 500L, 1000L).map { it * 3_600_000L }
        val DISCOVERY_THRESHOLDS = listOf(50, 100, 250, 500)
    }

    suspend fun detectAndPersistNewMoments(): List<Moment> {
        val newMoments = mutableListOf<Moment>()
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 3600 * 1000
        val sevenDaysAgo = now - 7L * 24 * 3600 * 1000
        val todayStart = startOfDay(now)
        val yearMonth = LocalDate.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM"))

        newMoments += detectSongPlayMilestones()
        newMoments += detectArtistHourMilestones()
        newMoments += detectStreakMilestones()
        newMoments += detectTotalHourMilestones(now)
        newMoments += detectDiscoveryMilestones()
        newMoments += detectArchetypes(thirtyDaysAgo, yearMonth)
        newMoments += detectObsessionDaily(todayStart, now)
        newMoments += detectDailyRitual(sevenDaysAgo, now)
        newMoments += detectBreakupCandidate(sevenDaysAgo, now)
        newMoments += detectFastObsession(now)
        newMoments += detectLongestSession(now)
        newMoments += detectQuickObsession(sevenDaysAgo, now)
        newMoments += detectDiscoveryWeek(sevenDaysAgo, now, yearMonth)

        return newMoments
    }

    private suspend fun persistIfNew(moment: Moment): Moment? {
        if (momentDao.existsByTypeAndKey(moment.type, moment.entityKey)) return null
        val id = momentDao.insert(moment)
        return if (id > 0) moment else null
    }

    private suspend fun detectSongPlayMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val songs = eventDao.getSongsWithMinPlays(SONG_PLAY_THRESHOLDS.first())
        for (song in songs) {
            for (threshold in SONG_PLAY_THRESHOLDS) {
                if (song.playCount >= threshold) {
                    persistIfNew(Moment(
                        type = "SONG_PLAYS_$threshold",
                        entityKey = "${song.songId}:$threshold",
                        triggeredAt = System.currentTimeMillis(),
                        title = "$threshold plays",
                        description = "You've played ${song.title} $threshold times",
                        songId = song.songId,
                        statLine = "${formatDuration(song.totalDurationMs)} total",
                        imageUrl = song.albumArtUrl
                    ))?.let { result += it }
                }
            }
        }
        return result
    }

    private suspend fun detectArtistHourMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val artists = eventDao.getAllArtistsWithDurationSuspend()
        for (artist in artists) {
            val artistEntity = artistDao.findByName(artist.artist)
            for (thresholdMs in ARTIST_HOUR_THRESHOLDS_MS) {
                if (artist.totalDurationMs >= thresholdMs) {
                    val hours = thresholdMs / 3_600_000L
                    val humanHours = if (hours == 1L) "1 hour" else "$hours hours"
                    persistIfNew(Moment(
                        type = "ARTIST_HOURS_$hours",
                        entityKey = "${artist.artist}:$hours",
                        triggeredAt = System.currentTimeMillis(),
                        title = "$humanHours of ${artist.artist}",
                        description = "You've spent $humanHours listening to ${artist.artist}",
                        artistId = artistEntity?.id,
                        statLine = "${artist.playCount} total plays",
                        imageUrl = artistEntity?.imageUrl
                    ))?.let { result += it }
                }
            }
        }
        return result
    }

    private suspend fun detectStreakMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val streak = computeStreak()
        for (threshold in STREAK_THRESHOLDS) {
            if (streak >= threshold) {
                persistIfNew(Moment(
                    type = "STREAK_$threshold",
                    entityKey = "$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = "$threshold-day streak",
                    description = "$threshold days in a row — you're on fire"
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectTotalHourMilestones(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        for (thresholdMs in TOTAL_HOUR_THRESHOLDS_MS) {
            if (totalMs >= thresholdMs) {
                val hours = thresholdMs / 3_600_000L
                persistIfNew(Moment(
                    type = "TOTAL_HOURS_$hours",
                    entityKey = "$hours",
                    triggeredAt = now,
                    title = "$hours hours",
                    description = "You've listened to ${hours}h of music in total"
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectDiscoveryMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val uniqueSongs = eventDao.getUniqueSongCountSuspend()
        for (threshold in DISCOVERY_THRESHOLDS) {
            if (uniqueSongs >= threshold) {
                persistIfNew(Moment(
                    type = "SONGS_DISCOVERED_$threshold",
                    entityKey = "$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = "$threshold songs",
                    description = "You've discovered $threshold unique songs"
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectArchetypes(since: Long, yearMonth: String): List<Moment> {
        val result = mutableListOf<Moment>()
        val now = System.currentTimeMillis()

        val hourly = eventDao.getHourlyListeningSuspend(since)
        val totalMs = hourly.sumOf { it.totalDurationMs }.coerceAtLeast(1L)
        val nightMs = hourly.filter { it.hour in listOf(22, 23, 0, 1, 2, 3) }
            .sumOf { it.totalDurationMs }
        val morningMs = hourly.filter { it.hour in 5..8 }.sumOf { it.totalDurationMs }
        val commuteAmMs = hourly.filter { it.hour in 7..8 }.sumOf { it.totalDurationMs }
        val commutePmMs = hourly.filter { it.hour in 17..18 }.sumOf { it.totalDurationMs }

        if (nightMs.toDouble() / totalMs > 0.5) {
            val nightPct = (nightMs * 100 / totalMs).toInt()
            persistIfNew(Moment(
                type = "ARCHETYPE_NIGHT_OWL", entityKey = yearMonth, triggeredAt = now,
                title = "Night Owl",
                description = "You do most of your listening after 10pm",
                statLine = "$nightPct% of your listening"
            ))?.let { result += it }
        }
        if (morningMs.toDouble() / totalMs > 0.5) {
            val morningPct = (morningMs * 100 / totalMs).toInt()
            persistIfNew(Moment(
                type = "ARCHETYPE_MORNING_LISTENER", entityKey = yearMonth, triggeredAt = now,
                title = "Morning Listener",
                description = "You do most of your listening before 9am",
                statLine = "$morningPct% of your listening"
            ))?.let { result += it }
        }
        if (totalMs > 1L && commuteAmMs > 0 && commutePmMs > 0 &&
            (commuteAmMs + commutePmMs).toDouble() / totalMs > 0.3) {
            val commutePct = ((commuteAmMs + commutePmMs) * 100 / totalMs).toInt()
            persistIfNew(Moment(
                type = "ARCHETYPE_COMMUTE_LISTENER", entityKey = yearMonth, triggeredAt = now,
                title = "Commute Listener",
                description = "Your listening peaks at 7–9am and 5–7pm",
                statLine = "$commutePct% during commute hours"
            ))?.let { result += it }
        }

        val totalPlays = eventDao.getTotalPlayCountSuspend().coerceAtLeast(1)
        val totalSkips = eventDao.getTotalSkipCountSuspend()
        val skipRate = totalSkips.toDouble() / (totalPlays + totalSkips)
        if (skipRate < 0.05) {
            val skipPct = "%.1f".format(skipRate * 100)
            persistIfNew(Moment(
                type = "ARCHETYPE_COMPLETIONIST", entityKey = yearMonth, triggeredAt = now,
                title = "Completionist",
                description = "You skip less than 5% of songs — truly dedicated",
                statLine = "${skipPct}% skip rate"
            ))?.let { result += it }
        }
        if (skipRate > 0.40) {
            val skipPct = "%.1f".format(skipRate * 100)
            persistIfNew(Moment(
                type = "ARCHETYPE_CERTIFIED_SKIPPER", entityKey = yearMonth, triggeredAt = now,
                title = "Certified Skipper",
                description = "You skip more than 40% of songs. Nothing is good enough.",
                statLine = "${skipPct}% skip rate"
            ))?.let { result += it }
        }

        val deepCuts = eventDao.getSongsWithMinPlays(50)
        if (deepCuts.isNotEmpty()) {
            persistIfNew(Moment(
                type = "ARCHETYPE_DEEP_CUT_DIGGER", entityKey = yearMonth, triggeredAt = now,
                title = "Deep Cut Digger",
                description = "You've listened to ${deepCuts[0].title} over 50 times",
                statLine = "${deepCuts[0].playCount} plays",
                imageUrl = deepCuts[0].albumArtUrl
            ))?.let { result += it }
        }

        val topArtists = eventDao.getTopArtistsByDurationSuspend(0L, 1)
        if (topArtists.isNotEmpty()) {
            val topMs = topArtists[0].totalDurationMs
            val allMs = eventDao.getTotalListeningTimeMsSuspend().coerceAtLeast(1L)
            if (topMs.toDouble() / allMs > 0.5) {
                val artistEntity = artistDao.findByName(topArtists[0].artist)
                val topPct = (topMs * 100 / allMs).toInt()
                persistIfNew(Moment(
                    type = "ARCHETYPE_LOYAL_FAN", entityKey = yearMonth, triggeredAt = now,
                    title = "Loyal Fan",
                    description = "Over 50% of your listening is ${topArtists[0].artist}",
                    artistId = artistEntity?.id,
                    statLine = "$topPct% of your listening",
                    imageUrl = artistEntity?.imageUrl
                ))?.let { result += it }
            }
        }

        val newArtistsThisWeek = eventDao.getNewArtistsSinceSuspend(now - 7L * 24 * 3600 * 1000)
        if (newArtistsThisWeek >= 5) {
            persistIfNew(Moment(
                type = "ARCHETYPE_EXPLORER", entityKey = yearMonth, triggeredAt = now,
                title = "Explorer",
                description = "You discovered $newArtistsThisWeek new artists this week"
            ))?.let { result += it }
        }

        return result
    }

    private suspend fun detectObsessionDaily(todayStart: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val todayEnd = todayStart + 86_400_000L
        val todaySongs = eventDao.getSongsPlayedOnDay(todayStart, todayEnd)
        val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()
        for (song in todaySongs) {
            if (song.playCount >= 5) {
                persistIfNew(Moment(
                    type = "OBSESSION_DAILY",
                    entityKey = "${song.songId}:$todayDate",
                    triggeredAt = now,
                    title = "${song.playCount}x in one day",
                    description = "You played ${song.title} ${song.playCount} times today. Are you okay?",
                    songId = song.songId,
                    imageUrl = song.albumArtUrl
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectDailyRitual(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val recentSongs = eventDao.getSongsWithMinPlays(7)
        val last7Days = (0 until 7).map {
            LocalDate.now(ZoneId.systemDefault()).minusDays(it.toLong()).toString()
        }.toSet()
        val detectedDate = LocalDate.now(ZoneId.systemDefault()).toString()
        for (song in recentSongs) {
            val days = eventDao.getDistinctDaysForSong(song.songId, sevenDaysAgo).toSet()
            if (days.containsAll(last7Days)) {
                persistIfNew(Moment(
                    type = "DAILY_RITUAL",
                    entityKey = "${song.songId}:$detectedDate",
                    triggeredAt = now,
                    title = "Daily ritual",
                    description = "You've listened to ${song.title} every day for 7 days",
                    songId = song.songId,
                    imageUrl = song.albumArtUrl
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectBreakupCandidate(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val allArtists = eventDao.getAllArtistsWithDurationSuspend()
        for (artistStats in allArtists) {
            val skipCount = eventDao.getArtistSkipCountSince(artistStats.artist, sevenDaysAgo)
            if (skipCount >= 10) {
                val artistEntity = artistDao.findByName(artistStats.artist)
                persistIfNew(Moment(
                    type = "BREAKUP_CANDIDATE",
                    entityKey = "${artistStats.artist}:$weekKey",
                    triggeredAt = now,
                    title = "Maybe break up?",
                    description = "You've skipped ${artistStats.artist} $skipCount times this week",
                    artistId = artistEntity?.id,
                    imageUrl = artistEntity?.imageUrl
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectFastObsession(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val songs = eventDao.getSongsWithMinPlays(20)
        for (song in songs) {
            val ageMs = now - song.firstHeardAt
            if (ageMs in 1..(30L * 24 * 3600 * 1000)) {
                val ageDays = ageMs / 86_400_000L
                persistIfNew(Moment(
                    type = "FAST_OBSESSION",
                    entityKey = "${song.songId}",
                    triggeredAt = now,
                    title = "${song.playCount} plays in $ageDays days",
                    description = "${song.title} came into your life $ageDays days ago. You've played it ${song.playCount} times.",
                    songId = song.songId,
                    imageUrl = song.albumArtUrl
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectLongestSession(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val longestMs = eventDao.getLongestSessionMs()
        if (longestMs >= 3_600_000L) {
            val hours = longestMs / 3_600_000L
            val mins = (longestMs % 3_600_000L) / 60_000L
            val label = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            persistIfNew(Moment(
                type = "LONGEST_SESSION",
                entityKey = "$longestMs",
                triggeredAt = now,
                title = "New record: $label",
                description = "New personal best: $label in one sitting"
            ))?.let { result += it }
        }
        return result
    }

    private suspend fun detectQuickObsession(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val top5 = eventDao.getTopSongsByPlayCountSuspend(5)
        val top5Ids = top5.map { it.songId }.toSet()
        val recentSongs = eventDao.getSongsWithMinPlays(1)
            .filter { it.songId in top5Ids && it.firstHeardAt >= sevenDaysAgo }
        for (song in recentSongs) {
            val ageDays = (now - song.firstHeardAt) / 86_400_000L
            persistIfNew(Moment(
                type = "QUICK_OBSESSION",
                entityKey = "${song.songId}",
                triggeredAt = now,
                title = "Fast obsession",
                description = "You discovered ${song.title} $ageDays days ago. It's already in your top 5.",
                songId = song.songId,
                imageUrl = song.albumArtUrl
            ))?.let { result += it }
        }
        return result
    }

    private suspend fun detectDiscoveryWeek(sevenDaysAgo: Long, now: Long, yearMonth: String): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val newArtistsCount = eventDao.getNewArtistsSinceSuspend(sevenDaysAgo)
        if (newArtistsCount >= 8) {
            persistIfNew(Moment(
                type = "DISCOVERY_WEEK",
                entityKey = weekKey,
                triggeredAt = now,
                title = "$newArtistsCount new artists",
                description = "You discovered $newArtistsCount new artists this week"
            ))?.let { result += it }
        }
        return result
    }

    private suspend fun computeStreak(): Int {
        var streak = 0
        var checkDay = LocalDate.now(ZoneId.systemDefault())
        while (streak <= 365) {
            val dayStart = checkDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = dayStart + 86_400_000L
            val count = eventDao.getSongsPlayedOnDay(dayStart, dayEnd).size
            if (count == 0) break
            streak++
            checkDay = checkDay.minusDays(1)
        }
        return streak
    }

    private fun startOfDay(epochMs: Long): Long {
        val date = java.time.Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault()).toLocalDate()
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
