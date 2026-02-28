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

        // Archetype moments — emoji only, no real data needed
        result += Moment(id = -1, type = "ARCHETYPE_NIGHT_OWL", entityKey = "preview",
            triggeredAt = now, title = "Night Owl",
            description = "You do most of your listening after 10pm",
            statLine = "73% of your listening")
        result += Moment(id = -2, type = "ARCHETYPE_COMPLETIONIST", entityKey = "preview",
            triggeredAt = now, title = "Completionist",
            description = "You skip less than 5% of songs — truly dedicated",
            statLine = "2.3% skip rate")

        // Artist moments — use real top artists
        artists.forEachIndexed { idx, artist ->
            val artistId = -(10L + idx)
            val hours = (artist.totalDurationMs / 3_600_000L).coerceAtLeast(1L)
            val humanHours = if (hours == 1L) "1 hour" else "$hours hours"
            result += Moment(
                id = -(20L + idx),
                type = "ARTIST_HOURS_$hours",
                entityKey = "preview",
                triggeredAt = now,
                title = "$humanHours of ${artist.name}",
                description = "You've spent $humanHours listening to ${artist.name}",
                artistId = artistId,
                statLine = "${artist.playCount} total plays",
                imageUrl = artist.imageUrl,
                entityName = artist.name
            )
        }

        // Loyal Fan — top artist
        if (artists.isNotEmpty()) {
            val top = artists[0]
            result += Moment(
                id = -30,
                type = "ARCHETYPE_LOYAL_FAN",
                entityKey = "preview",
                triggeredAt = now,
                title = "Loyal Fan",
                description = "Over 50% of your listening is ${top.name}",
                artistId = -10L,
                statLine = "${top.playCount} plays",
                imageUrl = top.imageUrl,
                entityName = top.name
            )
        }

        // Breakup Candidate — second artist if available
        if (artists.size > 1) {
            val artist = artists[1]
            result += Moment(
                id = -31,
                type = "BREAKUP_CANDIDATE",
                entityKey = "preview",
                triggeredAt = now,
                title = "Maybe break up?",
                description = "You've skipped ${artist.name} a lot this week",
                artistId = -11L,
                statLine = "12 skips this week",
                imageUrl = artist.imageUrl,
                entityName = artist.name
            )
        }

        // Song moments — use real top songs
        songs.forEachIndexed { idx, song ->
            result += Moment(
                id = -(40L + idx),
                type = "SONG_PLAYS_${song.playCount}",
                entityKey = "preview",
                triggeredAt = now,
                title = "${song.playCount} plays",
                description = "You've played ${song.title} ${song.playCount} times",
                songId = song.songId,
                statLine = "${formatDuration(song.totalDurationMs)} total",
                imageUrl = song.albumArtUrl
            )
        }

        // Streak / total hours — no image
        result += Moment(id = -50, type = "STREAK_7", entityKey = "preview",
            triggeredAt = now, title = "7-day streak",
            description = "7 days in a row — you're on fire")
        result += Moment(id = -51, type = "TOTAL_HOURS_100", entityKey = "preview",
            triggeredAt = now, title = "100 hours",
            description = "You've listened to 100h of music in total")

        return result
    }
}
