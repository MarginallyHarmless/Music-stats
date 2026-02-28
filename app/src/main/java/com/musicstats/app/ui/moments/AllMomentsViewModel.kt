package com.musicstats.app.ui.moments

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.repository.MomentsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AllMomentsViewModel @Inject constructor(
    private val momentsRepository: MomentsRepository
) : ViewModel() {

    val moments: StateFlow<List<Moment>> =
        momentsRepository.getAllMoments()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
}
