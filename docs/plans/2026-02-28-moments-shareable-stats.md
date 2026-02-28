# Moments & Shareable Stats Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a Moments system that detects interesting patterns in a user's listening history, surfaces them as a home screen feed, and lets users share stylized cards to social media.

**Architecture:** A `MomentDetector` (pure Kotlin, no Android deps) runs batch queries on app open (via HomeViewModel) and once daily via WorkManager, persisting new `Moment` rows to a dedicated Room table. New moments appear in a horizontal strip on the home screen and fire push notifications.

**Tech Stack:** Room (new entity + DAO), Hilt, WorkManager + hilt-work, Jetpack Compose (new composables), Android Notifications API

---

## Reference Files

Before starting, skim these files to understand existing patterns:
- `app/src/main/java/com/musicstats/app/data/model/Song.kt` — entity pattern
- `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt` — migration pattern
- `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt` — DI pattern
- `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt` — DAO + result class pattern
- `app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt` — ViewModel pattern
- `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt` — composable pattern
- `app/src/main/java/com/musicstats/app/ui/share/ShareCards.kt` — existing share card pattern
- `app/src/main/java/com/musicstats/app/MusicStatsApp.kt` — Application class

---

## Task 1: Add WorkManager dependency and Moment Room entity

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/musicstats/app/data/model/Moment.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt`

### Step 1: Add WorkManager + hilt-work to build.gradle.kts

In the `dependencies` block, add:
```kotlin
implementation("androidx.work:work-runtime-ktx:2.10.0")
implementation("androidx.hilt:hilt-work:1.2.0")
ksp("androidx.hilt:hilt-compiler:1.2.0")
```

### Step 2: Create `Moment.kt`

```kotlin
package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moments",
    indices = [Index(value = ["type", "entityKey"], unique = true)]
)
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,             // e.g. "SONG_PLAYS_100", "ARCHETYPE_NIGHT_OWL"
    val entityKey: String,        // idempotency key, unique per (type, key) pair
    val triggeredAt: Long,        // epoch millis
    val seenAt: Long? = null,     // null = unseen; set when user taps card
    val sharedAt: Long? = null,   // set when user shares
    val title: String,            // short hero label, e.g. "100 plays"
    val description: String,      // full punchy line, e.g. "You've played Blinding Lights 100 times"
    val songId: Long? = null,     // for album art + palette colors (nullable)
    val artistId: Long? = null    // for artist image (nullable)
)
```

### Step 3: Register entity and add MIGRATION_6_7 in `MusicStatsDatabase.kt`

Change the `@Database` annotation's `entities` list to include `Moment::class` and bump `version` to `7`:
```kotlin
@Database(
    entities = [Song::class, Artist::class, ListeningEvent::class, Moment::class],
    version = 7,
    exportSchema = false
)
```

Add a new DAO abstract function:
```kotlin
abstract fun momentDao(): MomentDao
```

Add `MIGRATION_6_7` in the companion object:
```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS moments (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                entityKey TEXT NOT NULL,
                triggeredAt INTEGER NOT NULL,
                seenAt INTEGER,
                sharedAt INTEGER,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                songId INTEGER,
                artistId INTEGER,
                UNIQUE(type, entityKey)
            )
        """)
    }
}
```

In `provideDatabase` inside `DatabaseModule.kt`, chain `.addMigrations(..., MusicStatsDatabase.MIGRATION_6_7)`.

### Step 4: Sync and verify build compiles

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL. Fix any compilation errors before proceeding.

### Step 5: Commit
```bash
git add app/build.gradle.kts app/src/main/java/com/musicstats/app/data/model/Moment.kt app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt
git commit -m "feat: add Moment entity, DB migration 6→7, WorkManager deps"
```

---

## Task 2: MomentDao and wire into DI

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/dao/MomentDao.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt`
- Create: `app/src/test/java/com/musicstats/app/data/dao/MomentDaoTest.kt`

### Step 1: Write the failing test

Create `MomentDaoTest.kt`:
```kotlin
package com.musicstats.app.data.dao

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.model.Moment
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

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
        val moment = Moment(type = "SONG_PLAYS_100", entityKey = "42:100", triggeredAt = 1000L,
            title = "100 plays", description = "desc")
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
```

### Step 2: Create `MomentDao.kt`

```kotlin
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

    @Query("SELECT * FROM moments ORDER BY triggeredAt DESC")
    fun getAllMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments WHERE seenAt IS NULL ORDER BY triggeredAt DESC")
    fun getUnseenMoments(): Flow<List<Moment>>

    @Query("SELECT * FROM moments ORDER BY triggeredAt DESC LIMIT :limit")
    fun getRecentMoments(limit: Int): Flow<List<Moment>>

    @Query("UPDATE moments SET seenAt = :timestamp WHERE id = :id")
    suspend fun markSeen(id: Long, timestamp: Long)

    @Query("UPDATE moments SET sharedAt = :timestamp WHERE id = :id")
    suspend fun markShared(id: Long, timestamp: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM moments WHERE type = :type AND entityKey = :entityKey)")
    suspend fun existsByTypeAndKey(type: String, entityKey: String): Boolean

    @Query("SELECT COUNT(*) FROM moments WHERE seenAt IS NULL")
    fun getUnseenCount(): Flow<Int>
}
```

### Step 3: Add DAO provider to `DatabaseModule.kt`

```kotlin
@Provides
fun provideMomentDao(database: MusicStatsDatabase): MomentDao = database.momentDao()
```

### Step 4: Run the tests

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.musicstats.app.data.dao.MomentDaoTest
```
Expected: All 5 tests PASS.

### Step 5: Commit
```bash
git add app/src/main/java/com/musicstats/app/data/dao/MomentDao.kt app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt app/src/androidTest/java/com/musicstats/app/data/dao/MomentDaoTest.kt
git commit -m "feat: add MomentDao with idempotency check and DI wiring"
```

---

## Task 3: Add suspend DAO queries needed by MomentDetector

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt`

The `MomentDetector` needs suspend (not Flow) versions of several queries. Read the existing `ListeningEventDao` and add only the queries that don't already exist as `suspend` functions.

### Step 1: Add the following suspend queries to `ListeningEventDao`

```kotlin
// Total listening time all-time (suspend, for detector)
@Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events WHERE completed = 1")
suspend fun getTotalListeningTimeMsSuspend(): Long

// Total play count all-time (suspend)
@Query("SELECT COUNT(*) FROM listening_events WHERE completed = 1")
suspend fun getTotalPlayCountSuspend(): Long

// Total skip count all-time (suspend)
@Query("SELECT COUNT(*) FROM listening_events WHERE completed = 0")
suspend fun getTotalSkipCountSuspend(): Long

// Unique song count (suspend)
@Query("SELECT COUNT(DISTINCT songId) FROM listening_events")
suspend fun getUniqueSongCountSuspend(): Int

// Unique artist count (suspend)
@Query("SELECT COUNT(DISTINCT s.artist) FROM listening_events e JOIN songs s ON e.songId = s.id")
suspend fun getUniqueArtistCountSuspend(): Int

// New artists since date (suspend)
@Query("""
    SELECT COUNT(DISTINCT s.artist) FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.startedAt >= :since
    AND s.firstHeardAt >= :since
""")
suspend fun getNewArtistsSinceSuspend(since: Long): Int

// Top songs by play count (suspend, for milestone + quick obsession detection)
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl,
           SUM(e.durationMs) as totalDurationMs,
           COUNT(*) as playCount
    FROM listening_events e JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1
    GROUP BY e.songId
    ORDER BY playCount DESC
    LIMIT :limit
""")
suspend fun getTopSongsByPlayCountSuspend(limit: Int): List<SongPlayStats>

// All songs with play count and first heard (for milestone + obsession detection)
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl, s.firstHeardAt,
           SUM(e.durationMs) as totalDurationMs,
           COUNT(*) as playCount
    FROM listening_events e JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1
    GROUP BY e.songId
    HAVING playCount >= :minPlays
""")
suspend fun getSongsWithMinPlays(minPlays: Int): List<SongWithStats>

// Top artists by duration (suspend, for artist hour milestones + loyal fan)
@Query("""
    SELECT s.artist, SUM(e.durationMs) as totalDurationMs, COUNT(*) as playCount
    FROM listening_events e JOIN songs s ON e.songId = s.id
    WHERE e.startedAt >= :since
    GROUP BY s.artist
    ORDER BY totalDurationMs DESC
    LIMIT :limit
""")
suspend fun getTopArtistsByDurationSuspend(since: Long, limit: Int): List<ArtistPlayStats>

// All artists with total duration all-time (for artist hour milestones)
@Query("""
    SELECT s.artist, SUM(e.durationMs) as totalDurationMs, COUNT(*) as playCount
    FROM listening_events e JOIN songs s ON e.songId = s.id
    GROUP BY s.artist
    ORDER BY totalDurationMs DESC
""")
suspend fun getAllArtistsWithDurationSuspend(): List<ArtistPlayStats>

// Hourly listening distribution for past N days (for archetype detection)
@Query("""
    SELECT strftime('%H', startedAt / 1000, 'unixepoch') * 1 as hour,
           SUM(durationMs) as totalDurationMs,
           COUNT(*) as eventCount
    FROM listening_events
    WHERE startedAt >= :since AND completed = 1
    GROUP BY hour
""")
suspend fun getHourlyListeningSuspend(since: Long): List<HourlyListening>

// Longest completed session ever
@Query("SELECT COALESCE(MAX(durationMs), 0) FROM listening_events WHERE completed = 1")
suspend fun getLongestSessionMs(): Long

// Artist skip count in time range (for breakup candidate detection)
@Query("""
    SELECT COUNT(*) FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE s.artist = :artist AND e.completed = 0 AND e.startedAt >= :since
""")
suspend fun getArtistSkipCountSince(artist: String, since: Long): Int

// Songs played on a specific day (for obsession daily + ritual detection)
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl,
           SUM(e.durationMs) as totalDurationMs,
           COUNT(*) as playCount
    FROM listening_events e JOIN songs s ON e.songId = s.id
    WHERE e.startedAt >= :dayStart AND e.startedAt < :dayEnd AND e.completed = 1
    GROUP BY e.songId
    ORDER BY playCount DESC
""")
suspend fun getSongsPlayedOnDay(dayStart: Long, dayEnd: Long): List<SongPlayStats>

// Days a specific song was played (for ritual detection - returns distinct day strings)
@Query("""
    SELECT DISTINCT date(startedAt / 1000, 'unixepoch') as day
    FROM listening_events
    WHERE songId = :songId AND completed = 1 AND startedAt >= :since
    ORDER BY day DESC
""")
suspend fun getDistinctDaysForSong(songId: Long, since: Long): List<String>

// Artist ID by name (needed for moment persistence)
@Query("SELECT id FROM artists WHERE name = :name LIMIT 1")
suspend fun getArtistIdByName(name: String): Long?
```

Note: `getArtistIdByName` goes in `ArtistDao`, not `ListeningEventDao`. Add it to `app/src/main/java/com/musicstats/app/data/dao/ArtistDao.kt`.

### Step 2: Build to verify no compile errors

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

### Step 3: Commit
```bash
git add app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt app/src/main/java/com/musicstats/app/data/dao/ArtistDao.kt
git commit -m "feat: add suspend DAO queries for MomentDetector"
```

---

## Task 4: MomentsRepository

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/repository/MomentsRepository.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt`

### Step 1: Create `MomentsRepository.kt`

```kotlin
package com.musicstats.app.data.repository

import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.model.Moment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentsRepository @Inject constructor(
    private val momentDao: MomentDao
) {
    fun getRecentMoments(limit: Int = 10): Flow<List<Moment>> =
        momentDao.getRecentMoments(limit)

    fun getUnseenCount(): Flow<Int> = momentDao.getUnseenCount()

    suspend fun markSeen(id: Long) = momentDao.markSeen(id, System.currentTimeMillis())

    suspend fun markShared(id: Long) = momentDao.markShared(id, System.currentTimeMillis())
}
```

### Step 2: Build
```bash
./gradlew assembleDebug
```

### Step 3: Commit
```bash
git add app/src/main/java/com/musicstats/app/data/repository/MomentsRepository.kt
git commit -m "feat: add MomentsRepository"
```

---

## Task 5: MomentDetector with full detection logic and unit tests

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`
- Create: `app/src/androidTest/java/com/musicstats/app/service/MomentDetectorTest.kt`

### Step 1: Write failing tests first

Create `MomentDetectorTest.kt`:
```kotlin
package com.musicstats.app.service

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.musicstats.app.data.MusicStatsDatabase
import com.musicstats.app.data.model.Artist
import com.musicstats.app.data.model.ListeningEvent
import com.musicstats.app.data.model.Song
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

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
        detector = MomentDetector(db.listeningEventDao(), db.songDao(), db.artistDao(), db.momentDao())
    }

    @After
    fun teardown() { db.close() }

    private suspend fun insertSongWithPlays(title: String, artist: String, playCount: Int): Long {
        val now = System.currentTimeMillis()
        db.artistDao().insert(Artist(name = artist, firstHeardAt = now - 1_000_000))
        val songId = db.songDao().insert(Song(
            title = title, artist = artist,
            firstHeardAt = now - 1_000_000
        ))
        repeat(playCount) { i ->
            db.listeningEventDao().insert(ListeningEvent(
                songId = songId,
                startedAt = now - (playCount - i) * 200_000L,
                durationMs = 200_000L,
                sourceApp = "test",
                completed = true
            ))
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
        val first = detector.detectAndPersistNewMoments()
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
        val songId = db.songDao().insert(Song(title = "T", artist = "Test Artist", firstHeardAt = thirtyDaysAgo))
        // Insert 10 completed + 50 skips
        repeat(10) { i ->
            db.listeningEventDao().insert(ListeningEvent(songId = songId,
                startedAt = thirtyDaysAgo + i * 3600_000L, durationMs = 200_000L,
                sourceApp = "test", completed = true))
        }
        repeat(50) { i ->
            db.listeningEventDao().insert(ListeningEvent(songId = songId,
                startedAt = thirtyDaysAgo + (10 + i) * 3600_000L, durationMs = 5_000L,
                sourceApp = "test", completed = false))
        }
        val moments = detector.detectAndPersistNewMoments()
        assertTrue(moments.any { it.type == "ARCHETYPE_CERTIFIED_SKIPPER" })
    }

    @Test
    fun detectsObsessionDaily_fivePlaysInOneDay() = runTest {
        val now = System.currentTimeMillis()
        val todayStart = now - (now % 86_400_000L)
        db.artistDao().insert(Artist(name = "Artist", firstHeardAt = now - 1_000_000))
        val songId = db.songDao().insert(Song(title = "Obsession", artist = "Artist", firstHeardAt = now - 1_000_000))
        repeat(6) { i ->
            db.listeningEventDao().insert(ListeningEvent(songId = songId,
                startedAt = todayStart + i * 3_600_000L, durationMs = 200_000L,
                sourceApp = "test", completed = true))
        }
        val moments = detector.detectAndPersistNewMoments()
        assertTrue(moments.any { it.type == "OBSESSION_DAILY" })
    }
}
```

### Step 2: Run tests to verify they fail

```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.musicstats.app.service.MomentDetectorTest
```
Expected: FAIL — `MomentDetector` does not exist yet.

### Step 3: Create `MomentDetector.kt`

```kotlin
package com.musicstats.app.service

import com.musicstats.app.data.dao.ArtistDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.dao.SongDao
import com.musicstats.app.data.model.Moment
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentDetector @Inject constructor(
    private val eventDao: ListeningEventDao,
    private val songDao: SongDao,
    private val artistDao: ArtistDao,
    private val momentDao: MomentDao
) {

    companion object {
        val SONG_PLAY_THRESHOLDS = listOf(10, 25, 50, 100, 250, 500)
        val ARTIST_HOUR_THRESHOLDS_MS = listOf(1L, 5L, 10L, 24L).map { it * 3_600_000L }
        val STREAK_THRESHOLDS = listOf(3, 7, 14, 30, 100)
        val TOTAL_HOUR_THRESHOLDS_MS = listOf(24L, 100L, 500L, 1000L).map { it * 3_600_000L }
        val DISCOVERY_THRESHOLDS = listOf(50, 100, 250, 500)
    }

    /** Detect all new moments since the beginning of time, persist them, return only newly inserted ones. */
    suspend fun detectAndPersistNewMoments(): List<Moment> {
        val newMoments = mutableListOf<Moment>()
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 3600 * 1000
        val sevenDaysAgo = now - 7L * 24 * 3600 * 1000
        val todayStart = startOfDay(now)
        val yearMonth = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM"))

        newMoments += detectSongPlayMilestones()
        newMoments += detectArtistHourMilestones()
        newMoments += detectStreakMilestones()
        newMoments += detectTotalHourMilestones(now)
        newMoments += detectDiscoveryMilestones()
        newMoments += detectArchetypes(thirtyDaysAgo, yearMonth)
        newMoments += detectObsessionDaily(todayStart, now)
        newMoments += detectDailyRitual(sevenDaysAgo, now)
        newMoments += detectBreakupCandidate(sevenDaysAgo, now)
        newMoments += detectFastObsession(now)
        newMoments += detectLongestSession(now)
        newMoments += detectArtistUnlocked(sevenDaysAgo, now)
        newMoments += detectQuickObsession(sevenDaysAgo, now)
        newMoments += detectDiscoveryWeek(sevenDaysAgo, now, yearMonth)

        return newMoments
    }

    private suspend fun persistIfNew(moment: Moment): Moment? {
        if (momentDao.existsByTypeAndKey(moment.type, moment.entityKey)) return null
        momentDao.insert(moment)
        return moment
    }

    private suspend fun detectSongPlayMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val songs = eventDao.getSongsWithMinPlays(SONG_PLAY_THRESHOLDS.first())
        for (song in songs) {
            for (threshold in SONG_PLAY_THRESHOLDS) {
                if (song.playCount >= threshold) {
                    val entityKey = "${song.songId}:$threshold"
                    persistIfNew(Moment(
                        type = "SONG_PLAYS_$threshold",
                        entityKey = entityKey,
                        triggeredAt = System.currentTimeMillis(),
                        title = "$threshold plays",
                        description = "You've played ${song.title} $threshold times",
                        songId = song.songId
                    ))?.let { result += it }
                }
            }
        }
        return result
    }

    private suspend fun detectArtistHourMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val artists = eventDao.getAllArtistsWithDurationSuspend()
        for (artist in artists) {
            val artistId = artistDao.findByName(artist.artist)?.id
            for (thresholdMs in ARTIST_HOUR_THRESHOLDS_MS) {
                if (artist.totalDurationMs >= thresholdMs) {
                    val hours = thresholdMs / 3_600_000L
                    val entityKey = "${artist.artist}:$hours"
                    val humanHours = if (hours == 1L) "1 hour" else "$hours hours"
                    persistIfNew(Moment(
                        type = "ARTIST_HOURS_$hours",
                        entityKey = entityKey,
                        triggeredAt = System.currentTimeMillis(),
                        title = "$humanHours of ${artist.artist}",
                        description = "You've spent $humanHours listening to ${artist.artist}",
                        artistId = artistId
                    ))?.let { result += it }
                }
            }
        }
        return result
    }

    private suspend fun detectStreakMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val streak = computeStreak()
        for (threshold in STREAK_THRESHOLDS) {
            if (streak >= threshold) {
                persistIfNew(Moment(
                    type = "STREAK_$threshold",
                    entityKey = "$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = "$threshold-day streak",
                    description = "$threshold days in a row — you're on fire"
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectTotalHourMilestones(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        for (thresholdMs in TOTAL_HOUR_THRESHOLDS_MS) {
            if (totalMs >= thresholdMs) {
                val hours = thresholdMs / 3_600_000L
                persistIfNew(Moment(
                    type = "TOTAL_HOURS_$hours",
                    entityKey = "$hours",
                    triggeredAt = now,
                    title = "$hours hours",
                    description = "You've listened to ${hours}h of music in total"
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectDiscoveryMilestones(): List<Moment> {
        val result = mutableListOf<Moment>()
        val uniqueSongs = eventDao.getUniqueSongCountSuspend()
        for (threshold in DISCOVERY_THRESHOLDS) {
            if (uniqueSongs >= threshold) {
                persistIfNew(Moment(
                    type = "SONGS_DISCOVERED_$threshold",
                    entityKey = "$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = "$threshold songs",
                    description = "You've discovered $threshold unique songs"
                ))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectArchetypes(since: Long, yearMonth: String): List<Moment> {
        val result = mutableListOf<Moment>()
        val now = System.currentTimeMillis()

        // Hourly distribution
        val hourly = eventDao.getHourlyListeningSuspend(since)
        val totalMs = hourly.sumOf { it.totalDurationMs }.coerceAtLeast(1L)
        val nightMs = hourly.filter { it.hour in listOf(22, 23, 0, 1, 2, 3) }.sumOf { it.totalDurationMs }
        val morningMs = hourly.filter { it.hour in 5..8 }.sumOf { it.totalDurationMs }
        val commuteAmMs = hourly.filter { it.hour in 7..8 }.sumOf { it.totalDurationMs }
        val commutePmMs = hourly.filter { it.hour in 17..18 }.sumOf { it.totalDurationMs }

        if (nightMs.toDouble() / totalMs > 0.5) {
            persistIfNew(Moment(type = "ARCHETYPE_NIGHT_OWL", entityKey = yearMonth, triggeredAt = now,
                title = "Night Owl", description = "You do most of your listening after 10pm"))?.let { result += it }
        }
        if (morningMs.toDouble() / totalMs > 0.5) {
            persistIfNew(Moment(type = "ARCHETYPE_MORNING_LISTENER", entityKey = yearMonth, triggeredAt = now,
                title = "Morning Listener", description = "You do most of your listening before 9am"))?.let { result += it }
        }
        val totalHourlyMs = hourly.sumOf { it.totalDurationMs }
        if (totalHourlyMs > 0 && commuteAmMs > 0 && commutePmMs > 0 &&
            (commuteAmMs + commutePmMs).toDouble() / totalHourlyMs > 0.3) {
            persistIfNew(Moment(type = "ARCHETYPE_COMMUTE_LISTENER", entityKey = yearMonth, triggeredAt = now,
                title = "Commute Listener", description = "Your listening peaks at 7–9am and 5–7pm"))?.let { result += it }
        }

        // Skip rate (all-time)
        val totalPlays = eventDao.getTotalPlayCountSuspend().coerceAtLeast(1)
        val totalSkips = eventDao.getTotalSkipCountSuspend()
        val skipRate = totalSkips.toDouble() / (totalPlays + totalSkips)
        if (skipRate < 0.05) {
            persistIfNew(Moment(type = "ARCHETYPE_COMPLETIONIST", entityKey = yearMonth, triggeredAt = now,
                title = "Completionist", description = "You skip less than 5% of songs — truly dedicated"))?.let { result += it }
        }
        if (skipRate > 0.40) {
            persistIfNew(Moment(type = "ARCHETYPE_CERTIFIED_SKIPPER", entityKey = yearMonth, triggeredAt = now,
                title = "Certified Skipper", description = "You skip more than 40% of songs. Nothing is good enough."))?.let { result += it }
        }

        // Deep cuts
        val deepCuts = eventDao.getSongsWithMinPlays(50)
        if (deepCuts.isNotEmpty()) {
            persistIfNew(Moment(type = "ARCHETYPE_DEEP_CUT_DIGGER", entityKey = yearMonth, triggeredAt = now,
                title = "Deep Cut Digger", description = "You've listened to ${deepCuts[0].title} over 50 times"))?.let { result += it }
        }

        // Loyal fan
        val topArtists = eventDao.getTopArtistsByDurationSuspend(0L, 1)
        if (topArtists.isNotEmpty()) {
            val topMs = topArtists[0].totalDurationMs
            val allMs = eventDao.getTotalListeningTimeMsSuspend().coerceAtLeast(1L)
            if (topMs.toDouble() / allMs > 0.5) {
                val artistId = artistDao.findByName(topArtists[0].artist)?.id
                persistIfNew(Moment(type = "ARCHETYPE_LOYAL_FAN", entityKey = yearMonth, triggeredAt = now,
                    title = "Loyal Fan", description = "Over 50% of your listening is ${topArtists[0].artist}",
                    artistId = artistId))?.let { result += it }
            }
        }

        // Explorer
        val newArtistsThisWeek = eventDao.getNewArtistsSinceSuspend(now - 7L * 24 * 3600 * 1000)
        if (newArtistsThisWeek >= 5) {
            persistIfNew(Moment(type = "ARCHETYPE_EXPLORER", entityKey = yearMonth, triggeredAt = now,
                title = "Explorer", description = "You discovered $newArtistsThisWeek new artists this week"))?.let { result += it }
        }

        return result
    }

    private suspend fun detectObsessionDaily(todayStart: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val todayEnd = todayStart + 86_400_000L
        val todaySongs = eventDao.getSongsPlayedOnDay(todayStart, todayEnd)
        val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()
        for (song in todaySongs) {
            if (song.playCount >= 5) {
                persistIfNew(Moment(type = "OBSESSION_DAILY",
                    entityKey = "${song.songId}:$todayDate",
                    triggeredAt = now,
                    title = "${song.playCount}x in one day",
                    description = "You played ${song.title} ${song.playCount} times today. Are you okay?",
                    songId = song.songId))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectDailyRitual(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val recentSongs = eventDao.getSongsWithMinPlays(7)
        val last7Days = (0 until 7).map {
            LocalDate.now(ZoneId.systemDefault()).minusDays(it.toLong()).toString()
        }.toSet()
        val detectedDate = LocalDate.now(ZoneId.systemDefault()).toString()
        for (song in recentSongs) {
            val days = eventDao.getDistinctDaysForSong(song.songId, sevenDaysAgo).toSet()
            if (days.containsAll(last7Days)) {
                persistIfNew(Moment(type = "DAILY_RITUAL",
                    entityKey = "${song.songId}:$detectedDate",
                    triggeredAt = now,
                    title = "Daily ritual",
                    description = "You've listened to ${song.title} every day for 7 days",
                    songId = song.songId))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectBreakupCandidate(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val allArtists = eventDao.getAllArtistsWithDurationSuspend()
        for (artistStats in allArtists) {
            val skipCount = eventDao.getArtistSkipCountSince(artistStats.artist, sevenDaysAgo)
            if (skipCount >= 10) {
                val artistId = artistDao.findByName(artistStats.artist)?.id
                persistIfNew(Moment(type = "BREAKUP_CANDIDATE",
                    entityKey = "${artistStats.artist}:$weekKey",
                    triggeredAt = now,
                    title = "Maybe break up?",
                    description = "You've skipped ${artistStats.artist} $skipCount times this week",
                    artistId = artistId))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectFastObsession(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val thirtyDaysAgo = now - 30L * 24 * 3600 * 1000
        val songs = eventDao.getSongsWithMinPlays(20)
        for (song in songs) {
            val ageMs = now - song.firstHeardAt
            if (ageMs < 30L * 24 * 3600 * 1000 && ageMs > 0) {
                val ageDays = ageMs / 86_400_000L
                persistIfNew(Moment(type = "FAST_OBSESSION",
                    entityKey = "${song.songId}",
                    triggeredAt = now,
                    title = "${song.playCount} plays in $ageDays days",
                    description = "${song.title} came into your life $ageDays days ago. You've played it ${song.playCount} times.",
                    songId = song.songId))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectLongestSession(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val longestMs = eventDao.getLongestSessionMs()
        if (longestMs >= 3_600_000L) { // only bother if >= 1 hour
            val entityKey = "$longestMs" // fires once per unique record length
            val hours = longestMs / 3_600_000L
            val mins = (longestMs % 3_600_000L) / 60_000L
            val label = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
            persistIfNew(Moment(type = "LONGEST_SESSION",
                entityKey = entityKey,
                triggeredAt = now,
                title = "New record: $label",
                description = "New personal best: $label in one sitting"))?.let { result += it }
        }
        return result
    }

    private suspend fun detectArtistUnlocked(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val newArtists = artistDao.getArtistsSince(sevenDaysAgo) // returns List<Artist>
        for (artist in newArtists) {
            persistIfNew(Moment(type = "ARTIST_UNLOCKED",
                entityKey = "${artist.id}",
                triggeredAt = now,
                title = "New artist",
                description = "New artist unlocked: ${artist.name}",
                artistId = artist.id))?.let { result += it }
        }
        return result
    }

    private suspend fun detectQuickObsession(sevenDaysAgo: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val top5 = eventDao.getTopSongsByPlayCountSuspend(5)
        for (song in top5) {
            val songDetails = eventDao.getSongsWithMinPlays(1).find { it.songId == song.songId }
            if (songDetails != null && songDetails.firstHeardAt >= sevenDaysAgo) {
                val ageDays = (now - songDetails.firstHeardAt) / 86_400_000L
                persistIfNew(Moment(type = "QUICK_OBSESSION",
                    entityKey = "${song.songId}",
                    triggeredAt = now,
                    title = "Fast obsession",
                    description = "You discovered ${song.title} $ageDays days ago. It's already in your top 5.",
                    songId = song.songId))?.let { result += it }
            }
        }
        return result
    }

    private suspend fun detectDiscoveryWeek(sevenDaysAgo: Long, now: Long, yearMonth: String): List<Moment> {
        val result = mutableListOf<Moment>()
        val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
        val newArtistsCount = eventDao.getNewArtistsSinceSuspend(sevenDaysAgo)
        if (newArtistsCount >= 8) {
            persistIfNew(Moment(type = "DISCOVERY_WEEK",
                entityKey = weekKey,
                triggeredAt = now,
                title = "$newArtistsCount new artists",
                description = "You discovered $newArtistsCount new artists this week"))?.let { result += it }
        }
        return result
    }

    // Computes current consecutive listening day streak
    private suspend fun computeStreak(): Int {
        val now = System.currentTimeMillis()
        var streak = 0
        var checkDay = LocalDate.now(ZoneId.systemDefault())
        while (true) {
            val dayStart = checkDay.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = dayStart + 86_400_000L
            val count = eventDao.getSongsPlayedOnDay(dayStart, dayEnd).size
            if (count == 0) break
            streak++
            checkDay = checkDay.minusDays(1)
            if (streak > 365) break // safety limit
        }
        return streak
    }

    private fun startOfDay(epochMs: Long): Long {
        val date = LocalDate.ofEpochDay(epochMs / 86_400_000L)
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
```

Also add `getArtistsSince` to `ArtistDao.kt`:
```kotlin
@Query("SELECT * FROM artists WHERE firstHeardAt >= :since ORDER BY firstHeardAt DESC")
suspend fun getArtistsSince(since: Long): List<Artist>
```

### Step 4: Run tests
```bash
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.musicstats.app.service.MomentDetectorTest
```
Expected: All 5 tests PASS.

### Step 5: Commit
```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt app/src/main/java/com/musicstats/app/data/dao/ArtistDao.kt app/src/androidTest/java/com/musicstats/app/service/MomentDetectorTest.kt
git commit -m "feat: add MomentDetector with full moment detection logic and tests"
```

---

## Task 6: MomentWorker, HiltWorkerFactory, and notification channel

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/MomentWorker.kt`
- Modify: `app/src/main/java/com/musicstats/app/MusicStatsApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`

### Step 1: Create `MomentWorker.kt`

```kotlin
package com.musicstats.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.musicstats.app.R
import com.musicstats.app.data.model.Moment
import com.musicstats.app.ui.MainActivity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MomentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val detector: MomentDetector
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val newMoments = detector.detectAndPersistNewMoments()
            newMoments.forEach { fireNotification(it) }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fireNotification(moment: Moment) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        ensureChannel(nm)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(applicationContext, moment.id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(moment.title)
            .setContentText(moment.description)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .build()
        nm.notify(moment.id.toInt(), notification)
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Moments", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Music listening milestones and insights" }
            nm.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "moments"
        const val GROUP_KEY = "com.musicstats.moments"
        const val WORK_NAME = "moment_detection_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MomentWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

### Step 2: Update `MusicStatsApp.kt` to provide HiltWorkerFactory

Read the existing `MusicStatsApp.kt`. Add `Configuration.Provider` interface so Hilt can inject workers:

```kotlin
@HiltAndroidApp
class MusicStatsApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

Add the required import: `import androidx.work.Configuration` and `import androidx.hilt.work.HiltWorkerFactory`.

### Step 3: Disable WorkManager default initialization in `AndroidManifest.xml`

Inside `<application>`, add:
```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="androidx.work.WorkManagerInitializer"
        android:value="androidx.startup"
        tools:node="remove" />
</provider>
```

Add `xmlns:tools="http://schemas.android.com/tools"` to the root `<manifest>` tag if not already present.

### Step 4: Schedule the worker from HomeViewModel.init

In `HomeViewModel.kt`, inject `@ApplicationContext context: Context` and call:
```kotlin
init {
    repository.backfillArtistImages()
    repository.backfillAlbumArt()
    repository.backfillPaletteColors()
    MomentWorker.schedule(context) // schedule daily background job
}
```

Adjust the `@HiltViewModel` constructor: `@HiltViewModel class HomeViewModel @Inject constructor(..., @ApplicationContext private val context: Context)`.

### Step 5: Build and verify

```bash
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL.

### Step 6: Commit
```bash
git add app/src/main/java/com/musicstats/app/service/MomentWorker.kt app/src/main/java/com/musicstats/app/MusicStatsApp.kt app/src/main/AndroidManifest.xml app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt
git commit -m "feat: add MomentWorker with daily background detection and push notifications"
```

---

## Task 7: HomeViewModel — on-open moment detection + moments StateFlow

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt`

### Step 1: Add moment detection and StateFlow to `HomeViewModel`

Add these injected dependencies to the constructor:
```kotlin
private val momentDetector: MomentDetector,
private val momentsRepository: MomentsRepository,
```

Add these StateFlow properties:
```kotlin
val recentMoments: StateFlow<List<Moment>> =
    momentsRepository.getRecentMoments(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

val unseenMomentsCount: StateFlow<Int> =
    momentsRepository.getUnseenCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

Add these functions:
```kotlin
fun detectMomentsOnOpen() {
    viewModelScope.launch(Dispatchers.IO) {
        momentDetector.detectAndPersistNewMoments()
    }
}

fun markMomentSeen(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        momentsRepository.markSeen(id)
    }
}

fun markMomentShared(id: Long) {
    viewModelScope.launch(Dispatchers.IO) {
        momentsRepository.markShared(id)
    }
}
```

### Step 2: Call `detectMomentsOnOpen()` from HomeScreen using `LaunchedEffect`

In `HomeScreen.kt`, after the other `collectAsState()` calls, add:
```kotlin
val recentMoments by viewModel.recentMoments.collectAsState()

LaunchedEffect(Unit) {
    viewModel.detectMomentsOnOpen()
}
```

### Step 3: Build
```bash
./gradlew assembleDebug
```

### Step 4: Commit
```bash
git add app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt
git commit -m "feat: detect moments on app open, expose recentMoments StateFlow"
```

---

## Task 8: MomentShareCard composable

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt`

### Step 1: Create `MomentShareCard.kt`

This composable renders a 360×640 shareable card. It uses album art palette colors when a `songId` is available, and themed gradient colors for archetypes.

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.musicstats.app.data.model.Moment

/**
 * A 360×640 shareable card for a Moment.
 *
 * @param moment The moment to display.
 * @param dominantColor Optional palette color from the associated song's album art.
 * @param darkMutedColor Optional palette color from the associated song's album art.
 * @param imageUrl Optional album art or artist image URL.
 */
@Composable
fun MomentShareCard(
    moment: Moment,
    dominantColor: Color? = null,
    darkMutedColor: Color? = null,
    imageUrl: String? = null
) {
    val isArchetype = moment.type.startsWith("ARCHETYPE_")

    val (gradientStart, gradientEnd) = if (dominantColor != null && darkMutedColor != null) {
        dominantColor to darkMutedColor
    } else {
        archetypeGradient(moment.type)
    }

    val textColor = Color.White

    Box(
        modifier = Modifier
            .width(360.dp)
            .height(640.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(gradientStart, gradientEnd),
                    start = Offset.Zero,
                    end = Offset(360f * 2, 640f * 2)
                )
            )
            .padding(32.dp)
    ) {
        // Album art or artist image — top right
        if (!isArchetype && imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .align(Alignment.TopEnd),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(8.dp))

            // Hero section
            Column {
                if (isArchetype) {
                    Text(
                        text = archetypeEmoji(moment.type),
                        fontSize = 72.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                Text(
                    text = moment.title,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = textColor,
                    lineHeight = 52.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = moment.description,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Watermark
            Text(
                text = "vibes",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
                color = textColor.copy(alpha = 0.35f),
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

private fun archetypeGradient(type: String): Pair<Color, Color> = when {
    type.contains("NIGHT_OWL") -> Color(0xFF1A1A2E) to Color(0xFF16213E)
    type.contains("MORNING") -> Color(0xFFFF6B35) to Color(0xFFFF8C42)
    type.contains("COMMUTE") -> Color(0xFF2D6A4F) to Color(0xFF1B4332)
    type.contains("COMPLETIONIST") -> Color(0xFF6B2D8B) to Color(0xFF4A1E6E)
    type.contains("SKIPPER") -> Color(0xFFD62828) to Color(0xFF9B1B1B)
    type.contains("DEEP_CUT") -> Color(0xFF0D3B66) to Color(0xFF051E3E)
    type.contains("LOYAL_FAN") -> Color(0xFFE63946) to Color(0xFFA4262E)
    type.contains("EXPLORER") -> Color(0xFF2B9348) to Color(0xFF1A5C2A)
    else -> Color(0xFF333333) to Color(0xFF111111)
}

private fun archetypeEmoji(type: String): String = when {
    type.contains("NIGHT_OWL") -> "🌙"
    type.contains("MORNING") -> "☀️"
    type.contains("COMMUTE") -> "🎧"
    type.contains("COMPLETIONIST") -> "✅"
    type.contains("SKIPPER") -> "⏭️"
    type.contains("DEEP_CUT") -> "💿"
    type.contains("LOYAL_FAN") -> "❤️"
    type.contains("EXPLORER") -> "🧭"
    else -> "🎵"
}
```

### Step 2: Build
```bash
./gradlew assembleDebug
```

### Step 3: Commit
```bash
git add app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt
git commit -m "feat: add MomentShareCard composable with palette-driven gradients"
```

---

## Task 9: MomentsStrip composable and HomeScreen integration

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/home/MomentsStrip.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

### Step 1: Create `MomentsStrip.kt`

```kotlin
package com.musicstats.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musicstats.app.data.model.Moment

/**
 * Horizontal strip of moment cards shown on the home screen.
 *
 * @param moments List of recent moments to display.
 * @param onMomentTap Called with the moment when user taps a card (opens share preview).
 * @param onSeeAll Called when "See all" is tapped.
 */
@Composable
fun MomentsStrip(
    moments: List<Moment>,
    onMomentTap: (Moment) -> Unit,
    onSeeAll: () -> Unit
) {
    if (moments.isEmpty()) return

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Moments",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            TextButton(onClick = onSeeAll) {
                Text("See all", style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(moments.take(5), key = { it.id }) { moment ->
                MomentFeedCard(
                    moment = moment,
                    onTap = { onMomentTap(moment) }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun MomentFeedCard(
    moment: Moment,
    onTap: () -> Unit
) {
    val isUnseen = moment.seenAt == null
    val isArchetype = moment.type.startsWith("ARCHETYPE_")

    Box(
        modifier = Modifier
            .width(180.dp)
            .height(100.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                )
            )
            .clickable(onClick = onTap)
            .then(
                if (isUnseen) Modifier.border(
                    1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)
                ) else Modifier
            )
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                if (isArchetype) {
                    Text(archetypeEmojiSmall(moment.type), fontSize = 20.sp)
                }
                if (isUnseen) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.Top)
                    )
                }
            }
            Column {
                Text(
                    text = moment.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = moment.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun archetypeEmojiSmall(type: String): String = when {
    type.contains("NIGHT_OWL") -> "🌙"
    type.contains("MORNING") -> "☀️"
    type.contains("COMMUTE") -> "🎧"
    type.contains("COMPLETIONIST") -> "✅"
    type.contains("SKIPPER") -> "⏭️"
    type.contains("DEEP_CUT") -> "💿"
    type.contains("LOYAL_FAN") -> "❤️"
    type.contains("EXPLORER") -> "🧭"
    else -> "🎵"
}
```

### Step 2: Wire MomentsStrip into HomeScreen

In `HomeScreen.kt`, in the main `Column` / `LazyColumn`, insert `MomentsStrip` **above** the `ListeningTimeChart` section. Find the section header for the weekly chart and add before it:

```kotlin
// Moments strip
MomentsStrip(
    moments = recentMoments,
    onMomentTap = { moment ->
        viewModel.markMomentSeen(moment.id)
        selectedMoment = moment
        showMomentShareSheet = true
    },
    onSeeAll = { /* TODO: navigate to moments list - future task */ }
)
```

Add state variables near the top of `HomeScreen`:
```kotlin
var selectedMoment by remember { mutableStateOf<Moment?>(null) }
var showMomentShareSheet by remember { mutableStateOf(false) }
```

Add the share sheet at the bottom of `HomeScreen` (after the existing `ShareBottomSheet`):
```kotlin
if (showMomentShareSheet && selectedMoment != null) {
    val moment = selectedMoment!!
    ShareBottomSheet(
        onDismiss = { showMomentShareSheet = false },
        onShare = { bitmap ->
            viewModel.markMomentShared(moment.id)
            // share bitmap via ShareCompat — same pattern as existing share FAB
            showMomentShareSheet = false
        },
        cardContent = {
            MomentShareCard(moment = moment)
        }
    )
}
```

Note: Check how the existing `ShareBottomSheet` accepts its card content. Adapt the call signature to match the existing pattern in the codebase. If `ShareBottomSheet` doesn't accept a `cardContent` lambda, create a wrapper or use the existing `ShareCardRenderer` pattern to render `MomentShareCard` offscreen and pass the bitmap.

### Step 3: Build and install on device

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Manually verify:
1. Insert some test listening events (or use existing data)
2. Open the app — moments strip should appear if moments were detected
3. Tap a moment card — share sheet should open with the styled card
4. Verify the unseen badge dot disappears after tapping

### Step 4: Commit
```bash
git add app/src/main/java/com/musicstats/app/ui/home/MomentsStrip.kt app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt
git commit -m "feat: add MomentsStrip to home screen with share flow integration"
```

---

## Summary

After completing all 9 tasks, the Moments system is fully functional:

| # | Task | Key output |
|---|---|---|
| 1 | WorkManager dep + Moment entity | `Moment.kt`, DB v7, WorkManager dep |
| 2 | MomentDao + DI | `MomentDao.kt`, wired into Room |
| 3 | MomentsRepository | `MomentsRepository.kt` |
| 4 | Suspend DAO queries | New queries in `ListeningEventDao` + `ArtistDao` |
| 5 | MomentDetector + tests | `MomentDetector.kt` + `MomentDetectorTest.kt` |
| 6 | MomentWorker + notifications | `MomentWorker.kt`, HiltWorkerFactory, channel setup |
| 7 | HomeViewModel integration | Detection on open, `recentMoments` StateFlow |
| 8 | MomentShareCard | `MomentShareCard.kt`, palette-driven gradients |
| 9 | MomentsStrip + HomeScreen | `MomentsStrip.kt`, wired share flow |
