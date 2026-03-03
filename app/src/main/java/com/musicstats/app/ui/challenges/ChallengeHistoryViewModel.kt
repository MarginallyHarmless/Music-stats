package com.musicstats.app.ui.challenges

import androidx.lifecycle.ViewModel
import com.musicstats.app.data.dao.ChallengeDao
import com.musicstats.app.data.model.Challenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ChallengeHistoryViewModel @Inject constructor(
    private val challengeDao: ChallengeDao
) : ViewModel() {
    val allChallenges: Flow<List<Challenge>> = challengeDao.getAllChallenges()
}
