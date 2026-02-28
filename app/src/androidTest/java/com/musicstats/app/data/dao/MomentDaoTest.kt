package com.musicstats.app.data.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.model.Moment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MomentDaoTest {

    private lateinit var db: MusicStatsDatabase
    private lateinit var dao: MomentDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().context,
            MusicStatsDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.momentDao()
    }

    @After
    fun teardown() { db.close() }

    @Test
    fun insertAndQueryMoment() = runTest {
        val moment = Moment(
            type = "SONG_PLAYS_100",
            entityKey = "42:100",
            triggeredAt = 1000L,
            title = "100 plays",
            description = "You've played Test Song 100 times"
        )
        dao.insert(moment)
        val all = dao.getAllMoments().first()
        assertEquals(1, all.size)
        assertEquals("SONG_PLAYS_100", all[0].type)
    }

    @Test
    fun duplicateInsertIsIgnored() = runTest {
        val moment = Moment(
            type = "SONG_PLAYS_100", entityKey = "42:100", triggeredAt = 1000L,
            title = "100 plays", description = "desc"
        )
        dao.insert(moment)
        dao.insert(moment.copy(triggeredAt = 2000L)) // same type+entityKey
        assertEquals(1, dao.getAllMoments().first().size)
    }

    @Test
    fun unseenMomentsReturnsOnlyUnseen() = runTest {
        dao.insert(Moment(type = "A", entityKey = "1", triggeredAt = 1000L,
            title = "T", description = "D", seenAt = null))
        dao.insert(Moment(type = "B", entityKey = "2", triggeredAt = 2000L,
            title = "T", description = "D", seenAt = 1001L))
        val unseen = dao.getUnseenMoments().first()
        assertEquals(1, unseen.size)
        assertEquals("A", unseen[0].type)
    }

    @Test
    fun markSeenUpdatesTimestamp() = runTest {
        dao.insert(Moment(type = "A", entityKey = "1", triggeredAt = 1000L,
            title = "T", description = "D"))
        val id = dao.getAllMoments().first()[0].id
        dao.markSeen(id, 9999L)
        val moment = dao.getAllMoments().first()[0]
        assertNotNull(moment.seenAt)
        assertEquals(9999L, moment.seenAt)
    }

    @Test
    fun existsByTypeAndKey_returnsTrueWhenPresent() = runTest {
        dao.insert(Moment(type = "STREAK_7", entityKey = "7", triggeredAt = 1000L,
            title = "T", description = "D"))
        assertEquals(true, dao.existsByTypeAndKey("STREAK_7", "7"))
        assertEquals(false, dao.existsByTypeAndKey("STREAK_7", "14"))
    }
}
