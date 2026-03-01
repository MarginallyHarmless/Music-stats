package com.musicstats.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musicstats.app.data.model.Moment
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(moment: Moment): Long

    @Query("SELECT * FROM moments WHERE type != 'ARTIST_UNLOCKED' ORDER BY triggeredAt DESC")
    fun getAllMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE seenAt IS NULL ORDER BY triggeredAt DESC")
    fun getUnseenMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE type != 'ARTIST_UNLOCKED' ORDER BY triggeredAt DESC LIMIT :limit")
    fun getRecentMoments(limit: Int): Flow<List<Moment>>

    @Query("UPDATE moments SET seenAt = :timestamp WHERE id = :id")
    suspend fun markSeen(id: Long, timestamp: Long)

    @Query("UPDATE moments SET sharedAt = :timestamp WHERE id = :id")
    suspend fun markShared(id: Long, timestamp: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM moments WHERE type = :type AND entityKey = :entityKey)")
    suspend fun existsByTypeAndKey(type: String, entityKey: String): Boolean

    @Query("SELECT COUNT(*) FROM moments WHERE seenAt IS NULL AND type != 'ARTIST_UNLOCKED'")
    fun getUnseenCount(): Flow<Int>

    @Query("UPDATE moments SET imageUrl = (SELECT imageUrl FROM artists WHERE artists.id = moments.artistId) WHERE artistId IS NOT NULL AND imageUrl IS NULL")
    suspend fun backfillArtistImageUrls()

    @Query("SELECT COUNT(*) FROM moments WHERE type = :type")
    suspend fun countByType(type: String): Int
}
