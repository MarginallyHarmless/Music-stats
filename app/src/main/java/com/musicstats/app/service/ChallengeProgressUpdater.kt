package com.musicstats.app.service

import com.musicstats.app.data.dao.ChallengeDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.model.Challenge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChallengeProgressUpdater @Inject constructor(
    private val challengeDao: ChallengeDao,
    private val eventDao: ListeningEventDao
) {
    suspend fun updateAll() {
        val weekStart = WeekUtils.currentWeekStart()
        val weekEnd = WeekUtils.weekEnd(weekStart)
        val challenges = challengeDao.getChallengesForWeekSuspend(weekStart)

        for (challenge in challenges) {
            if (challenge.completed) continue
            val currentValue = calculateProgress(challenge, weekStart, weekEnd)
            val completed = currentValue >= challenge.targetValue
            val completedAt = if (completed) System.currentTimeMillis() else null
            challengeDao.updateProgress(challenge.id, currentValue, completed, completedAt)
        }
    }

    private suspend fun calculateProgress(challenge: Challenge, weekStart: Long, weekEnd: Long): Float {
        val meta = challenge.metadata?.let {
            runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
        }

        return when (challenge.type) {
            ChallengeType.DISCOVERY_STRETCH.name -> {
                eventDao.getNewSongsDiscoveredBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.GENRE_EXPLORER.name -> {
                val dominantArtist = meta?.get("dominantArtist")?.jsonPrimitive?.content ?: return 0f
                val totalMs = eventDao.getListeningTimeBetweenSuspend(weekStart, weekEnd)
                val topArtists = eventDao.getTopArtistsByPlayCountInPeriod(weekStart, weekEnd, 100)
                val dominantMs = topArtists.filter { it.artist == dominantArtist }.sumOf { it.totalDurationMs }
                (totalMs - dominantMs).toFloat()
            }

            ChallengeType.ARTIST_DEEP_DIVE.name -> {
                val artist = meta?.get("artist")?.jsonPrimitive?.content ?: return 0f
                eventDao.getArtistPlayCountSinceSuspend(artist, weekStart).toFloat()
            }

            ChallengeType.REDISCOVERY.name, ChallengeType.OLD_FAVORITE.name -> {
                val songId = meta?.get("songId")?.jsonPrimitive?.long ?: return 0f
                eventDao.getSongPlayCountSinceSuspend(songId, weekStart).toFloat()
            }

            ChallengeType.CONSISTENCY.name -> {
                eventDao.getDaysWithListeningBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.TIME_STRETCH.name -> {
                eventDao.getListeningTimeBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.NO_SKIP_RUN.name -> {
                eventDao.getPlayCountBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.NIGHT_OWL_EARLY_BIRD.name -> {
                val direction = meta?.get("direction")?.jsonPrimitive?.content ?: return 0f
                val hourly = eventDao.getHourlyListeningBetweenSuspend(weekStart, weekEnd)
                val targetMs = if (direction == "morning") {
                    hourly.filter { it.hour in 6..11 }.sumOf { it.totalDurationMs }
                } else {
                    hourly.filter { it.hour in 20..23 }.sumOf { it.totalDurationMs }
                }
                targetMs.toFloat()
            }

            ChallengeType.MARATHON_SESSION.name -> {
                eventDao.getLongestSessionBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.ARTIST_VARIETY.name -> {
                eventDao.getUniqueArtistCountBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.LOYALTY_TEST.name -> {
                val songId = meta?.get("songId")?.jsonPrimitive?.long ?: return 0f
                eventDao.getSongPlayCountSinceSuspend(songId, weekStart).toFloat()
            }

            ChallengeType.SOURCE_SWAP.name -> {
                val currentApp = meta?.get("currentApp")?.jsonPrimitive?.content ?: return 0f
                val apps = eventDao.getDistinctSourceAppsBetweenSuspend(weekStart, weekEnd)
                if (apps.any { it != currentApp }) 1f else 0f
            }

            ChallengeType.WEEKEND_WARRIOR.name -> {
                eventDao.getWeekendListeningMsBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            else -> 0f
        }
    }
}
