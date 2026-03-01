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
        val SONG_PLAY_THRESHOLDS = listOf(50, 100, 250, 500)
        val ARTIST_HOUR_THRESHOLDS_MS = listOf(5L, 10L, 24L).map { it * 3_600_000L }
        val STREAK_THRESHOLDS = listOf(7, 14, 30, 100)
        val TOTAL_HOUR_THRESHOLDS_MS = listOf(24L, 100L, 500L, 1000L).map { it * 3_600_000L }
        val DISCOVERY_THRESHOLDS = listOf(100, 250, 500)
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
                    val type = "SONG_PLAYS_$threshold"
                    val copyVariant = momentDao.countByType(type)
                    val rawStats = mapOf<String, Any>(
                        "totalDurationMs" to song.totalDurationMs,
                        "rank" to rank
                    )
                    val copy = MomentCopywriter.generate(type, song.artist, rawStats, copyVariant)
                    persistIfNew(Moment(
                        type = type,
                        entityKey = "${song.songId}:$threshold",
                        triggeredAt = System.currentTimeMillis(),
                        title = copy.title,
                        description = copy.description,
                        songId = song.songId,
                        statLines = copy.statLines,
                        imageUrl = song.albumArtUrl,
                        copyVariant = copyVariant
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
                    val type = "ARTIST_HOURS_$hours"
                    val copyVariant = momentDao.countByType(type)
                    val uniqueSongs = eventDao.getUniqueSongCountForArtistSuspend(artist.artist)
                    val rawStats = mapOf<String, Any>(
                        "playCount" to artist.playCount,
                        "uniqueSongs" to uniqueSongs
                    )
                    val copy = MomentCopywriter.generate(type, artist.artist, rawStats, copyVariant)
                    persistIfNew(Moment(
                        type = type,
                        entityKey = "${artist.artist}:$hours",
                        triggeredAt = System.currentTimeMillis(),
                        title = copy.title,
                        description = copy.description,
                        artistId = artistEntity?.id,
                        statLines = copy.statLines,
                        imageUrl = artistEntity?.imageUrl,
                        entityName = artist.artist,
                        copyVariant = copyVariant
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
                val type = "STREAK_$threshold"
                val copyVariant = momentDao.countByType(type)
                val since = now - threshold.toLong() * 86_400_000L
                val avgMs = eventDao.getAvgDailyListeningMsSuspend(since)
                val avgMins = avgMs / 60_000L
                val uniqueSongs = eventDao.getUniqueSongCountInPeriodSuspend(since)
                val rawStats = mapOf<String, Any>(
                    "avgMinPerDay" to avgMins,
                    "uniqueSongs" to uniqueSongs
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "$threshold",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    statLines = copy.statLines,
                    copyVariant = copyVariant
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
                val type = "TOTAL_HOURS_$hours"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "uniqueSongs" to uniqueSongs,
                    "uniqueArtists" to uniqueArtists
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "$hours",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    statLines = copy.statLines,
                    copyVariant = copyVariant
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
                val type = "SONGS_DISCOVERED_$threshold"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "uniqueArtists" to uniqueArtists,
                    "totalHours" to totalHours
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = copy.title,
                    description = copy.description,
                    statLines = copy.statLines,
                    copyVariant = copyVariant
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
            val type = "ARCHETYPE_NIGHT_OWL"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "nightPct" to "${nightPct}%",
                "peakLabel" to peakLabel
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }
        }
        if (morningMs.toDouble() / totalMs > 0.5) {
            val morningPct = (morningMs * 100 / totalMs).toInt()
            val peakMorningHour = hourly.filter { it.hour in 5..8 }
                .maxByOrNull { it.totalDurationMs }?.hour ?: 7
            val type = "ARCHETYPE_MORNING_LISTENER"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "morningPct" to "${morningPct}%",
                "peakLabel" to "${peakMorningHour}am"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }
        }
        if (totalMs > 1L && commuteAmMs > 0 && commutePmMs > 0 &&
            (commuteAmMs + commutePmMs).toDouble() / totalMs > 0.3) {
            val commutePct = ((commuteAmMs + commutePmMs) * 100 / totalMs).toInt()
            val commuteDays = eventDao.getCommuteDaysCountSuspend(since)
            val type = "ARCHETYPE_COMMUTE_LISTENER"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "commutePct" to "${commutePct}%",
                "commuteDays" to "$commuteDays"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }
        }

        val totalPlays = eventDao.getTotalPlayCountSuspend().coerceAtLeast(1)
        val totalSkips = eventDao.getTotalSkipCountSuspend()
        val skipRate = totalSkips.toDouble() / (totalPlays + totalSkips)
        if (skipRate < 0.05) {
            val skipPct = "%.1f".format(skipRate * 100)
            val type = "ARCHETYPE_COMPLETIONIST"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "skipPct" to "${skipPct}%",
                "totalPlays" to "$totalPlays"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }
        }
        if (skipRate > 0.40) {
            val skipPct = "%.1f".format(skipRate * 100)
            val type = "ARCHETYPE_CERTIFIED_SKIPPER"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "skipPct" to "${skipPct}%",
                "skipCount" to "$totalSkips"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }
        }

        val deepCuts = eventDao.getSongsWithMinPlays(50)
        if (deepCuts.isNotEmpty()) {
            val deepCutMs = deepCuts[0].totalDurationMs
            val deepCutHours = deepCutMs / 3_600_000L
            val deepCutMins = (deepCutMs % 3_600_000L) / 60_000L
            val deepCutDuration = if (deepCutHours > 0) "${deepCutHours}h ${deepCutMins}m" else "${deepCutMins}m"
            val type = "ARCHETYPE_DEEP_CUT_DIGGER"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "playCount" to "${deepCuts[0].playCount}",
                "duration" to deepCutDuration
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                imageUrl = deepCuts[0].albumArtUrl,
                copyVariant = copyVariant
            ))?.let { result += it }
        }

        val topArtists = eventDao.getTopArtistsByDurationSuspend(0L, 1)
        if (topArtists.isNotEmpty()) {
            val topMs = topArtists[0].totalDurationMs
            val allMs = eventDao.getTotalListeningTimeMsSuspend().coerceAtLeast(1L)
            if (topMs.toDouble() / allMs > 0.5) {
                val artistEntity = artistDao.findByName(topArtists[0].artist)
                val topPct = (topMs * 100 / allMs).toInt()
                val type = "ARCHETYPE_LOYAL_FAN"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "topPct" to "${topPct}%",
                    "playCount" to "${topArtists[0].playCount}"
                )
                val copy = MomentCopywriter.generate(type, topArtists[0].artist, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type, entityKey = yearMonth, triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    artistId = artistEntity?.id,
                    statLines = copy.statLines,
                    imageUrl = artistEntity?.imageUrl,
                    entityName = topArtists[0].artist,
                    copyVariant = copyVariant
                ))?.let { result += it }
            }
        }

        val newArtistsThisWeek = eventDao.getNewArtistsSinceSuspend(now - 7L * 24 * 3600 * 1000)
        if (newArtistsThisWeek >= 5) {
            val newSongsThisWeek = eventDao.getNewSongsSinceSuspend(now - 7L * 24 * 3600 * 1000)
            val type = "ARCHETYPE_EXPLORER"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "newArtists" to "$newArtistsThisWeek",
                "newSongs" to "$newSongsThisWeek"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }
        }

        // Weekend Warrior
        val fourWeeksAgo = now - 28L * 24 * 3600 * 1000
        val weekendMs = eventDao.getWeekendListeningMsSuspend(fourWeeksAgo)
        val totalFourWeekMs = eventDao.getListeningTimeSinceSuspend(fourWeeksAgo).coerceAtLeast(1L)
        if (weekendMs.toDouble() / totalFourWeekMs > 0.6) {
            val weekendPct = (weekendMs * 100 / totalFourWeekMs).toInt()
            val weekendHoursPerWeek = weekendMs / 4 / 3_600_000L
            val type = "ARCHETYPE_WEEKEND_WARRIOR"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "weekendPct" to "${weekendPct}%",
                "weekendHours" to "${weekendHoursPerWeek}h"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }
        }

        // Wide Taste
        val uniqueArtistsThisMonth = eventDao.getUniqueArtistCountSinceSuspend(since)
        val topArtistThisMonth = eventDao.getTopArtistsByDurationSuspend(since, 1)
        val totalThisMonthMs = eventDao.getListeningTimeSinceSuspend(since).coerceAtLeast(1L)
        if (topArtistThisMonth.isNotEmpty() && uniqueArtistsThisMonth >= 15) {
            val topArtistPct = (topArtistThisMonth[0].totalDurationMs * 100 / totalThisMonthMs).toInt()
            if (topArtistPct < 15) {
                val type = "ARCHETYPE_WIDE_TASTE"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "uniqueArtists" to "$uniqueArtistsThisMonth",
                    "topArtistPct" to "${topArtistPct}%"
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type, entityKey = yearMonth, triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    statLines = copy.statLines,
                    copyVariant = copyVariant
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
                val type = "ARCHETYPE_REPEAT_OFFENDER"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "top3Pct" to "${top3Pct}%",
                    "topSongLine" to "${topSong.title} \u00d7 ${topSong.playCount}"
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type, entityKey = yearMonth, triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    songId = topSong.songId,
                    statLines = copy.statLines,
                    imageUrl = topSong.albumArtUrl,
                    copyVariant = copyVariant
                ))?.let { result += it }
            }
        }

        // Album Listener — detect consecutive same-artist runs >= 5 in sessions
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
            val type = "ARCHETYPE_ALBUM_LISTENER"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "albumRuns" to "${albumRuns.size}",
                "avgPerRun" to "$avgRun"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type, entityKey = yearMonth, triggeredAt = now,
                title = copy.title,
                description = copy.description,
                songId = topSong?.songId,
                statLines = copy.statLines,
                imageUrl = topSong?.albumArtUrl,
                copyVariant = copyVariant
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
                val type = "OBSESSION_DAILY"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "playCountToday" to "${song.playCount}",
                    "allTimePlays" to "$allTimePlays"
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "${song.songId}:$todayDate",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    songId = song.songId,
                    statLines = copy.statLines,
                    imageUrl = song.albumArtUrl,
                    copyVariant = copyVariant
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
                val type = "DAILY_RITUAL"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "playCount" to "${song.playCount}",
                    "duration" to formatDuration(song.totalDurationMs)
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "${song.songId}:$detectedDate",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    songId = song.songId,
                    statLines = copy.statLines,
                    imageUrl = song.albumArtUrl,
                    copyVariant = copyVariant
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
                val type = "BREAKUP_CANDIDATE"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "skipCount" to "$skipCount",
                    "playsThisWeek" to "$playsThisWeek"
                )
                val copy = MomentCopywriter.generate(type, artistStats.artist, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "${artistStats.artist}:$weekKey",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    artistId = artistEntity?.id,
                    statLines = copy.statLines,
                    imageUrl = artistEntity?.imageUrl,
                    entityName = artistStats.artist,
                    copyVariant = copyVariant
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
                val type = "FAST_OBSESSION"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "playCount" to "${song.playCount}",
                    "ageDays" to "$ageDays",
                    "ageLine" to "$ageDays days \u00b7 #$rank all-time"
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "${song.songId}",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    songId = song.songId,
                    statLines = copy.statLines,
                    imageUrl = song.albumArtUrl,
                    copyVariant = copyVariant
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
            val type = "LONGEST_SESSION"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "sessionLabel" to label,
                "pbLabel" to "new personal best"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = "$longestMs",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
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
            val type = "QUICK_OBSESSION"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "rank" to "$rank",
                "ageLine" to "$ageDays days",
                "ageDays" to "$ageDays"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = "${song.songId}",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                songId = song.songId,
                statLines = copy.statLines,
                imageUrl = song.albumArtUrl,
                copyVariant = copyVariant
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
            val type = "DISCOVERY_WEEK"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "newArtists" to "$newArtistsCount",
                "newSongs" to "$newSongsCount"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = weekKey,
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
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
                val type = "RESURRECTION"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "gapDays" to "$gapDays",
                    "playsToday" to "${song.playCount}"
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "${song.songId}:$todayDate",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    songId = song.songId,
                    statLines = copy.statLines,
                    imageUrl = song.albumArtUrl,
                    copyVariant = copyVariant
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
                val type = "NIGHT_BINGE"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "duration" to duration,
                    "songCount" to "$songCount"
                )
                val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = dayNight.day,
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    statLines = copy.statLines,
                    copyVariant = copyVariant
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
            val type = "COMFORT_ZONE"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "top5Pct" to "$top5Pct",
                "totalPlays" to "$totalPlaysThisWeek"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = weekKey,
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                songId = topSong.songId,
                statLines = copy.statLines,
                imageUrl = topSong.albumArtUrl,
                copyVariant = copyVariant
            ))?.let { result += it }
        }
        return result
    }

    private suspend fun detectRediscovery(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val topArtistsThisWeek = eventDao.getTopArtistsByDurationSuspend(sevenDaysAgo, 1)

        for (artistStats in topArtistsThisWeek) {
            val playsThisWeek = eventDao.getArtistPlayCountSinceSuspend(artistStats.artist, sevenDaysAgo)
            if (playsThisWeek < 5) continue
            val lastPlayedBefore = eventDao.getArtistLastPlayedBeforeSuspend(artistStats.artist, sevenDaysAgo) ?: continue
            val gapDays = (now - lastPlayedBefore) / 86_400_000L
            if (gapDays >= 60) {
                val artistEntity = artistDao.findByName(artistStats.artist)
                val type = "REDISCOVERY"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "gapDays" to "$gapDays",
                    "playsThisWeek" to "$playsThisWeek"
                )
                val copy = MomentCopywriter.generate(type, artistStats.artist, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "${artistStats.artist}:$weekKey",
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    artistId = artistEntity?.id,
                    statLines = copy.statLines,
                    imageUrl = artistEntity?.imageUrl,
                    entityName = artistStats.artist,
                    copyVariant = copyVariant
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
            val type = "SLOW_BURN"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "ageDays" to "$ageDays",
                "totalPlays" to "$totalPlays"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = "${song.songId}:$weekKey",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                songId = song.songId,
                statLines = copy.statLines,
                imageUrl = song.albumArtUrl,
                copyVariant = copyVariant
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
            val type = "MARATHON_WEEK"
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "weekDuration" to duration,
                "songArtistLine" to "$songCount songs \u00b7 $artistCount artists"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = weekKey,
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
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
