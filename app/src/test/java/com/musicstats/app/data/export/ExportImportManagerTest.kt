package com.musicstats.app.data.export

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
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ExportImportManagerTest {

    private lateinit var songDao: SongDao
    private lateinit var artistDao: ArtistDao
    private lateinit var eventDao: ListeningEventDao
    private lateinit var manager: ExportImportManager

    @Before
    fun setup() {
        songDao = mockk(relaxed = true)
        artistDao = mockk(relaxed = true)
        eventDao = mockk(relaxed = true)
        manager = ExportImportManager(songDao, artistDao, eventDao)
    }

    @Test
    fun `export produces valid json with version`() = runTest {
        val song = Song(id = 1, title = "Song", artist = "Artist", album = "Album", firstHeardAt = 1000L)
        val artist = Artist(id = 1, name = "Artist", firstHeardAt = 1000L)
        val event = ListeningEvent(id = 1, songId = 1, startedAt = 1000L, durationMs = 60_000L, sourceApp = "com.spotify", completed = true)

        coEvery { songDao.getAllSongsSnapshot() } returns listOf(song)
        coEvery { artistDao.getAllArtistsSnapshot() } returns listOf(artist)
        coEvery { eventDao.getAllEventsSnapshot() } returns listOf(event)

        val json = manager.exportToJson()

        assertTrue("JSON should contain Song", json.contains("Song"))
        assertTrue("JSON should contain Artist", json.contains("Artist"))
        assertTrue("JSON should contain exportedAt", json.contains("exportedAt"))
    }

    @Test
    fun `import rejects future version`() = runTest {
        val json = """
            {
                "version": 2,
                "exportedAt": "2025-01-01T00:00:00Z",
                "songs": [],
                "artists": [],
                "listeningEvents": []
            }
        """.trimIndent()

        try {
            manager.importFromJson(json)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("Unsupported export version"))
        }
    }

    @Test
    fun `import accepts version 1`() = runTest {
        val json = """
            {
                "version": 1,
                "exportedAt": "2025-01-01T00:00:00Z",
                "songs": [],
                "artists": [],
                "listeningEvents": []
            }
        """.trimIndent()

        val result = manager.importFromJson(json)
        assertEquals(0, result.songsImported)
        assertEquals(0, result.eventsImported)
    }

    @Test
    fun `import skips duplicate songs`() = runTest {
        val existingSong = Song(id = 1, title = "Song", artist = "Artist", firstHeardAt = 1000L)
        coEvery { artistDao.findByName("Artist") } returns Artist(1, "Artist", 1000L)
        coEvery { songDao.findByTitleAndArtist("Song", "Artist") } returns existingSong

        val json = """
            {
                "version": 1,
                "exportedAt": "2025-01-01T00:00:00Z",
                "songs": [{"title": "Song", "artist": "Artist", "firstHeardAt": 1000}],
                "artists": [{"name": "Artist", "firstHeardAt": 1000}],
                "listeningEvents": []
            }
        """.trimIndent()

        val result = manager.importFromJson(json)
        assertEquals(0, result.songsImported)
        coVerify(exactly = 0) { songDao.insert(any()) }
    }

    @Test
    fun `import skips duplicate events`() = runTest {
        val song = Song(id = 1, title = "Song", artist = "Artist", firstHeardAt = 1000L)
        val existingEvent = ListeningEvent(id = 1, songId = 1, startedAt = 1000L, durationMs = 60_000L, sourceApp = "com.spotify", completed = true)

        coEvery { artistDao.findByName("Artist") } returns Artist(1, "Artist", 1000L)
        coEvery { songDao.findByTitleAndArtist("Song", "Artist") } returns song
        coEvery { eventDao.findBySongAndTime(1L, 1000L) } returns existingEvent

        val json = """
            {
                "version": 1,
                "exportedAt": "2025-01-01T00:00:00Z",
                "songs": [{"title": "Song", "artist": "Artist", "firstHeardAt": 1000}],
                "artists": [{"name": "Artist", "firstHeardAt": 1000}],
                "listeningEvents": [{"songTitle": "Song", "songArtist": "Artist", "startedAt": 1000, "durationMs": 60000, "sourceApp": "com.spotify", "completed": true}]
            }
        """.trimIndent()

        val result = manager.importFromJson(json)
        assertEquals(0, result.eventsImported)
        coVerify(exactly = 0) { eventDao.insert(any()) }
    }
}
