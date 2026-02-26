package com.musicstats.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.musicstats.app.data.model.Song
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(song: Song): Long

    @Update
    suspend fun update(song: Song)

    @Query("SELECT * FROM songs WHERE title = :title AND artist = :artist LIMIT 1")
    suspend fun findByTitleAndArtist(title: String, artist: String): Song?

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongsSnapshot(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getById(id: Long): Song?

    @Query("SELECT * FROM songs WHERE id = :id")
    fun getSongByIdFlow(id: Long): Flow<Song?>

    @Query("SELECT COUNT(*) FROM songs")
    fun getTotalSongCount(): Flow<Int>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN listening_events le ON le.songId = s.id
        GROUP BY s.id
        ORDER BY COALESCE(SUM(le.durationMs), 0) DESC
        LIMIT :limit
        """
    )
    fun getTopSongsByTime(limit: Int = 10): Flow<List<Song>>

    @Query("UPDATE songs SET albumArtUrl = :url WHERE id = :songId")
    suspend fun updateAlbumArtUrl(songId: Long, url: String)

    @Query("SELECT * FROM songs WHERE albumArtUrl IS NULL")
    suspend fun getSongsWithoutAlbumArt(): List<Song>
}
