package com.musicstats.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musicstats.app.data.model.Challenge
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(challenge: Challenge): Long

    @Query("SELECT * FROM challenges WHERE weekStart = :weekStart ORDER BY id ASC")
    fun getChallengesForWeek(weekStart: Long): Flow<List<Challenge>>

    @Query("SELECT * FROM challenges WHERE weekStart = :weekStart ORDER BY id ASC")
    suspend fun getChallengesForWeekSuspend(weekStart: Long): List<Challenge>

    @Query("SELECT * FROM challenges ORDER BY weekStart DESC, id ASC")
    fun getAllChallenges(): Flow<List<Challenge>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun getById(id: Long): Challenge?

    @Query("UPDATE challenges SET currentValue = :currentValue, completed = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, currentValue: Float, completed: Boolean, completedAt: Long?)

    @Query("SELECT DISTINCT type FROM challenges WHERE weekStart = :prevWeekStart")
    suspend fun getTypesForWeek(prevWeekStart: Long): List<String>

    @Query("SELECT COUNT(*) FROM challenges WHERE weekStart = :weekStart")
    suspend fun countForWeek(weekStart: Long): Int
}
