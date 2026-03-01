package com.musicstats.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musicstats.app.data.model.Artist
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(artist: Artist): Long

    @Query("SELECT * FROM artists WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): Artist?

    @Query("SELECT * FROM artists ORDER BY name ASC")
    fun getAllArtists(): Flow<List<Artist>>

    @Query("SELECT COUNT(*) FROM artists")
    fun getTotalArtistCount(): Flow<Int>

    @Query("SELECT * FROM artists ORDER BY name ASC")
    suspend fun getAllArtistsSnapshot(): List<Artist>

    @Query("UPDATE artists SET imageUrl = :imageUrl WHERE name = :name")
    suspend fun updateImageUrl(name: String, imageUrl: String)

    @Query("SELECT imageUrl FROM artists WHERE name = :name LIMIT 1")
    fun getArtistImageUrl(name: String): Flow<String?>

    @Query("SELECT * FROM artists WHERE imageUrl IS NULL")
    suspend fun getArtistsWithoutImage(): List<Artist>

    @Query("UPDATE artists SET imageUrl = REPLACE(imageUrl, '250x250', '1000x1000') WHERE imageUrl LIKE '%250x250%'")
    suspend fun upgradeDeezerImagesToXl(): Int

    // --- Suspend queries for MomentDetector ---

    @Query("SELECT * FROM artists WHERE firstHeardAt >= :since ORDER BY firstHeardAt DESC")
    suspend fun getArtistsSince(since: Long): List<Artist>
}
