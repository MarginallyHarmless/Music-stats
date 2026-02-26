package com.musicstats.app.data.repository

import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class MusicRepositoryTest {

    private lateinit var songDao: SongDao
    private lateinit var artistDao: ArtistDao
    private lateinit var eventDao: ListeningEventDao
    private lateinit var repository: MusicRepository

    @Before
    fun setup() {
        songDao = mockk(relaxed = true)
        artistDao = mockk(relaxed = true)
        eventDao = mockk(relaxed = true)
        repository = MusicRepository(songDao, artistDao, eventDao)
    }

    @Test
    fun `recordPlay creates new artist and song when they dont exist`() = runTest {
        coEvery { artistDao.findByName("Artist") } returns null
        coEvery { artistDao.insert(any()) } returns 1L
        coEvery { songDao.findByTitleAndArtist("Song", "Artist") } returns null
        coEvery { songDao.insert(any()) } returns 10L
        coEvery { eventDao.insert(any()) } returns 100L

        val event = repository.recordPlay(
            title = "Song",
            artist = "Artist",
            album = "Album",
            sourceApp = "com.spotify",
            startedAt = 1000L,
            durationMs = 60_000L,
            completed = true
        )

        assertEquals(10L, event.songId)
        assertEquals(100L, event.id)
        coVerify { artistDao.insert(match { it.name == "Artist" }) }
        coVerify { songDao.insert(match { it.title == "Song" && it.artist == "Artist" }) }
    }

    @Test
    fun `recordPlay reuses existing artist and song`() = runTest {
        val existingArtist = Artist(id = 1, name = "Artist", firstHeardAt = 500L)
        val existingSong = Song(id = 10, title = "Song", artist = "Artist", firstHeardAt = 500L)
        coEvery { artistDao.findByName("Artist") } returns existingArtist
        coEvery { songDao.findByTitleAndArtist("Song", "Artist") } returns existingSong
        coEvery { eventDao.insert(any()) } returns 100L

        val event = repository.recordPlay(
            title = "Song",
            artist = "Artist",
            album = null,
            sourceApp = "com.spotify",
            startedAt = 2000L,
            durationMs = 30_000L,
            completed = false
        )

        assertEquals(10L, event.songId)
        coVerify(exactly = 0) { artistDao.insert(any()) }
        coVerify(exactly = 0) { songDao.insert(any()) }
    }

    @Test
    fun `recordPlay deduplicates by exact title and artist match`() = runTest {
        // First call — new song
        coEvery { artistDao.findByName("Artist") } returns null
        coEvery { artistDao.insert(any()) } returns 1L
        coEvery { songDao.findByTitleAndArtist("Song", "Artist") } returns null
        coEvery { songDao.insert(any()) } returns 10L
        coEvery { eventDao.insert(any()) } returns 100L

        repository.recordPlay("Song", "Artist", null, "com.spotify", 1000L, 60_000L, true)

        // Second call — same song, now exists
        val existingSong = Song(id = 10, title = "Song", artist = "Artist", firstHeardAt = 1000L)
        coEvery { artistDao.findByName("Artist") } returns Artist(1, "Artist", 1000L)
        coEvery { songDao.findByTitleAndArtist("Song", "Artist") } returns existingSong
        coEvery { eventDao.insert(any()) } returns 101L

        val event2 = repository.recordPlay("Song", "Artist", null, "com.spotify", 2000L, 60_000L, true)

        assertEquals(10L, event2.songId)
        // Song insert should only have been called once (first call)
        coVerify(exactly = 1) { songDao.insert(any()) }
    }
}
