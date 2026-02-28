package com.musicstats.app.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.dao.ArtistWithStats
import com.musicstats.app.data.dao.SongPlayStats
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.repository.MomentsRepository
import com.musicstats.app.data.repository.MusicRepository
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

        // ── Song Play Milestones (6) ──────────────────────────────
        listOf(10, 25, 50, 100, 250, 500).forEachIndexed { idx, threshold ->
            result += Moment(id = (-1L - idx), type = "SONG_PLAYS_$threshold", entityKey = "preview",
                triggeredAt = now,
                title = "$threshold plays",
                description = "You've played ${s0?.title ?: "Blinding Lights"} $threshold times",
                songId = s0?.songId,
                statLines = listOf("${formatDuration((threshold * 200_000L))} total", "#${idx + 1} all-time"),
                imageUrl = s0?.albumArtUrl)
        }

        // ── Artist Hour Milestones (4) ────────────────────────────
        listOf(1, 5, 10, 24).forEachIndexed { idx, hours ->
            val label = if (hours == 1) "1 hour" else "$hours hours"
            result += Moment(id = (-10L - idx), type = "ARTIST_HOURS_$hours", entityKey = "preview",
                triggeredAt = now,
                title = "$label of ${a0?.name ?: "The Weeknd"}",
                description = "You've spent $label listening to ${a0?.name ?: "The Weeknd"}",
                entityName = a0?.name ?: "The Weeknd",
                statLines = listOf("${hours * 120} plays", "${hours * 8} songs heard"),
                imageUrl = a0?.imageUrl)
        }

        // ── Streak Milestones (5) ─────────────────────────────────
        listOf(3, 7, 14, 30, 100).forEachIndexed { idx, days ->
            result += Moment(id = (-20L - idx), type = "STREAK_$days", entityKey = "preview",
                triggeredAt = now,
                title = "$days-day streak",
                description = "$days days in a row — you're on fire",
                statLines = listOf("avg 47min/day", "${days * 12} songs this streak"))
        }

        // ── Total Hour Milestones (4) ─────────────────────────────
        listOf(24, 100, 500, 1000).forEachIndexed { idx, hours ->
            result += Moment(id = (-30L - idx), type = "TOTAL_HOURS_$hours", entityKey = "preview",
                triggeredAt = now,
                title = "$hours hours",
                description = "You've listened to ${hours}h of music in total",
                statLines = listOf("${hours * 12} unique songs", "${hours / 4} artists"))
        }

        // ── Discovery Milestones (4) ──────────────────────────────
        listOf(50, 100, 250, 500).forEachIndexed { idx, count ->
            result += Moment(id = (-40L - idx), type = "SONGS_DISCOVERED_$count", entityKey = "preview",
                triggeredAt = now,
                title = "$count songs",
                description = "You've discovered $count unique songs",
                statLines = listOf("from ${count / 5} artists", "${count / 10}h of music"))
        }

        // ── Archetypes (12) ──────────────────────────────────────
        result += Moment(id = -50, type = "ARCHETYPE_NIGHT_OWL", entityKey = "preview",
            triggeredAt = now, title = "Night Owl",
            description = "You do most of your listening after 10pm",
            statLines = listOf("73% of your listening", "peak: 11pm"))
        result += Moment(id = -51, type = "ARCHETYPE_MORNING_LISTENER", entityKey = "preview",
            triggeredAt = now, title = "Morning Listener",
            description = "You do most of your listening before 9am",
            statLines = listOf("61% of your listening", "avg start: 7am"))
        result += Moment(id = -52, type = "ARCHETYPE_COMMUTE_LISTENER", entityKey = "preview",
            triggeredAt = now, title = "Commute Listener",
            description = "Your listening peaks at 7–9am and 5–7pm",
            statLines = listOf("34% during commute hours", "22 commute sessions"))
        result += Moment(id = -53, type = "ARCHETYPE_COMPLETIONIST", entityKey = "preview",
            triggeredAt = now, title = "Completionist",
            description = "You skip less than 5% of songs — truly dedicated",
            statLines = listOf("2.3% skip rate", "1,840 plays"))
        result += Moment(id = -54, type = "ARCHETYPE_CERTIFIED_SKIPPER", entityKey = "preview",
            triggeredAt = now, title = "Certified Skipper",
            description = "You skip more than 40% of songs. Nothing is good enough.",
            statLines = listOf("47.8% skip rate", "312 skips"))
        result += Moment(id = -55, type = "ARCHETYPE_DEEP_CUT_DIGGER", entityKey = "preview",
            triggeredAt = now, title = "Deep Cut Digger",
            description = "You've listened to ${s0?.title ?: "that song"} over 50 times",
            songId = s0?.songId,
            statLines = listOf("${s0?.playCount ?: 63} plays", "${formatDuration((s0?.totalDurationMs ?: 19_200_000L))} total"),
            imageUrl = s0?.albumArtUrl)
        result += Moment(id = -56, type = "ARCHETYPE_LOYAL_FAN", entityKey = "preview",
            triggeredAt = now, title = "Loyal Fan",
            description = "Over 50% of your listening is ${a0?.name ?: "that artist"}",
            entityName = a0?.name,
            statLines = listOf("54% of listening", "${a0?.playCount ?: 420} plays"),
            imageUrl = a0?.imageUrl)
        result += Moment(id = -57, type = "ARCHETYPE_EXPLORER", entityKey = "preview",
            triggeredAt = now, title = "Explorer",
            description = "You discovered 12 new artists this week",
            statLines = listOf("12 new artists", "31 new songs"))
        result += Moment(id = -58, type = "ARCHETYPE_WEEKEND_WARRIOR", entityKey = "preview",
            triggeredAt = now, title = "Weekend Warrior",
            description = "Most of your listening happens on weekends",
            statLines = listOf("68% on weekends", "4.2h on weekends/week"))
        result += Moment(id = -59, type = "ARCHETYPE_WIDE_TASTE", entityKey = "preview",
            triggeredAt = now, title = "Wide Taste",
            description = "No single artist dominates your listening",
            statLines = listOf("47 artists this month", "top artist: 8%"))
        result += Moment(id = -60, type = "ARCHETYPE_REPEAT_OFFENDER", entityKey = "preview",
            triggeredAt = now, title = "Repeat Offender",
            description = "You found your songs. You're not letting go.",
            songId = s0?.songId,
            statLines = listOf("top 3 songs: 43% of plays", "${s0?.title ?: "that song"} × ${s0?.playCount ?: 28}"),
            imageUrl = s0?.albumArtUrl)
        result += Moment(id = -61, type = "ARCHETYPE_ALBUM_LISTENER", entityKey = "preview",
            triggeredAt = now, title = "Album Listener",
            description = "You don't shuffle. You commit.",
            statLines = listOf("7 album runs this month", "avg 9 songs per run"),
            imageUrl = a0?.imageUrl)

        // ── Behavioral (13) ──────────────────────────────────────
        val dailyPlays = (s0?.playCount?.div(7))?.coerceAtLeast(5) ?: 8
        result += Moment(id = -70, type = "OBSESSION_DAILY", entityKey = "preview",
            triggeredAt = now,
            title = "${dailyPlays}x in one day",
            description = "You played ${s0?.title ?: "that song"} $dailyPlays times today. Are you okay?",
            songId = s0?.songId,
            statLines = listOf("$dailyPlays plays today", "${s0?.playCount ?: 312} all-time"),
            imageUrl = s0?.albumArtUrl)
        result += Moment(id = -71, type = "DAILY_RITUAL", entityKey = "preview",
            triggeredAt = now,
            title = "Daily ritual",
            description = "You've listened to ${s0?.title ?: "that song"} every day for 7 days",
            songId = s0?.songId,
            statLines = listOf("${s0?.playCount ?: 87} all-time plays", "${formatDuration(s0?.totalDurationMs ?: 18_000_000L)} total"),
            imageUrl = s0?.albumArtUrl)
        result += Moment(id = -72, type = "BREAKUP_CANDIDATE", entityKey = "preview",
            triggeredAt = now,
            title = "Maybe break up?",
            description = "You've skipped ${a1?.name ?: "that artist"} 12 times this week",
            entityName = a1?.name,
            statLines = listOf("12 skips this week", "3 plays this week"),
            imageUrl = a1?.imageUrl)
        result += Moment(id = -73, type = "FAST_OBSESSION", entityKey = "preview",
            triggeredAt = now,
            title = "${s1?.playCount ?: 24} plays in 12 days",
            description = "${s1?.title ?: "that song"} came into your life 12 days ago. You've played it ${s1?.playCount ?: 24} times.",
            songId = s1?.songId,
            statLines = listOf("${s1?.playCount ?: 24} plays", "12 days · #3 all-time"),
            imageUrl = s1?.albumArtUrl)
        result += Moment(id = -74, type = "LONGEST_SESSION", entityKey = "preview",
            triggeredAt = now,
            title = "New record: 3h 12m",
            description = "New personal best: 3h 12m in one sitting",
            statLines = listOf("3h 12m", "47 songs"))
        result += Moment(id = -75, type = "QUICK_OBSESSION", entityKey = "preview",
            triggeredAt = now,
            title = "Fast obsession",
            description = "You discovered ${s1?.title ?: "that song"} 5 days ago. It's already in your top 5.",
            songId = s1?.songId,
            statLines = listOf("#4 all-time", "5 days since discovered"),
            imageUrl = s1?.albumArtUrl)
        result += Moment(id = -76, type = "DISCOVERY_WEEK", entityKey = "preview",
            triggeredAt = now,
            title = "9 new artists",
            description = "You discovered 9 new artists this week",
            statLines = listOf("9 new artists", "23 new songs"))
        result += Moment(id = -77, type = "RESURRECTION", entityKey = "preview",
            triggeredAt = now,
            title = "It's back",
            description = "${s2?.title ?: "that song"} went quiet for 45 days. Today it's all you're playing.",
            songId = s2?.songId,
            statLines = listOf("45 days away", "6 plays today"),
            imageUrl = s2?.albumArtUrl)
        result += Moment(id = -78, type = "NIGHT_BINGE", entityKey = "preview",
            triggeredAt = now,
            title = "Night binge",
            description = "You listened for 2h 18m after midnight",
            statLines = listOf("2h 18m after midnight", "34 songs"))
        result += Moment(id = -79, type = "COMFORT_ZONE", entityKey = "preview",
            triggeredAt = now,
            title = "Comfort zone",
            description = "Your top 5 songs made up 84% of your listening this week",
            songId = s0?.songId,
            statLines = listOf("84% from 5 songs", "62 plays this week"),
            imageUrl = s0?.albumArtUrl)
        result += Moment(id = -80, type = "REDISCOVERY", entityKey = "preview",
            triggeredAt = now,
            title = "You're back",
            description = "You hadn't played ${a1?.name ?: "that artist"} in 73 days. Welcome back.",
            entityName = a1?.name,
            statLines = listOf("73 days away", "8 plays this week"),
            imageUrl = a1?.imageUrl)
        result += Moment(id = -81, type = "SLOW_BURN", entityKey = "preview",
            triggeredAt = now,
            title = "Slow burn",
            description = "${s2?.title ?: "that song"} has been in your library for 91 days. It just clicked.",
            songId = s2?.songId,
            statLines = listOf("91 days to click", "7 plays now"),
            imageUrl = s2?.albumArtUrl)
        result += Moment(id = -82, type = "MARATHON_WEEK", entityKey = "preview",
            triggeredAt = now,
            title = "Marathon week",
            description = "New record: 18h 40m this week",
            statLines = listOf("18h 40m this week", "247 songs · 31 artists"))

        return result
    }
}
