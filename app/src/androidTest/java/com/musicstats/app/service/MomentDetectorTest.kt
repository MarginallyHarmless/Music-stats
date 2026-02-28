package com.musicstats.app.service

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentDetectorTest {

    private lateinit var db: MusicStatsDatabase
    private lateinit var detector: MomentDetector

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            MusicStatsDatabase::class.java
        ).allowMainThreadQueries().build()
        detector = MomentDetector(
            db.listeningEventDao(),
            db.artistDao(),
            db.momentDao()
        )
    }

    @After
    fun teardown() { db.close() }

    private suspend fun insertSongWithPlays(title: String, artist: String, playCount: Int): Long {
        val now = System.currentTimeMillis()
        db.artistDao().insert(Artist(name = artist, firstHeardAt = now - 86_400_000L * 60))
        val songId = db.songDao().insert(
            Song(title = title, artist = artist, firstHeardAt = now - 86_400_000L * 60)
        )
        repeat(playCount) { i ->
            db.listeningEventDao().insert(
                ListeningEvent(
                    songId = songId,
                    startedAt = now - (playCount - i) * 200_000L,
                    durationMs = 200_000L,
                    sourceApp = "test",
                    completed = true
                )
            )
        }
        return songId
    }

    @Test
    fun detectsSongPlayMilestone_100plays() = runTest {
        insertSongWithPlays("Blinding Lights", "The Weeknd", 100)
        val moments = detector.detectAndPersistNewMoments()
        val milestone = moments.find { it.type == "SONG_PLAYS_100" }
        assertTrue("Expected SONG_PLAYS_100 moment", milestone != null)
        assertEquals("You've played Blinding Lights 100 times", milestone!!.description)
    }

    @Test
    fun milestoneIsIdempotent() = runTest {
        insertSongWithPlays("Blinding Lights", "The Weeknd", 100)
        detector.detectAndPersistNewMoments()
        val second = detector.detectAndPersistNewMoments()
        assertEquals(0, second.count { it.type == "SONG_PLAYS_100" })
    }

    @Test
    fun detectsSongPlays_lowerMilestone_notHigher() = runTest {
        insertSongWithPlays("Song A", "Artist A", 25)
        val moments = detector.detectAndPersistNewMoments()
        assertTrue(moments.any { it.type == "SONG_PLAYS_25" })
        assertTrue(moments.none { it.type == "SONG_PLAYS_50" })
    }

    @Test
    fun detectsCertifiedSkipperArchetype() = runTest {
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 3600 * 1000
        db.artistDao().insert(Artist(name = "Test Artist", firstHeardAt = thirtyDaysAgo))
        val songId = db.songDao().insert(
            Song(title = "T", artist = "Test Artist", firstHeardAt = thirtyDaysAgo)
        )
        repeat(10) { i ->
            db.listeningEventDao().insert(
                ListeningEvent(songId = songId,
                    startedAt = thirtyDaysAgo + i * 3_600_000L,
                    durationMs = 200_000L, sourceApp = "test", completed = true)
            )
        }
        repeat(50) { i ->
            db.listeningEventDao().insert(
                ListeningEvent(songId = songId,
                    startedAt = thirtyDaysAgo + (10 + i) * 3_600_000L,
                    durationMs = 5_000L, sourceApp = "test", completed = false)
            )
        }
        val moments = detector.detectAndPersistNewMoments()
        assertTrue(moments.any { it.type == "ARCHETYPE_CERTIFIED_SKIPPER" })
    }

    @Test
    fun detectsObsessionDaily_fivePlaysInOneDay() = runTest {
        val now = System.currentTimeMillis()
        val todayStart = LocalDate.now(ZoneId.systemDefault())
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        db.artistDao().insert(Artist(name = "Artist", firstHeardAt = now - 86_400_000L * 60))
        val songId = db.songDao().insert(
            Song(title = "Obsession", artist = "Artist", firstHeardAt = now - 86_400_000L * 60)
        )
        repeat(6) { i ->
            db.listeningEventDao().insert(
                ListeningEvent(songId = songId,
                    startedAt = todayStart + i * 3_600_000L,
                    durationMs = 200_000L, sourceApp = "test", completed = true)
            )
        }
        val moments = detector.detectAndPersistNewMoments()
        assertTrue(moments.any { it.type == "OBSESSION_DAILY" })
    }
}
