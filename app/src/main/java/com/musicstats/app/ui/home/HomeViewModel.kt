package com.musicstats.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.dao.ArtistPlayStats
import com.musicstats.app.data.dao.DailyListening
import com.musicstats.app.data.dao.SongPlayStats
import com.musicstats.app.data.repository.MusicRepository
import com.musicstats.app.util.daysAgo
import com.musicstats.app.util.startOfToday
import com.musicstats.app.util.startOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _todayListeningTimeMs = MutableStateFlow(0L)
    val todayListeningTimeMs: StateFlow<Long> = _todayListeningTimeMs

    private val _songsToday = MutableStateFlow(0)
    val songsToday: StateFlow<Int> = _songsToday

    private val _topArtistToday = MutableStateFlow<String?>(null)
    val topArtistToday: StateFlow<String?> = _topArtistToday

    val weeklyDailyListening: StateFlow<List<DailyListening>> =
        repository.getDailyListening(daysAgo(7))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSongsThisWeek: StateFlow<List<SongPlayStats>> =
        repository.getTopSongsByDuration(startOfWeek(), 5)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentStreak: StateFlow<Int> =
        repository.getDailyListening(daysAgo(365))
            .map { dailyData -> computeStreak(dailyData) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var topArtistJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _todayListeningTimeMs.value = repository.getListeningTimeSince(startOfToday())
            _songsToday.value = repository.getSongCountSince(startOfToday())
        }
        topArtistJob?.cancel()
        topArtistJob = viewModelScope.launch {
            repository.getTopArtistsByDuration(startOfToday(), 1).collect { artists ->
                _topArtistToday.value = artists.firstOrNull()?.artist
            }
        }
    }

    private fun computeStreak(dailyData: List<DailyListening>): Int {
        if (dailyData.isEmpty()) return 0

        val daysWithListening = dailyData
            .filter { it.totalDurationMs > 0 }
            .map { java.time.LocalDate.parse(it.day) }
            .toSortedSet(compareByDescending { it })

        if (daysWithListening.isEmpty()) return 0

        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)

        // Streak must include today or yesterday to be "current"
        val startDay = when {
            daysWithListening.contains(today) -> today
            daysWithListening.contains(yesterday) -> yesterday
            else -> return 0
        }

        var streak = 0
        var checkDay = startDay
        while (daysWithListening.contains(checkDay)) {
            streak++
            checkDay = checkDay.minusDays(1)
        }
        return streak
    }
}
