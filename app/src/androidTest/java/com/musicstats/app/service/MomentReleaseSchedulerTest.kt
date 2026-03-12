package com.musicstats.app.service

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Moment
import com.musicstats.app.data.model.Song
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentReleaseSchedulerTest {

    private lateinit var db: MusicStatsDatabase
    private lateinit var momentDao: MomentDao
    private lateinit var eventDao: ListeningEventDao
    private lateinit var scheduler: MomentReleaseScheduler

    // A real song ID required by the ListeningEvent foreign key
    private var testSongId: Long = -1L

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            MusicStatsDatabase::class.java
        ).allowMainThreadQueries().build()

        momentDao = db.momentDao()
        eventDao = db.listeningEventDao()
        scheduler = MomentReleaseScheduler(momentDao, eventDao)

        // Insert a seed song so ListeningEvents can reference a valid songId
        testSongId = db.songDao().insert(
            Song(title = "Test Song", artist = "Test Artist", firstHeardAt = 0L)
        )
    }

    @After
    fun teardown() { db.close() }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Inserts a single ListeningEvent with [completed = true] that covers [hours] hours.
     * The gate query sums only completed events so we must set completed = true.
     */
    private suspend fun insertListeningTime(hours: Float) {
        val durationMs = (hours * 3_600_000L).toLong()
        eventDao.insert(
            ListeningEvent(
                songId = testSongId,
                startedAt = System.currentTimeMillis() - durationMs,
                durationMs = durationMs,
                sourceApp = "test",
                completed = true
            )
        )
    }

    /**
     * Inserts an unreleased Moment using [type] (which determines tier via MomentPriority)
     * and [triggeredAt]. Each call uses a unique [entityKey] so the unique-index constraint
     * is never violated.
     */
    private suspend fun insertMoment(
        type: String,
        triggeredAt: Long,
        releasedAt: Long? = null,
        entityKey: String = "$type:$triggeredAt"
    ): Long {
        return momentDao.insert(
            Moment(
                type = type,
                entityKey = entityKey,
                triggeredAt = triggeredAt,
                title = "Test $type",
                description = "Desc for $type",
                releasedAt = releasedAt
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------

    /**
     * Gate not reached → releaseNext() must return null regardless of pending moments.
     * We insert only 1 hour of listening (gate requires 5 h).
     */
    @Test
    fun releaseNext_returnsNullWhenGateNotReached() = runTest {
        insertListeningTime(1f)
        // OBSESSION_DAILY is tier 5; it should be eligible once the gate is open
        insertMoment(type = "OBSESSION_DAILY", triggeredAt = System.currentTimeMillis() - 1000L)

        val result = scheduler.releaseNext()

        assertNull("Expected null when gate has not been reached", result)
    }

    /**
     * When a tier-2 and a tier-5 moment are both unreleased and the gate is satisfied,
     * the tier-2 moment (higher priority = lower tier number) must be chosen.
     */
    @Test
    fun releaseNext_releasesHighestPriorityFirst() = runTest {
        insertListeningTime(6f)

        val now = System.currentTimeMillis()
        // Tier 5 — Common
        insertMoment(type = "OBSESSION_DAILY", triggeredAt = now - 2000L, entityKey = "tier5:1")
        // Tier 2 — Epic
        insertMoment(type = "STREAK_30", triggeredAt = now - 1000L, entityKey = "tier2:1")

        val released = scheduler.releaseNext()

        assertNotNull("Expected a moment to be released", released)
        assert(released!!.type == "STREAK_30") {
            "Expected tier-2 moment STREAK_30 to be released first, but got ${released.type}"
        }
    }

    /**
     * The daily limit is 1. A second call on the same calendar day must return null.
     */
    @Test
    fun releaseNext_respectsDailyLimit() = runTest {
        insertListeningTime(6f)

        val now = System.currentTimeMillis()
        insertMoment(type = "OBSESSION_DAILY", triggeredAt = now - 2000L, entityKey = "daily:1")
        insertMoment(type = "LONGEST_SESSION",  triggeredAt = now - 1000L, entityKey = "daily:2")

        val first  = scheduler.releaseNext()
        val second = scheduler.releaseNext()

        assertNotNull("First call should release a moment", first)
        assertNull("Second call on the same day should return null (daily limit)", second)
    }

    /**
     * A tier-5 moment triggered 15 days ago (> 14-day expiry window) must be deleted
     * rather than released.  With no other candidates the result is null.
     */
    @Test
    fun releaseNext_expiresOldTier5Moments() = runTest {
        insertListeningTime(6f)

        val fifteenDaysAgo = System.currentTimeMillis() - (15L * 24 * 3_600_000)
        insertMoment(type = "OBSESSION_DAILY", triggeredAt = fifteenDaysAgo, entityKey = "tier5:stale")

        val result = scheduler.releaseNext()

        assertNull("Stale tier-5 moment should be expired, not released", result)
    }

    /**
     * Tier-1 moments never expire, regardless of how old they are.
     * A tier-1 moment triggered 30 days ago must still be released.
     */
    @Test
    fun releaseNext_neverExpiresTier1() = runTest {
        insertListeningTime(6f)

        val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 3_600_000)
        // SONG_PLAYS_500 is tier 1 — Legendary
        insertMoment(type = "SONG_PLAYS_500", triggeredAt = thirtyDaysAgo, entityKey = "tier1:old")

        val result = scheduler.releaseNext()

        assertNotNull("Tier-1 moment must never expire and should be released", result)
        assert(result!!.type == "SONG_PLAYS_500") {
            "Expected SONG_PLAYS_500 to be released, but got ${result.type}"
        }
    }

    /**
     * A newer, higher-priority moment must jump ahead of an older, lower-priority one.
     * tier-3 moment (newer) vs tier-5 moment (older) → tier-3 wins.
     */
    @Test
    fun releaseNext_newerHigherPriorityJumpsQueue() = runTest {
        insertListeningTime(6f)

        val now = System.currentTimeMillis()
        // Old tier-5
        insertMoment(type = "OBSESSION_DAILY", triggeredAt = now - 10_000L, entityKey = "tier5:old")
        // Newer tier-3
        insertMoment(type = "SONG_PLAYS_100",  triggeredAt = now - 1_000L,  entityKey = "tier3:new")

        val released = scheduler.releaseNext()

        assertNotNull("Expected a moment to be released", released)
        assert(released!!.type == "SONG_PLAYS_100") {
            "Expected tier-3 SONG_PLAYS_100 to jump the queue over tier-5 OBSESSION_DAILY, " +
                "but got ${released.type}"
        }
    }
}
