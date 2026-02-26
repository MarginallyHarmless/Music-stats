package com.musicstats.app.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SongDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository
) : ViewModel() {

    private val songId: Long = savedStateHandle.get<Long>("songId") ?: 0L

    val song: StateFlow<Song?> = repository.getSongByIdFlow(songId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val listeningHistory: StateFlow<List<ListeningEvent>> = repository.getEventsForSong(songId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _totalPlayCount = MutableStateFlow(0)
    val totalPlayCount: StateFlow<Int> = _totalPlayCount

    private val _totalListeningTime = MutableStateFlow(0L)
    val totalListeningTime: StateFlow<Long> = _totalListeningTime

    private val _skipRate = MutableStateFlow(0f)
    val skipRate: StateFlow<Float> = _skipRate

    init {
        viewModelScope.launch {
            val stats = repository.getSongStats(songId)
            if (stats != null) {
                _totalPlayCount.value = stats.playCount
                _totalListeningTime.value = stats.totalDurationMs
            }
            _skipRate.value = repository.getSkipRate(songId).toFloat()
        }
    }
}
