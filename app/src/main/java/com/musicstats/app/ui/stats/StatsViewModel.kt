package com.musicstats.app.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.dao.ArtistPlayStats
import com.musicstats.app.data.dao.DailyListening
import com.musicstats.app.data.dao.HourlyListening
import com.musicstats.app.data.dao.SongPlayStats
import com.musicstats.app.data.repository.MusicRepository
import com.musicstats.app.util.startOfMonth
import com.musicstats.app.util.startOfToday
import com.musicstats.app.util.startOfWeek
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class TimeRange(val label: String) {
    Today("Today"),
    ThisWeek("This Week"),
    ThisMonth("This Month"),
    AllTime("All Time");

    fun toEpochMillis(): Long = when (this) {
        Today -> startOfToday()
        ThisWeek -> startOfWeek()
        ThisMonth -> startOfMonth()
        AllTime -> 0L
    }
}

enum class TopListMetric(val label: String) {
    Duration("By Duration"),
    PlayCount("By Play Count")
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    private val _selectedTimeRange = MutableStateFlow(TimeRange.AllTime)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _selectedMetric = MutableStateFlow(TopListMetric.Duration)
    val selectedMetric: StateFlow<TopListMetric> = _selectedMetric.asStateFlow()

    // --- Time tab (reactive, flatMapLatest on time range) ---

    val totalListeningTime: StateFlow<Long> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getListeningTimeSince(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalPlayCount: StateFlow<Int> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getTotalPlayCount(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val avgSessionDuration: StateFlow<Long> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getAverageSessionDuration(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val longestSession: StateFlow<Long> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getLongestSession(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val totalSkips: StateFlow<Int> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getSkipCountSince(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hourlyListening: StateFlow<List<HourlyListening>> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getHourlyListening(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyListening: StateFlow<List<DailyListening>> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getDailyListening(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Discovery tab (reactive, flatMapLatest on time range) ---

    val newSongsDiscovered: StateFlow<Int> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getNewSongsDiscoveredSince(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val newArtistsDiscovered: StateFlow<Int> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getNewArtistsDiscoveredSince(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUniqueSongs: StateFlow<Int> =
        repository.getTotalSongCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUniqueArtists: StateFlow<Int> =
        repository.getTotalArtistCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deepCuts: StateFlow<List<SongPlayStats>> =
        repository.getDeepCuts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Top Lists tab (reactive, flatMapLatest on time range) ---

    val topSongsByDuration: StateFlow<List<SongPlayStats>> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getTopSongsByDuration(range.toEpochMillis(), 10)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topSongsByPlayCount: StateFlow<List<SongPlayStats>> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getTopSongsByPlayCount(range.toEpochMillis(), 10)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topArtistsByDuration: StateFlow<List<ArtistPlayStats>> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getTopArtistsByDuration(range.toEpochMillis(), 10)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
    }

    fun selectMetric(metric: TopListMetric) {
        _selectedMetric.value = metric
    }
}
