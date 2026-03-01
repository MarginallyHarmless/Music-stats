package com.musicstats.app.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.dao.ArtistWithStats
import com.musicstats.app.data.dao.SongPlayStats
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.repository.MomentsRepository
import com.musicstats.app.data.repository.MusicRepository
import com.musicstats.app.service.MomentCopywriter
import com.musicstats.app.util.formatDuration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllMomentsViewModel @Inject constructor(
    private val momentsRepository: MomentsRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    val moments: StateFlow<List<Moment>> =
        momentsRepository.getAllMoments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val previewMoments: StateFlow<List<Moment>> = combine(
        musicRepository.getAllArtistsWithStats()
            .map { it.sortedByDescending { a -> a.totalDurationMs }.take(3) },
        musicRepository.getTopSongsByPlayCount(limit = 3)
    ) { artists, songs ->
        buildPreviewMoments(artists, songs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markSeen(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            momentsRepository.markSeen(id)
        }
    }

    fun markShared(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            momentsRepository.markShared(id)
        }
    }

    private fun buildPreviewMoments(
        artists: List<ArtistWithStats>,
        songs: List<SongPlayStats>
    ): List<Moment> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Moment>()

        val s0 = songs.getOrNull(0)
        val s1 = songs.getOrNull(1)
        val s2 = songs.getOrNull(2)
        val a0 = artists.getOrNull(0)
        val a1 = artists.getOrNull(1)

        // Fallback art so every card that should have a background gets one
        val songArt = s0?.albumArtUrl ?: s1?.albumArtUrl ?: s2?.albumArtUrl
        val artistArt = a0?.imageUrl ?: a1?.imageUrl

        // ── Song Play Milestones (4) ──────────────────────────────
        listOf(50, 100, 250, 500).forEachIndexed { idx, threshold ->
            val type = "SONG_PLAYS_$threshold"
            val rawStats = mapOf<String, Any>("totalDurationMs" to (threshold * 200_000L), "rank" to (idx + 1))
            val copy = MomentCopywriter.generate(type, s0?.artist ?: "The Weeknd", rawStats, 0)
            result += Moment(
                id = (-1L - idx), type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                songId = s0?.songId,
                statLines = copy.statLines,
                imageUrl = s0?.albumArtUrl ?: songArt,
                copyVariant = 0
            )
        }

        // ── Artist Hour Milestones (3) ────────────────────────────
        listOf(5, 10, 24).forEachIndexed { idx, hours ->
            val type = "ARTIST_HOURS_$hours"
            val rawStats = mapOf<String, Any>("playCount" to (hours * 120), "uniqueSongs" to (hours * 8))
            val copy = MomentCopywriter.generate(type, a0?.name ?: "The Weeknd", rawStats, 0)
            result += Moment(
                id = (-10L - idx), type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                entityName = a0?.name ?: "The Weeknd",
                statLines = copy.statLines,
                imageUrl = a0?.imageUrl ?: artistArt,
                copyVariant = 0
            )
        }

        // ── Streak Milestones (4) ─────────────────────────────────
        listOf(7, 14, 30, 100).forEachIndexed { idx, days ->
            val type = "STREAK_$days"
            val rawStats = mapOf<String, Any>("avgMinPerDay" to 47L, "uniqueSongs" to (days * 12))
            val copy = MomentCopywriter.generate(type, null, rawStats, 0)
            result += Moment(
                id = (-20L - idx), type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = 0
            )
        }

        // ── Total Hour Milestones (4) ─────────────────────────────
        listOf(24, 100, 500, 1000).forEachIndexed { idx, hours ->
            val type = "TOTAL_HOURS_$hours"
            val rawStats = mapOf<String, Any>("uniqueSongs" to (hours * 12), "uniqueArtists" to (hours / 4))
            val copy = MomentCopywriter.generate(type, null, rawStats, 0)
            result += Moment(
                id = (-30L - idx), type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = 0
            )
        }

        // ── Discovery Milestones (3) ──────────────────────────────
        listOf(100, 250, 500).forEachIndexed { idx, count ->
            val type = "SONGS_DISCOVERED_$count"
            val rawStats = mapOf<String, Any>("uniqueArtists" to (count / 5), "totalHours" to (count / 10))
            val copy = MomentCopywriter.generate(type, null, rawStats, 0)
            result += Moment(
                id = (-40L - idx), type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = 0
            )
        }

        // ── Archetypes (12) ──────────────────────────────────────

        fun archetypeMoment(id: Long, type: String, entityName: String?, rawStats: Map<String, Any>,
                            songId: Long? = null, imageUrl: String? = null): Moment {
            val copy = MomentCopywriter.generate(type, entityName, rawStats, 0)
            return Moment(
                id = id, type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                entityName = entityName,
                songId = songId,
                statLines = copy.statLines,
                imageUrl = imageUrl,
                copyVariant = 0
            )
        }

        result += archetypeMoment(-50, "ARCHETYPE_NIGHT_OWL", null,
            mapOf("nightPct" to "73%", "peakLabel" to "11pm"))
        result += archetypeMoment(-51, "ARCHETYPE_MORNING_LISTENER", null,
            mapOf("morningPct" to "61%", "peakLabel" to "7am"))
        result += archetypeMoment(-52, "ARCHETYPE_COMMUTE_LISTENER", null,
            mapOf("commutePct" to "34%", "commuteDays" to "22"))
        result += archetypeMoment(-53, "ARCHETYPE_COMPLETIONIST", null,
            mapOf("skipPct" to "2.3%", "totalPlays" to "1,840"))
        result += archetypeMoment(-54, "ARCHETYPE_CERTIFIED_SKIPPER", null,
            mapOf("skipPct" to "47.8%", "skipCount" to "312"))
        result += archetypeMoment(-55, "ARCHETYPE_DEEP_CUT_DIGGER", null,
            mapOf("playCount" to "${s0?.playCount ?: 63}", "duration" to formatDuration(s0?.totalDurationMs ?: 19_200_000L)),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += archetypeMoment(-56, "ARCHETYPE_LOYAL_FAN", a0?.name,
            mapOf("topPct" to "54%", "playCount" to "${a0?.playCount ?: 420}"),
            imageUrl = a0?.imageUrl ?: artistArt)
        result += archetypeMoment(-57, "ARCHETYPE_EXPLORER", null,
            mapOf("newArtists" to "12", "newSongs" to "31"))
        result += archetypeMoment(-58, "ARCHETYPE_WEEKEND_WARRIOR", null,
            mapOf("weekendPct" to "68%", "weekendHours" to "4.2h"))
        result += archetypeMoment(-59, "ARCHETYPE_WIDE_TASTE", null,
            mapOf("uniqueArtists" to "47", "topArtistPct" to "8%"))
        result += archetypeMoment(-60, "ARCHETYPE_REPEAT_OFFENDER", null,
            mapOf("top3Pct" to "43%", "topSongLine" to "${s0?.title ?: "that song"} × ${s0?.playCount ?: 28}"),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += archetypeMoment(-61, "ARCHETYPE_ALBUM_LISTENER", null,
            mapOf("albumRuns" to "7", "avgPerRun" to "9"),
            imageUrl = a0?.imageUrl ?: artistArt)

        // ── Behavioral (13) ──────────────────────────────────────

        fun behavioralMoment(id: Long, type: String, entityName: String?, rawStats: Map<String, Any>,
                             songId: Long? = null, imageUrl: String? = null): Moment {
            val copy = MomentCopywriter.generate(type, entityName, rawStats, 0)
            return Moment(
                id = id, type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                entityName = entityName,
                songId = songId,
                statLines = copy.statLines,
                imageUrl = imageUrl,
                copyVariant = 0
            )
        }

        val dailyPlays = (s0?.playCount?.div(7))?.coerceAtLeast(5) ?: 8
        result += behavioralMoment(-70, "OBSESSION_DAILY", null,
            mapOf("playCountToday" to "$dailyPlays", "allTimePlays" to "${s0?.playCount ?: 312}"),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += behavioralMoment(-71, "DAILY_RITUAL", null,
            mapOf("playCount" to "${s0?.playCount ?: 87}", "duration" to formatDuration(s0?.totalDurationMs ?: 18_000_000L)),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += behavioralMoment(-72, "BREAKUP_CANDIDATE", a1?.name,
            mapOf("skipCount" to "12", "playsThisWeek" to "3"),
            imageUrl = a1?.imageUrl ?: artistArt)
        result += behavioralMoment(-73, "FAST_OBSESSION", null,
            mapOf("playCount" to "${s1?.playCount ?: 24}", "ageDays" to "12", "ageLine" to "12 days · #3 all-time"),
            songId = s1?.songId, imageUrl = s1?.albumArtUrl ?: songArt)
        result += behavioralMoment(-74, "LONGEST_SESSION", null,
            mapOf("sessionLabel" to "3h 12m", "pbLabel" to "new personal best"))
        result += behavioralMoment(-75, "QUICK_OBSESSION", null,
            mapOf("rank" to "4", "ageLine" to "5 days", "ageDays" to "5"),
            songId = s1?.songId, imageUrl = s1?.albumArtUrl ?: songArt)
        result += behavioralMoment(-76, "DISCOVERY_WEEK", null,
            mapOf("newArtists" to "9", "newSongs" to "23"))
        result += behavioralMoment(-77, "RESURRECTION", null,
            mapOf("gapDays" to "45", "playsToday" to "6"),
            songId = s2?.songId, imageUrl = s2?.albumArtUrl ?: songArt)
        result += behavioralMoment(-78, "NIGHT_BINGE", null,
            mapOf("duration" to "2h 18m", "songCount" to "34"))
        result += behavioralMoment(-79, "COMFORT_ZONE", null,
            mapOf("top5Pct" to "84", "totalPlays" to "62"),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += behavioralMoment(-80, "REDISCOVERY", a1?.name,
            mapOf("gapDays" to "73", "playsThisWeek" to "8"),
            imageUrl = a1?.imageUrl ?: artistArt)
        result += behavioralMoment(-81, "SLOW_BURN", null,
            mapOf("ageDays" to "91", "totalPlays" to "7"),
            songId = s2?.songId, imageUrl = s2?.albumArtUrl ?: songArt)
        result += behavioralMoment(-82, "MARATHON_WEEK", null,
            mapOf("weekDuration" to "18h 40m", "songArtistLine" to "247 songs · 31 artists"))

        // ── Narrative Moments (12) ─────────────────────────────────

        fun narrativeMoment(id: Long, type: String, entityName: String?, rawStats: Map<String, Any>,
                            songId: Long? = null, imageUrl: String? = null): Moment {
            val copy = MomentCopywriter.generate(type, entityName, rawStats, 0)
            return Moment(
                id = id, type = type, entityKey = "preview",
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                entityName = entityName,
                songId = songId,
                statLines = copy.statLines,
                imageUrl = imageUrl,
                copyVariant = 0
            )
        }

        result += narrativeMoment(-90, "NARRATIVE_ORIGIN_STORY", a0?.name ?: "Radiohead",
            mapOf("firstSong" to (s0?.title ?: "Everything In Its Right Place"), "rank" to 3, "daysAgo" to 47, "uniqueSongs" to 9, "totalHours" to 18L),
            imageUrl = a0?.imageUrl ?: artistArt)
        result += narrativeMoment(-91, "NARRATIVE_GATEWAY", a0?.name ?: "Arctic Monkeys",
            mapOf("firstSong" to (s0?.title ?: "Do I Wanna Know?"), "topSong" to (s1?.title ?: "Fluorescent Adolescent"), "ratio" to 3, "topSongLine" to "${s1?.title ?: "Fluorescent Adolescent"} (${s1?.playCount ?: 84} plays)"),
            imageUrl = a0?.imageUrl ?: artistArt)
        result += narrativeMoment(-92, "NARRATIVE_COLLECTION", a0?.name ?: "Arctic Monkeys",
            mapOf("songCount" to 23, "firstSong" to (s0?.title ?: "Do I Wanna Know?"), "totalHours" to 31L, "daysAgo" to 120),
            imageUrl = a0?.imageUrl ?: artistArt)
        result += narrativeMoment(-93, "NARRATIVE_TAKEOVER", s1?.title ?: "Espresso",
            mapOf("rank" to 4, "playCount" to (s1?.playCount ?: 67), "daysAgo" to 21),
            songId = s1?.songId, imageUrl = s1?.albumArtUrl ?: songArt)
        result += narrativeMoment(-94, "NARRATIVE_SLOW_BUILD", s0?.title ?: "a song",
            mapOf("w1" to 2, "w2" to 5, "w3" to 11, "w4" to 19, "trajectory" to "2 → 5 → 11 → 19 plays"),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += narrativeMoment(-95, "NARRATIVE_BINGE_AND_FADE", s2?.title ?: "Heat Waves",
            mapOf("bingePlays" to 47, "fadePlays" to 2),
            songId = s2?.songId, imageUrl = s2?.albumArtUrl ?: songArt)
        result += narrativeMoment(-96, "NARRATIVE_FULL_CIRCLE", a1?.name ?: "Daft Punk",
            mapOf("gapDays" to 73, "playsThisWeek" to 12),
            imageUrl = a1?.imageUrl ?: artistArt)
        result += narrativeMoment(-97, "NARRATIVE_ONE_THAT_GOT_AWAY", a1?.name ?: "Tyler, The Creator",
            mapOf("peakPlays" to 34, "peakMonth" to "February", "currentPlays" to 2),
            imageUrl = a1?.imageUrl ?: artistArt)
        result += narrativeMoment(-98, "NARRATIVE_SOUNDTRACK", s0?.title ?: "Blinding Lights",
            mapOf("distinctDays" to 47, "monthSpan" to 6, "playCount" to (s0?.playCount ?: 89)),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += narrativeMoment(-99, "NARRATIVE_RABBIT_HOLE", a0?.name ?: "Radiohead",
            mapOf("songCount" to 14, "duration" to "58m", "noInterrupt" to "no interruptions"),
            imageUrl = a0?.imageUrl ?: artistArt)
        result += narrativeMoment(-100, "NARRATIVE_NIGHT_AND_DAY", null,
            mapOf("dayArtist" to (a0?.name ?: "Taylor Swift"), "nightArtist" to (a1?.name ?: "Bon Iver"),
                "dayLine" to "${a0?.name ?: "Taylor Swift"} (12h)", "nightLine" to "${a1?.name ?: "Bon Iver"} (7h)"))
        result += narrativeMoment(-101, "NARRATIVE_PARALLEL_LIVES", null,
            mapOf("artist1" to (a0?.name ?: "Radiohead"), "artist2" to (a1?.name ?: "Coldplay"),
                "artist1Line" to "${a0?.name ?: "Radiohead"}: 8h", "artist2Line" to "${a1?.name ?: "Coldplay"}: 5h",
                "overlap" to "0 shared sessions"))

        return result
    }
}
