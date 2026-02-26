package com.musicstats.app.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.dao.ArtistListeningEvent
import com.musicstats.app.data.dao.ArtistStats
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: MusicRepository
) : ViewModel() {

    val artistName: String = Uri.decode(savedStateHandle.get<String>("artistName") ?: "")

    val imageUrl: StateFlow<String?> = repository.getArtistImageUrl(artistName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val listeningHistory: StateFlow<List<ArtistListeningEvent>> =
        repository.getEventsForArtist(artistName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _stats = MutableStateFlow<ArtistStats?>(null)
    val stats: StateFlow<ArtistStats?> = _stats

    init {
        viewModelScope.launch {
            _stats.value = repository.getArtistStats(artistName)
        }
    }
}
