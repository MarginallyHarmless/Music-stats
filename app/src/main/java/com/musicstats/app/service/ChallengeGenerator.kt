package com.musicstats.app.service

import com.musicstats.app.data.dao.ChallengeDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.model.Challenge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChallengeGenerator @Inject constructor(
    private val eventDao: ListeningEventDao,
    private val challengeDao: ChallengeDao
) {
    suspend fun generateWeeklyChallenges(): List<Challenge> {
        val weekStart = WeekUtils.currentWeekStart()

        if (challengeDao.countForWeek(weekStart) > 0) {
            DebugLog.log(DebugEventType.STATE, "Challenges: already generated for this week")
            return emptyList()
        }

        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        if (totalMs < MomentPriority.GATE_MS) {
            DebugLog.log(DebugEventType.STATE, "Challenges: gate not met (${totalMs / 3_600_000f}h / ${MomentPriority.GATE_HOURS}h)")
            return emptyList()
        }

        val prevWeekStart = WeekUtils.previousWeekStart()
        val preWeekDataCount = eventDao.getPlayCountBetweenSuspend(prevWeekStart, weekStart)
        if (preWeekDataCount == 0) {
            DebugLog.log(DebugEventType.STATE, "Challenges: need at least 1 week of data (0 plays last week)")
            return emptyList()
        }

        val lastWeekEnd = weekStart
        val lastWeekStart = prevWeekStart

        DebugLog.log(DebugEventType.STATE, "Challenges: running 14 generators against last week data")

        val cooldownTypes = challengeDao.getTypesForWeek(prevWeekStart).toSet()

        val allResults = listOf(
            "DISCOVERY_STRETCH" to generateDiscoveryStretch(lastWeekStart, lastWeekEnd, weekStart),
            "GENRE_EXPLORER" to generateGenreExplorer(lastWeekStart, lastWeekEnd, weekStart),
            "ARTIST_DEEP_DIVE" to generateArtistDeepDive(lastWeekStart, lastWeekEnd, weekStart),
            "REDISCOVERY" to generateRediscovery(lastWeekStart, lastWeekEnd, weekStart),
            "CONSISTENCY" to generateConsistency(lastWeekStart, lastWeekEnd, weekStart),
            "TIME_STRETCH" to generateTimeStretch(lastWeekStart, lastWeekEnd, weekStart),
            "NO_SKIP_RUN" to generateNoSkipRun(lastWeekStart, lastWeekEnd, weekStart),
            "NIGHT_OWL" to generateNightOwlEarlyBird(lastWeekStart, lastWeekEnd, weekStart),
            "MARATHON" to generateMarathonSession(lastWeekStart, lastWeekEnd, weekStart),
            "ARTIST_VARIETY" to generateArtistVariety(lastWeekStart, lastWeekEnd, weekStart),
            "LOYALTY_TEST" to generateLoyaltyTest(lastWeekStart, lastWeekEnd, weekStart),
            "SOURCE_SWAP" to generateSourceSwap(lastWeekStart, lastWeekEnd, weekStart),
            "OLD_FAVORITE" to generateOldFavorite(lastWeekStart, lastWeekEnd, weekStart),
            "WEEKEND_WARRIOR" to generateWeekendWarrior(lastWeekStart, lastWeekEnd, weekStart),
        )

        val hit = allResults.filter { it.second != null }.map { it.first }
        val miss = allResults.filter { it.second == null }.map { it.first }
        DebugLog.log(DebugEventType.STATE, "Challenges: ${hit.size} hit [${hit.joinToString()}], ${miss.size} miss")

        val candidates = allResults.mapNotNull { it.second }.filter { it.type !in cooldownTypes }

        if (candidates.isEmpty()) {
            DebugLog.log(DebugEventType.STATE, "Challenges: 0 candidates after cooldown filter")
            return emptyList()
        }

        val count = if (candidates.size >= 3) 3 else candidates.size.coerceAtMost(2)
        val selected = candidates.shuffled().take(count)

        selected.forEach { challengeDao.insert(it) }
        return selected
    }

    private suspend fun generateDiscoveryStretch(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val discovered = eventDao.getNewSongsDiscoveredBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (discovered < 1) return null
        val target = (discovered * 2).coerceAtLeast(4).toFloat()
        return Challenge(
            type = ChallengeType.DISCOVERY_STRETCH.name,
            weekStart = weekStart,
            title = "Discovery stretch",
            description = "You found $discovered new songs last week. Discover ${target.toInt()} this week.",
            targetValue = target,
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateGenreExplorer(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val topArtists = eventDao.getTopArtistsByPlayCountInPeriod(lastWeekStart, lastWeekEnd, 5)
        val totalPlays = eventDao.getPlayCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (totalPlays < 10 || topArtists.isEmpty()) return null
        val topArtistPlays = topArtists[0].playCount
        val concentration = topArtistPlays.toFloat() / totalPlays
        if (concentration < 0.5f) return null
        val targetMinutes = 20
        return Challenge(
            type = ChallengeType.GENRE_EXPLORER.name,
            weekStart = weekStart,
            title = "Branch out",
            description = "${(concentration * 100).toInt()}% of last week was ${topArtists[0].artist}. Listen to $targetMinutes min of someone new.",
            targetValue = (targetMinutes * 60 * 1000).toFloat(),
            generatedAt = System.currentTimeMillis(),
            metadata = """{"dominantArtist":"${topArtists[0].artist}"}"""
        )
    }

    private suspend fun generateArtistDeepDive(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val shallow = eventDao.getShallowArtistsInPeriodSuspend(lastWeekStart, lastWeekEnd, 3, 5)
        if (shallow.isEmpty()) return null
        val pick = shallow[0]
        return Challenge(
            type = ChallengeType.ARTIST_DEEP_DIVE.name,
            weekStart = weekStart,
            title = "Deep dive",
            description = "You played ${pick.artist} ${pick.playCount} times last week. Give them a real chance — 5 plays.",
            targetValue = 5f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"artist":"${pick.artist}"}"""
        )
    }

    private suspend fun generateRediscovery(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val thirtyDaysAgo = lastWeekStart - 30L * 24 * 3600 * 1000
        val forgotten = eventDao.getSongsNotPlayedSince(10, thirtyDaysAgo, 5)
        if (forgotten.isEmpty()) return null
        val pick = forgotten.random()
        return Challenge(
            type = ChallengeType.REDISCOVERY.name,
            weekStart = weekStart,
            title = "Rediscovery",
            description = "You used to love ${pick.title} by ${pick.artist} (${pick.playCount} plays). Revisit it.",
            targetValue = 1f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"songId":${pick.songId},"songTitle":"${pick.title}","artist":"${pick.artist}"}"""
        )
    }

    private suspend fun generateConsistency(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val daysActive = eventDao.getDaysWithListeningBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (daysActive >= 6) return null
        if (daysActive < 2) return null
        return Challenge(
            type = ChallengeType.CONSISTENCY.name,
            weekStart = weekStart,
            title = "Every day counts",
            description = "You listened on $daysActive days last week. Hit all 7 this week.",
            targetValue = 7f,
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateTimeStretch(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val avgDailyMs = eventDao.getAvgDailyListeningMsBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (avgDailyMs < 5 * 60 * 1000) return null
        val avgMinutes = avgDailyMs / 60000
        val targetMinutes = (avgMinutes * 1.5).toLong().coerceAtLeast(avgMinutes + 15)
        return Challenge(
            type = ChallengeType.TIME_STRETCH.name,
            weekStart = weekStart,
            title = "Time stretch",
            description = "You averaged ${avgMinutes}min/day last week. Push for ${targetMinutes}min/day.",
            targetValue = (targetMinutes * 60 * 1000 * 7).toFloat(),
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateNoSkipRun(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val totalEvents = eventDao.getTotalEventCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        val skips = eventDao.getSkipCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (totalEvents < 20) return null
        val skipRate = skips.toFloat() / totalEvents
        if (skipRate < 0.25f) return null
        return Challenge(
            type = ChallengeType.NO_SKIP_RUN.name,
            weekStart = weekStart,
            title = "No-skip run",
            description = "You skipped ${(skipRate * 100).toInt()}% of songs last week. Try a no-skip session — 10 songs straight.",
            targetValue = 10f,
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateNightOwlEarlyBird(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val hourly = eventDao.getHourlyListeningBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (hourly.isEmpty()) return null
        val totalMs = hourly.sumOf { it.totalDurationMs }
        if (totalMs < 30 * 60 * 1000) return null

        val morningMs = hourly.filter { it.hour in 6..11 }.sumOf { it.totalDurationMs }
        val eveningMs = hourly.filter { it.hour in 20..23 }.sumOf { it.totalDurationMs }

        return when {
            eveningMs > totalMs * 0.6 -> Challenge(
                type = ChallengeType.NIGHT_OWL_EARLY_BIRD.name,
                weekStart = weekStart,
                title = "Early bird",
                description = "All your listening was after 8pm last week. Try a morning session before noon.",
                targetValue = (15 * 60 * 1000).toFloat(),
                generatedAt = System.currentTimeMillis(),
                metadata = """{"direction":"morning"}"""
            )
            morningMs > totalMs * 0.6 -> Challenge(
                type = ChallengeType.NIGHT_OWL_EARLY_BIRD.name,
                weekStart = weekStart,
                title = "Night owl",
                description = "Most of your listening was before noon last week. Try an evening session after 8pm.",
                targetValue = (15 * 60 * 1000).toFloat(),
                generatedAt = System.currentTimeMillis(),
                metadata = """{"direction":"evening"}"""
            )
            else -> null
        }
    }

    private suspend fun generateMarathonSession(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val longestMs = eventDao.getLongestSessionBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (longestMs < 10 * 60 * 1000) return null
        val longestMin = longestMs / 60000
        val targetMin = (longestMin * 1.5).toLong().coerceAtLeast(longestMin + 10).coerceAtMost(120)
        return Challenge(
            type = ChallengeType.MARATHON_SESSION.name,
            weekStart = weekStart,
            title = "Marathon session",
            description = "Your longest session was ${longestMin}min. Go for ${targetMin}min.",
            targetValue = (targetMin * 60 * 1000).toFloat(),
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateArtistVariety(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val uniqueArtists = eventDao.getUniqueArtistCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (uniqueArtists < 3 || uniqueArtists > 20) return null
        val target = (uniqueArtists * 2).coerceAtLeast(8)
        return Challenge(
            type = ChallengeType.ARTIST_VARIETY.name,
            weekStart = weekStart,
            title = "Artist variety",
            description = "You listened to $uniqueArtists artists last week. Explore $target different ones this week.",
            targetValue = target.toFloat(),
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateLoyaltyTest(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val topSongs = eventDao.getTopSongsInPeriodSuspend(lastWeekStart, lastWeekEnd, 1)
        if (topSongs.isEmpty()) return null
        val top = topSongs[0]
        if (top.playCount < 5) return null
        val target = (top.playCount * 1.5).toInt().coerceAtLeast(top.playCount + 3)
        return Challenge(
            type = ChallengeType.LOYALTY_TEST.name,
            weekStart = weekStart,
            title = "Loyalty test",
            description = "${top.title} is your current obsession at ${top.playCount} plays. Can you hit $target?",
            targetValue = target.toFloat(),
            generatedAt = System.currentTimeMillis(),
            metadata = """{"songId":${top.songId},"songTitle":"${top.title}","artist":"${top.artist}"}"""
        )
    }

    private suspend fun generateSourceSwap(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val apps = eventDao.getDistinctSourceAppsBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (apps.size != 1) return null
        val appName = apps[0].substringAfterLast('.').replaceFirstChar { it.uppercase() }
        return Challenge(
            type = ChallengeType.SOURCE_SWAP.name,
            weekStart = weekStart,
            title = "Source swap",
            description = "You've only used $appName. Try discovering something on a different app.",
            targetValue = 1f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"currentApp":"${apps[0]}"}"""
        )
    }

    private suspend fun generateOldFavorite(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val thirtyDaysAgo = lastWeekStart - 30L * 24 * 3600 * 1000
        val forgotten = eventDao.getSongsNotPlayedSince(20, thirtyDaysAgo, 10)
        if (forgotten.isEmpty()) return null
        val pick = forgotten.random()
        return Challenge(
            type = ChallengeType.OLD_FAVORITE.name,
            weekStart = weekStart,
            title = "Old favorite",
            description = "You used to love ${pick.title} (${pick.playCount} plays). It's been a while — play it again.",
            targetValue = 1f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"songId":${pick.songId},"songTitle":"${pick.title}","artist":"${pick.artist}"}"""
        )
    }

    private suspend fun generateWeekendWarrior(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val weekendMs = eventDao.getWeekendListeningMsBetweenSuspend(lastWeekStart, lastWeekEnd)
        val weekdayMs = eventDao.getWeekdayListeningMsBetweenSuspend(lastWeekStart, lastWeekEnd)
        val totalMs = weekendMs + weekdayMs
        if (totalMs < 30 * 60 * 1000) return null
        val weekendPct = weekendMs.toFloat() / totalMs
        if (weekendPct > 0.2f) return null
        return Challenge(
            type = ChallengeType.WEEKEND_WARRIOR.name,
            weekStart = weekStart,
            title = "Weekend warrior",
            description = "You barely listen on weekends. Hit 30 min on Saturday or Sunday.",
            targetValue = (30 * 60 * 1000).toFloat(),
            generatedAt = System.currentTimeMillis()
        )
    }
}
