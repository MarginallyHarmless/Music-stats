package com.musicstats.app.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.dao.DailyListening
import com.musicstats.app.data.dao.SongPlayStats
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.repository.MomentsRepository
import com.musicstats.app.data.repository.MusicRepository
import com.musicstats.app.service.MediaSessionTracker
import com.musicstats.app.service.MomentDetector
import com.musicstats.app.service.MomentWorker
import com.musicstats.app.util.daysAgo
import com.musicstats.app.util.startOfToday
import com.musicstats.app.util.startOfWeek
import com.musicstats.app.util.startOfYesterday
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    val mediaSessionTracker: MediaSessionTracker,
    @ApplicationContext private val context: Context,
    private val momentDetector: MomentDetector,
    private val momentsRepository: MomentsRepository
) : ViewModel() {

    val greeting: String
        get() {
            val hour = java.time.LocalTime.now().hour
            return when {
                hour < 12 -> "Good morning"
                hour < 17 -> "Good afternoon"
                else -> "Good evening"
            }
        }

    val todayFormatted: String
        get() {
            val today = java.time.LocalDate.now()
            return today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d"))
        }

    init {
        repository.upgradeArtworkToHighRes()
        repository.backfillArtistImages()
        repository.backfillAlbumArt()
        repository.backfillPaletteColors()
        MomentWorker.schedule(context)
    }

    val todayListeningTimeMs: StateFlow<Long> =
        repository.getListeningTimeSince(startOfToday())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val yesterdayListeningTimeMs: StateFlow<Long> =
        repository.getListeningTimeBetween(startOfYesterday(), startOfToday())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val songsToday: StateFlow<Int> =
        repository.getSongCountSince(startOfToday())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val skipsToday: StateFlow<Int> =
        repository.getSkipCountSince(startOfToday())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topArtistToday: StateFlow<TopArtistInfo?> =
        repository.getTopArtistsByDuration(startOfToday(), 1)
            .map { artists -> artists.firstOrNull() }
            .flatMapLatest { stats ->
                if (stats == null) flowOf(null)
                else repository.getArtistImageUrl(stats.artist).map { imageUrl ->
                    TopArtistInfo(stats.artist, imageUrl, stats.playCount, stats.totalDurationMs)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    val recentMoments: StateFlow<List<Moment>> =
        momentsRepository.getRecentMoments(10)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unseenMomentsCount: StateFlow<Int> =
        momentsRepository.getUnseenCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun detectMomentsOnOpen() {
        viewModelScope.launch(Dispatchers.IO) {
            momentDetector.detectAndPersistNewMoments()
        }
    }

    fun markMomentSeen(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            momentsRepository.markSeen(id)
        }
    }

    fun markMomentShared(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            momentsRepository.markShared(id)
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

data class TopArtistInfo(
    val name: String,
    val imageUrl: String?,
    val playCount: Int = 0,
    val totalDurationMs: Long = 0L
)
