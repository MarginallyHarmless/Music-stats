package com.musicstats.app.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.model.Challenge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChallengeDaoTest {
    private lateinit var db: MusicStatsDatabase
    private lateinit var dao: ChallengeDao

    private val mondayMs = 1709510400000L

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            MusicStatsDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.challengeDao()
    }

    @After
    fun teardown() { db.close() }

    private fun challenge(type: String = "DISCOVERY_STRETCH", weekStart: Long = mondayMs) =
        Challenge(
            type = type,
            weekStart = weekStart,
            title = "Discover 6 new songs",
            description = "You found 3 last week. Double it.",
            targetValue = 6f,
            generatedAt = System.currentTimeMillis()
        )

    @Test
    fun insertAndQuery() = runTest {
        dao.insert(challenge())
        val result = dao.getChallengesForWeek(mondayMs).first()
        assertEquals(1, result.size)
        assertEquals("DISCOVERY_STRETCH", result[0].type)
    }

    @Test
    fun duplicateTypeWeekIsIgnored() = runTest {
        dao.insert(challenge())
        dao.insert(challenge())
        assertEquals(1, dao.getChallengesForWeek(mondayMs).first().size)
    }

    @Test
    fun updateProgress() = runTest {
        dao.insert(challenge())
        val id = dao.getChallengesForWeek(mondayMs).first()[0].id
        dao.updateProgress(id, 4f, false, null)
        val updated = dao.getById(id)!!
        assertEquals(4f, updated.currentValue)
        assertFalse(updated.completed)
    }

    @Test
    fun updateProgressCompleted() = runTest {
        dao.insert(challenge())
        val id = dao.getChallengesForWeek(mondayMs).first()[0].id
        val now = System.currentTimeMillis()
        dao.updateProgress(id, 6f, true, now)
        val updated = dao.getById(id)!!
        assertTrue(updated.completed)
        assertEquals(now, updated.completedAt)
    }

    @Test
    fun getTypesForWeek() = runTest {
        dao.insert(challenge("DISCOVERY_STRETCH"))
        dao.insert(challenge("CONSISTENCY"))
        val types = dao.getTypesForWeek(mondayMs)
        assertEquals(2, types.size)
        assertTrue(types.contains("DISCOVERY_STRETCH"))
        assertTrue(types.contains("CONSISTENCY"))
    }

    @Test
    fun countForWeek() = runTest {
        dao.insert(challenge("A"))
        dao.insert(challenge("B"))
        assertEquals(2, dao.countForWeek(mondayMs))
    }

    @Test
    fun differentWeeksAreIndependent() = runTest {
        val nextMonday = mondayMs + 7 * 24 * 3600 * 1000L
        dao.insert(challenge(weekStart = mondayMs))
        dao.insert(challenge(weekStart = nextMonday))
        assertEquals(1, dao.getChallengesForWeek(mondayMs).first().size)
        assertEquals(1, dao.getChallengesForWeek(nextMonday).first().size)
    }
}
