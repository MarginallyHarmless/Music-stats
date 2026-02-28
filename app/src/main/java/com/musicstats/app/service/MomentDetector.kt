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
        // Backfill imageUrl on existing moments where artist image was fetched after detection
        momentDao.backfillArtistImageUrls()

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
        newMoments += detectResurrection(todayStart, now)
        newMoments += detectNightBinge(now)
        newMoments += detectComfortZone(sevenDaysAgo, now)
        newMoments += detectRediscovery(sevenDaysAgo, now)
        newMoments += detectSlowBurn(sevenDaysAgo, now)
        newMoments += detectMarathonWeek(now)

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
            val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
            for (threshold in SONG_PLAY_THRESHOLDS) {
                if (song.playCount >= threshold) {
                    persistIfNew(Moment(
                        type = "SONG_PLAYS_$threshold",
                        entityKey = "${song.songId}:$threshold",
                        triggeredAt = System.currentTimeMillis(),
                        title = "$threshold plays",
                        description = "You've played ${song.title} $threshold times",
                        songId = song.songId,
                        statLines = listOf("${formatDuration(song.totalDurationMs)} total", "#$rank all-time"),
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
                    val uniqueSongs = eventDao.getUniqueSongCountForArtistSuspend(artist.artist)
                    persistIfNew(Moment(
                        type = "ARTIST_HOURS_$hours",
                        entityKey = "${artist.artist}:$hours",
                        triggeredAt = System.currentTimeMillis(),
                        title = "$humanHours of ${artist.artist}",
                        description = "You've spent $humanHours listening to ${artist.artist}",
                        artistId = artistEntity?.id,
                        statLines = listOf("${artist.playCount} plays", "$uniqueSongs songs heard"),
                        imageUrl = artistEntity?.imageUrl,
                        entityName = artist.artist
                    ))?.let { result += it }
                }
            }
        }
        return result
    }

    private suspend fun detectStreakMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val streak = computeStreak()
        val now = System.currentTimeMillis()
        for (threshold in STREAK_THRESHOLDS) {
            if (streak >= threshold) {
                val since = now - threshold.toLong() * 86_400_000L
                val avgMs = eventDao.getAvgDailyListeningMsSuspend(since)
                val avgMins = avgMs / 60_000L
                val uniqueSongs = eventDao.getUniqueSongCountInPeriodSuspend(since)
                persistIfNew(Moment(
                    type = "STREAK_$threshold",
                    entityKey = "$threshold",
                    triggeredAt = now,
                    title = "$threshold-day streak",
                    description = "$threshold days in a row — you're on fire",
                    statLines = listOf("avg ${avgMins}min/day", "$uniqueSongs songs this streak")
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectTotalHourMilestones(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        val uniqueSongs = eventDao.getUniqueSongCountSuspend()
        val uniqueArtists = eventDao.getUniqueArtistCountSuspend()
        for (thresholdMs in TOTAL_HOUR_THRESHOLDS_MS) {
            if (totalMs >= thresholdMs) {
                val hours = thresholdMs / 3_600_000L
                persistIfNew(Moment(
                    type = "TOTAL_HOURS_$hours",
                    entityKey = "$hours",
                    triggeredAt = now,
                    title = "$hours hours",
                    description = "You've listened to ${hours}h of music in total",
                    statLines = listOf("$uniqueSongs unique songs", "$uniqueArtists artists")
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectDiscoveryMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val uniqueSongs = eventDao.getUniqueSongCountSuspend()
        val uniqueArtists = eventDao.getUniqueArtistCountSuspend()
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        val totalHours = totalMs / 3_600_000L
        for (threshold in DISCOVERY_THRESHOLDS) {
            if (uniqueSongs >= threshold) {
                persistIfNew(Moment(
                    type = "SONGS_DISCOVERED_$threshold",
                    entityKey = "$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = "$threshold songs",
                    description = "You've discovered $threshold unique songs",
                    statLines = listOf("from $uniqueArtists artists", "${totalHours}h of music")
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
            val peakNightHour = hourly.filter { it.hour in listOf(22, 23, 0, 1, 2, 3) }
                .maxByOrNull { it.totalDurationMs }?.hour ?: 23
            val peakLabel = when (peakNightHour) {
                0 -> "midnight"; 1 -> "1am"; 2 -> "2am"; 3 -> "3am"
                22 -> "10pm"; 23 -> "11pm"; else -> "${peakNightHour}pm"
            }
            persistIfNew(Moment(
                type = "ARCHETYPE_NIGHT_OWL", entityKey = yearMonth, triggeredAt = now,
                title = "Night Owl",
                description = "You do most of your listening after 10pm",
                statLines = listOf("$nightPct% of your listening", "peak: $peakLabel")
            ))?.let { result += it }
        }
        if (morningMs.toDouble() / totalMs > 0.5) {
            val morningPct = (morningMs * 100 / totalMs).toInt()
            val peakMorningHour = hourly.filter { it.hour in 5..8 }
                .maxByOrNull { it.totalDurationMs }?.hour ?: 7
            persistIfNew(Moment(
                type = "ARCHETYPE_MORNING_LISTENER", entityKey = yearMonth, triggeredAt = now,
                title = "Morning Listener",
                description = "You do most of your listening before 9am",
                statLines = listOf("$morningPct% of your listening", "peak: ${peakMorningHour}am")
            ))?.let { result += it }
        }
        if (totalMs > 1L && commuteAmMs > 0 && commutePmMs > 0 &&
            (commuteAmMs + commutePmMs).toDouble() / totalMs > 0.3) {
            val commutePct = ((commuteAmMs + commutePmMs) * 100 / totalMs).toInt()
            val commuteDays = eventDao.getCommuteDaysCountSuspend(since)
            persistIfNew(Moment(
                type = "ARCHETYPE_COMMUTE_LISTENER", entityKey = yearMonth, triggeredAt = now,
                title = "Commute Listener",
                description = "Your listening peaks at 7–9am and 5–7pm",
                statLines = listOf("$commutePct% during commute hours", "$commuteDays days this month")
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
                statLines = listOf("${skipPct}% skip rate", "$totalPlays plays")
            ))?.let { result += it }
        }
        if (skipRate > 0.40) {
            val skipPct = "%.1f".format(skipRate * 100)
            persistIfNew(Moment(
                type = "ARCHETYPE_CERTIFIED_SKIPPER", entityKey = yearMonth, triggeredAt = now,
                title = "Certified Skipper",
                description = "You skip more than 40% of songs. Nothing is good enough.",
                statLines = listOf("${skipPct}% skip rate", "$totalSkips skips")
            ))?.let { result += it }
        }

        val deepCuts = eventDao.getSongsWithMinPlays(50)
        if (deepCuts.isNotEmpty()) {
            val deepCutMs = deepCuts[0].totalDurationMs
            val deepCutHours = deepCutMs / 3_600_000L
            val deepCutMins = (deepCutMs % 3_600_000L) / 60_000L
            val deepCutDuration = if (deepCutHours > 0) "${deepCutHours}h ${deepCutMins}m" else "${deepCutMins}m"
            persistIfNew(Moment(
                type = "ARCHETYPE_DEEP_CUT_DIGGER", entityKey = yearMonth, triggeredAt = now,
                title = "Deep Cut Digger",
                description = "You've listened to ${deepCuts[0].title} over 50 times",
                statLines = listOf("${deepCuts[0].playCount} plays", "$deepCutDuration total"),
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
                    statLines = listOf("$topPct% of listening", "${topArtists[0].playCount} plays"),
                    imageUrl = artistEntity?.imageUrl,
                    entityName = topArtists[0].artist
                ))?.let { result += it }
            }
        }

        val newArtistsThisWeek = eventDao.getNewArtistsSinceSuspend(now - 7L * 24 * 3600 * 1000)
        if (newArtistsThisWeek >= 5) {
            val newSongsThisWeek = eventDao.getNewSongsSinceSuspend(now - 7L * 24 * 3600 * 1000)
            persistIfNew(Moment(
                type = "ARCHETYPE_EXPLORER", entityKey = yearMonth, triggeredAt = now,
                title = "Explorer",
                description = "You discovered $newArtistsThisWeek new artists this week",
                statLines = listOf("$newArtistsThisWeek new artists", "$newSongsThisWeek new songs")
            ))?.let { result += it }
        }

        // Weekend Warrior
        val fourWeeksAgo = now - 28L * 24 * 3600 * 1000
        val weekendMs = eventDao.getWeekendListeningMsSuspend(fourWeeksAgo)
        val totalFourWeekMs = eventDao.getListeningTimeSinceSuspend(fourWeeksAgo).coerceAtLeast(1L)
        if (weekendMs.toDouble() / totalFourWeekMs > 0.6) {
            val weekendPct = (weekendMs * 100 / totalFourWeekMs).toInt()
            val weekendHoursPerWeek = weekendMs / 4 / 3_600_000L
            persistIfNew(Moment(
                type = "ARCHETYPE_WEEKEND_WARRIOR", entityKey = yearMonth, triggeredAt = now,
                title = "Weekend Warrior",
                description = "Most of your listening happens on weekends",
                statLines = listOf("$weekendPct% on weekends", "${weekendHoursPerWeek}h on weekends/week")
            ))?.let { result += it }
        }

        // Wide Taste
        val uniqueArtistsThisMonth = eventDao.getUniqueArtistCountSinceSuspend(since)
        val topArtistThisMonth = eventDao.getTopArtistsByDurationSuspend(since, 1)
        val totalThisMonthMs = eventDao.getListeningTimeSinceSuspend(since).coerceAtLeast(1L)
        if (topArtistThisMonth.isNotEmpty() && uniqueArtistsThisMonth >= 15) {
            val topArtistPct = (topArtistThisMonth[0].totalDurationMs * 100 / totalThisMonthMs).toInt()
            if (topArtistPct < 15) {
                persistIfNew(Moment(
                    type = "ARCHETYPE_WIDE_TASTE", entityKey = yearMonth, triggeredAt = now,
                    title = "Wide Taste",
                    description = "No single artist dominates your listening",
                    statLines = listOf("$uniqueArtistsThisMonth artists this month", "top artist: $topArtistPct%")
                ))?.let { result += it }
            }
        }

        // Repeat Offender
        val totalPlaysThisMonth = eventDao.getTotalPlayCountInPeriodSuspend(since).coerceAtLeast(1L)
        val top3ThisMonth = eventDao.getTopSongsInPeriodByPlayCountSuspend(since, 3)
        if (top3ThisMonth.isNotEmpty()) {
            val top3Plays = top3ThisMonth.sumOf { it.playCount }
            val top3Pct = (top3Plays * 100 / totalPlaysThisMonth).toInt()
            if (top3Pct > 40) {
                val topSong = top3ThisMonth[0]
                persistIfNew(Moment(
                    type = "ARCHETYPE_REPEAT_OFFENDER", entityKey = yearMonth, triggeredAt = now,
                    title = "Repeat Offender",
                    description = "You found your songs. You're not letting go.",
                    songId = topSong.songId,
                    statLines = listOf("top 3 songs: $top3Pct% of plays", "${topSong.title} × ${topSong.playCount}"),
                    imageUrl = topSong.albumArtUrl
                ))?.let { result += it }
            }
        }

        // Album Listener — detect consecutive same-artist runs ≥ 5 in sessions
        val orderedEvents = eventDao.getOrderedSongArtistEventsSuspend(since)
        val sessionGapMs = 30 * 60 * 1000L // 30-minute gap = new session
        data class AlbumRun(val artist: String, val length: Int)
        val albumRuns = mutableListOf<AlbumRun>()
        var currentArtist: String? = null
        var currentRunLength = 0
        var lastEndMs = 0L

        for (event in orderedEvents) {
            val isNewSession = event.startedAt - lastEndMs > sessionGapMs
            if (isNewSession || event.artist != currentArtist) {
                if (currentRunLength >= 5 && currentArtist != null) {
                    albumRuns += AlbumRun(currentArtist!!, currentRunLength)
                }
                currentArtist = event.artist
                currentRunLength = 1
            } else {
                currentRunLength++
            }
            lastEndMs = event.startedAt + event.durationMs
        }
        // Check last run
        if (currentRunLength >= 5 && currentArtist != null) {
            albumRuns += AlbumRun(currentArtist!!, currentRunLength)
        }

        if (albumRuns.size >= 3) {
            val avgRun = albumRuns.map { it.length }.average().toInt()
            val topRunArtist = albumRuns.groupBy { it.artist }
                .maxByOrNull { it.value.size }?.key
            val topSong = if (topRunArtist != null)
                eventDao.getTopSongsInPeriodByPlayCountSuspend(since, 100)
                    .firstOrNull { it.artist == topRunArtist }
            else null
            persistIfNew(Moment(
                type = "ARCHETYPE_ALBUM_LISTENER", entityKey = yearMonth, triggeredAt = now,
                title = "Album Listener",
                description = "You don't shuffle. You commit.",
                songId = topSong?.songId,
                statLines = listOf("${albumRuns.size} album runs this month", "avg $avgRun songs per run"),
                imageUrl = topSong?.albumArtUrl
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
                val allTimePlays = eventDao.getSongPlayCountSinceSuspend(song.songId, 0L)
                persistIfNew(Moment(
                    type = "OBSESSION_DAILY",
                    entityKey = "${song.songId}:$todayDate",
                    triggeredAt = now,
                    title = "${song.playCount}x in one day",
                    description = "You played ${song.title} ${song.playCount} times today. Are you okay?",
                    songId = song.songId,
                    statLines = listOf("${song.playCount} plays today", "$allTimePlays all-time"),
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
                    statLines = listOf("${song.playCount} all-time plays", "${formatDuration(song.totalDurationMs)} total"),
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
                val playsThisWeek = eventDao.getArtistPlayCountSinceSuspend(artistStats.artist, sevenDaysAgo)
                persistIfNew(Moment(
                    type = "BREAKUP_CANDIDATE",
                    entityKey = "${artistStats.artist}:$weekKey",
                    triggeredAt = now,
                    title = "Maybe break up?",
                    description = "You've skipped ${artistStats.artist} $skipCount times this week",
                    artistId = artistEntity?.id,
                    statLines = listOf("$skipCount skips this week", "$playsThisWeek plays this week"),
                    imageUrl = artistEntity?.imageUrl,
                    entityName = artistStats.artist
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
                val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
                persistIfNew(Moment(
                    type = "FAST_OBSESSION",
                    entityKey = "${song.songId}",
                    triggeredAt = now,
                    title = "${song.playCount} plays in $ageDays days",
                    description = "${song.title} came into your life $ageDays days ago. You've played it ${song.playCount} times.",
                    songId = song.songId,
                    statLines = listOf("${song.playCount} plays", "$ageDays days · #$rank all-time"),
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
                description = "New personal best: $label in one sitting",
                statLines = listOf(label, "new personal best")
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
            val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
            persistIfNew(Moment(
                type = "QUICK_OBSESSION",
                entityKey = "${song.songId}",
                triggeredAt = now,
                title = "Fast obsession",
                description = "You discovered ${song.title} $ageDays days ago. It's already in your top 5.",
                songId = song.songId,
                statLines = listOf("#$rank all-time", "$ageDays days since discovered"),
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
            val newSongsCount = eventDao.getNewSongsSinceSuspend(sevenDaysAgo)
            persistIfNew(Moment(
                type = "DISCOVERY_WEEK",
                entityKey = weekKey,
                triggeredAt = now,
                title = "$newArtistsCount new artists",
                description = "You discovered $newArtistsCount new artists this week",
                statLines = listOf("$newArtistsCount new artists", "$newSongsCount new songs")
            ))?.let { result += it }
        }
        return result
    }

    private suspend fun detectResurrection(todayStart: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val todayEnd = todayStart + 86_400_000L
        val todaySongs = eventDao.getSongsPlayedOnDay(todayStart, todayEnd)
        val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()

        for (song in todaySongs) {
            if (song.playCount < 5) continue
            val lastPlayedBefore = eventDao.getSongLastPlayedBeforeSuspend(song.songId, todayStart) ?: continue
            val gapDays = (todayStart - lastPlayedBefore) / 86_400_000L
            if (gapDays >= 30) {
                persistIfNew(Moment(
                    type = "RESURRECTION",
                    entityKey = "${song.songId}:$todayDate",
                    triggeredAt = now,
                    title = "It's back",
                    description = "${song.title} went quiet for $gapDays days. Today it's all you're playing.",
                    songId = song.songId,
                    statLines = listOf("$gapDays days away", "${song.playCount} plays today"),
                    imageUrl = song.albumArtUrl
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectNightBinge(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val thirtyDaysAgo = now - 30L * 24 * 3600 * 1000
        val nightDays = eventDao.getNightListeningByDaySuspend(thirtyDaysAgo)

        for (dayNight in nightDays) {
            if (dayNight.nightMs >= 2 * 3_600_000L) {
                val duration = formatDuration(dayNight.nightMs)
                val songCount = (dayNight.nightMs / 210_000L).toInt().coerceAtLeast(1)
                persistIfNew(Moment(
                    type = "NIGHT_BINGE",
                    entityKey = dayNight.day,
                    triggeredAt = now,
                    title = "Night binge",
                    description = "You listened for $duration after midnight",
                    statLines = listOf("$duration after midnight", "~$songCount songs")
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectComfortZone(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val totalPlaysThisWeek = eventDao.getTotalPlayCountInPeriodSuspend(sevenDaysAgo).coerceAtLeast(1L)
        val top5 = eventDao.getTopSongsInPeriodByPlayCountSuspend(sevenDaysAgo, 5)
        if (top5.isEmpty()) return result
        val top5Plays = top5.sumOf { it.playCount }
        val top5Pct = (top5Plays * 100 / totalPlaysThisWeek).toInt()
        if (top5Pct >= 80) {
            val topSong = top5.first()
            persistIfNew(Moment(
                type = "COMFORT_ZONE",
                entityKey = weekKey,
                triggeredAt = now,
                title = "Comfort zone",
                description = "Your top 5 songs made up $top5Pct% of your listening this week",
                songId = topSong.songId,
                statLines = listOf("$top5Pct% from 5 songs", "$totalPlaysThisWeek plays this week"),
                imageUrl = topSong.albumArtUrl
            ))?.let { result += it }
        }
        return result
    }

    private suspend fun detectRediscovery(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val topArtistsThisWeek = eventDao.getTopArtistsByDurationSuspend(sevenDaysAgo, 5)

        for (artistStats in topArtistsThisWeek) {
            val playsThisWeek = eventDao.getArtistPlayCountSinceSuspend(artistStats.artist, sevenDaysAgo)
            if (playsThisWeek < 5) continue
            val lastPlayedBefore = eventDao.getArtistLastPlayedBeforeSuspend(artistStats.artist, sevenDaysAgo) ?: continue
            val gapDays = (sevenDaysAgo - lastPlayedBefore) / 86_400_000L
            if (gapDays >= 60) {
                val artistEntity = artistDao.findByName(artistStats.artist)
                persistIfNew(Moment(
                    type = "REDISCOVERY",
                    entityKey = "${artistStats.artist}:$weekKey",
                    triggeredAt = now,
                    title = "You're back",
                    description = "You hadn't played ${artistStats.artist} in $gapDays days. Welcome back.",
                    artistId = artistEntity?.id,
                    statLines = listOf("$gapDays days away", "$playsThisWeek plays this week"),
                    imageUrl = artistEntity?.imageUrl,
                    entityName = artistStats.artist
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectSlowBurn(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val topThisWeek = eventDao.getTopSongsInPeriodByPlayCountSuspend(sevenDaysAgo, 20)

        for (song in topThisWeek) {
            val playsThisWeek = eventDao.getSongPlayCountSinceSuspend(song.songId, sevenDaysAgo)
            if (playsThisWeek < 5) continue
            val playsBeforeThisWeek = eventDao.getSongPlayCountBeforeSuspend(song.songId, sevenDaysAgo)
            if (playsBeforeThisWeek >= 5) continue
            val songDetails = eventDao.getSongsWithMinPlays(0).firstOrNull { it.songId == song.songId } ?: continue
            val ageDays = (now - songDetails.firstHeardAt) / 86_400_000L
            if (ageDays < 60) continue
            val totalPlays = playsBeforeThisWeek + playsThisWeek
            persistIfNew(Moment(
                type = "SLOW_BURN",
                entityKey = "${song.songId}:$weekKey",
                triggeredAt = now,
                title = "Slow burn",
                description = "${song.title} has been in your library for $ageDays days. It just clicked.",
                songId = song.songId,
                statLines = listOf("$ageDays days to click", "$totalPlays plays now"),
                imageUrl = song.albumArtUrl
            ))?.let { result += it }
        }
        return result
    }

    private suspend fun detectMarathonWeek(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val twoYearsAgo = now - 730L * 24 * 3600 * 1000
        val weeks = eventDao.getWeeklyListeningTotalsSuspend(twoYearsAgo)
        if (weeks.size < 2) return result

        val currentWeekMs = weeks.lastOrNull()?.totalMs ?: return result
        val previousMax = weeks.dropLast(1).maxOfOrNull { it.totalMs } ?: return result

        if (currentWeekMs > previousMax) {
            val sevenDaysAgo = now - 7L * 24 * 3600 * 1000
            val songCount = eventDao.getTopSongsInPeriodByPlayCountSuspend(sevenDaysAgo, 999).size
            val artistCount = eventDao.getUniqueArtistCountSinceSuspend(sevenDaysAgo)
            val duration = formatDuration(currentWeekMs)
            persistIfNew(Moment(
                type = "MARATHON_WEEK",
                entityKey = weekKey,
                triggeredAt = now,
                title = "Marathon week",
                description = "New record: $duration this week",
                statLines = listOf("$duration this week", "$songCount songs · $artistCount artists")
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
