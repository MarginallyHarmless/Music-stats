package com.musicstats.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.dao.SongWithStats
import com.musicstats.app.data.repository.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SortMode {
    MostPlayed, MostRecent, Alphabetical
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: MusicRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortMode = MutableStateFlow(SortMode.Alphabetical)
    val sortMode: StateFlow<SortMode> = _sortMode

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    val songs: StateFlow<List<SongWithStats>> = combine(
        repository.getAllSongsWithStats(),
        _searchQuery,
        _sortMode
    ) { allSongs, query, sort ->
        val filtered = if (query.isBlank()) {
            allSongs
        } else {
            val lower = query.lowercase()
            allSongs.filter { song ->
                song.title.lowercase().contains(lower) ||
                    song.artist.lowercase().contains(lower)
            }
        }
        when (sort) {
            SortMode.MostPlayed -> filtered.sortedByDescending { it.playCount }
            SortMode.MostRecent -> filtered.sortedByDescending { it.firstHeardAt }
            SortMode.Alphabetical -> filtered.sortedBy { it.title.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
