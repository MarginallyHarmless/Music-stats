package com.musicstats.app.data.repository

import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.model.Moment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentsRepository @Inject constructor(
    private val momentDao: MomentDao
) {
    fun getRecentMoments(limit: Int = 10): Flow<List<Moment>> =
        momentDao.getRecentMoments(limit)

    fun getAllMoments(): Flow<List<Moment>> = momentDao.getAllMoments()

    fun getUnseenCount(): Flow<Int> = momentDao.getUnseenCount()

    suspend fun markSeen(id: Long) = momentDao.markSeen(id, System.currentTimeMillis())

    suspend fun markShared(id: Long) = momentDao.markShared(id, System.currentTimeMillis())
}
