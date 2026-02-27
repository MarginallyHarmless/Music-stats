package com.musicstats.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PaletteViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentPalette: StateFlow<AlbumPalette> =
        repository.getMostRecentEvent()
            .flatMapLatest { event ->
                if (event == null) flowOf(AlbumPalette())
                else repository.getSongByIdFlow(event.songId).map { song ->
                    song?.toAlbumPalette() ?: AlbumPalette()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumPalette())
}
