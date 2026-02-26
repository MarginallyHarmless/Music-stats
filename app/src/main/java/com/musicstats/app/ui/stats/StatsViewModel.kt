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
import kotlinx.coroutines.launch
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

    // --- Time tab (suspend-based data, reloaded on time range change) ---

    private val _totalListeningTime = MutableStateFlow(0L)
    val totalListeningTime: StateFlow<Long> = _totalListeningTime.asStateFlow()

    private val _avgSessionDuration = MutableStateFlow(0L)
    val avgSessionDuration: StateFlow<Long> = _avgSessionDuration.asStateFlow()

    private val _longestSession = MutableStateFlow(0L)
    val longestSession: StateFlow<Long> = _longestSession.asStateFlow()

    // Flow-based data — flatMapLatest when time range changes
    val hourlyListening: StateFlow<List<HourlyListening>> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getHourlyListening(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyListening: StateFlow<List<DailyListening>> =
        _selectedTimeRange.flatMapLatest { range ->
            repository.getDailyListening(range.toEpochMillis())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Discovery tab (suspend-based data) ---

    private val _newSongsDiscovered = MutableStateFlow(0)
    val newSongsDiscovered: StateFlow<Int> = _newSongsDiscovered.asStateFlow()

    private val _newArtistsDiscovered = MutableStateFlow(0)
    val newArtistsDiscovered: StateFlow<Int> = _newArtistsDiscovered.asStateFlow()

    val totalUniqueSongs: StateFlow<Int> =
        repository.getTotalSongCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUniqueArtists: StateFlow<Int> =
        repository.getTotalArtistCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deepCuts: StateFlow<List<SongPlayStats>> =
        repository.getDeepCuts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Top Lists tab (Flow-based, flatMapLatest) ---

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

    init {
        loadSuspendData()
    }

    fun selectTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        loadSuspendData()
    }

    fun selectMetric(metric: TopListMetric) {
        _selectedMetric.value = metric
    }

    private fun loadSuspendData() {
        val since = _selectedTimeRange.value.toEpochMillis()
        viewModelScope.launch {
            _totalListeningTime.value = repository.getListeningTimeSince(since)
            _avgSessionDuration.value = repository.getAverageSessionDuration(since)
            _longestSession.value = repository.getLongestSession(since)
            _newSongsDiscovered.value = repository.getNewSongsDiscoveredSince(since)
            _newArtistsDiscovered.value = repository.getNewArtistsDiscoveredSince(since)
        }
    }
}
