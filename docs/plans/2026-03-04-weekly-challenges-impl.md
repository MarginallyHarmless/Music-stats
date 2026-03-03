# Weekly Challenges Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a pattern-reactive weekly challenge system that generates 2-3 personalized listening challenges every Monday based on the user's actual listening patterns.

**Architecture:** A `ChallengeGenerator` service runs 14 generator functions against last week's listening data, randomly selects 2-3 applicable challenges (with a one-week cooldown per type), and persists them via `ChallengeDao`. Progress is recalculated from existing `ListeningEvent` data on app open and by a daily `ChallengeWorker`. Challenges are shown as a strip on the Home screen with a detail bottom sheet.

**Tech Stack:** Room (entity + DAO + migration), Hilt DI, WorkManager, Jetpack Compose + Material 3, Kotlin Coroutines + Flow.

**Design doc:** `docs/plans/2026-03-04-weekly-challenges-design.md`

---

### Task 1: Challenge Entity + Room Migration

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/model/Challenge.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt`

**Step 1: Create the Challenge entity**

Create `app/src/main/java/com/musicstats/app/data/model/Challenge.kt`:

```kotlin
package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "challenges",
    indices = [Index(value = ["type", "weekStart"], unique = true)]
)
data class Challenge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val weekStart: Long,
    val title: String,
    val description: String,
    val targetValue: Float,
    val currentValue: Float = 0f,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val generatedAt: Long,
    val metadata: String? = null
)
```

**Step 2: Add MIGRATION_17_18 and register the entity in MusicStatsDatabase.kt**

In `MusicStatsDatabase.kt`:
- Add `Challenge::class` to the `entities` array in `@Database`
- Bump `version` from 17 to 18
- Add `import com.musicstats.app.data.model.Challenge`
- Add `abstract fun challengeDao(): ChallengeDao` (will compile after Task 2)
- Add this migration in the companion object:

```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS challenges (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                weekStart INTEGER NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                targetValue REAL NOT NULL,
                currentValue REAL NOT NULL DEFAULT 0,
                completed INTEGER NOT NULL DEFAULT 0,
                completedAt INTEGER,
                generatedAt INTEGER NOT NULL,
                metadata TEXT
            )
        """)
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_challenges_type_weekStart` ON `challenges` (`type`, `weekStart`)"
        )
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/model/Challenge.kt app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt
git commit -m "feat: add Challenge entity and MIGRATION_17_18"
```

---

### Task 2: ChallengeDao

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/dao/ChallengeDao.kt`

**Step 1: Create ChallengeDao**

```kotlin
package com.musicstats.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musicstats.app.data.model.Challenge
import kotlinx.coroutines.flow.Flow

@Dao
interface ChallengeDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(challenge: Challenge): Long

    @Query("SELECT * FROM challenges WHERE weekStart = :weekStart ORDER BY id ASC")
    fun getChallengesForWeek(weekStart: Long): Flow<List<Challenge>>

    @Query("SELECT * FROM challenges WHERE weekStart = :weekStart ORDER BY id ASC")
    suspend fun getChallengesForWeekSuspend(weekStart: Long): List<Challenge>

    @Query("SELECT * FROM challenges ORDER BY weekStart DESC, id ASC")
    fun getAllChallenges(): Flow<List<Challenge>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun getById(id: Long): Challenge?

    @Query("UPDATE challenges SET currentValue = :currentValue, completed = :completed, completedAt = :completedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, currentValue: Float, completed: Boolean, completedAt: Long?)

    @Query("SELECT DISTINCT type FROM challenges WHERE weekStart = :prevWeekStart")
    suspend fun getTypesForWeek(prevWeekStart: Long): List<String>

    @Query("SELECT COUNT(*) FROM challenges WHERE weekStart = :weekStart")
    suspend fun countForWeek(weekStart: Long): Int
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/ChallengeDao.kt
git commit -m "feat: add ChallengeDao with queries for weekly challenges"
```

---

### Task 3: Wire ChallengeDao into Hilt DI

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt` (add abstract fun if not done in Task 1)

**Step 1: Add ChallengeDao provider in DatabaseModule.kt**

Add after the `provideMomentDao` function:

```kotlin
@Provides
fun provideChallengeDao(database: MusicStatsDatabase): ChallengeDao = database.challengeDao()
```

Add the import:
```kotlin
import com.musicstats.app.data.dao.ChallengeDao
```

**Step 2: Register MIGRATION_17_18 in the `provideDatabase` builder**

In the `Room.databaseBuilder` chain, add:
```kotlin
.addMigrations(
    // ... existing migrations ...
    MusicStatsDatabase.MIGRATION_16_17,
    MusicStatsDatabase.MIGRATION_17_18,
)
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt
git commit -m "feat: wire ChallengeDao into Hilt DI module"
```

---

### Task 4: ChallengeDao Tests

**Files:**
- Create: `app/src/androidTest/java/com/musicstats/app/data/dao/ChallengeDaoTest.kt`

**Step 1: Write ChallengeDao tests**

```kotlin
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

    private val mondayMs = 1709510400000L // a Monday epoch millis

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
        dao.insert(challenge()) // same type + weekStart
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
```

**Step 2: Run tests**

Run: `./gradlew connectedAndroidTest --tests "*.ChallengeDaoTest"` (requires connected device/emulator)

Expected: All 7 tests PASS.

**Step 3: Commit**

```bash
git add app/src/androidTest/java/com/musicstats/app/data/dao/ChallengeDaoTest.kt
git commit -m "test: add ChallengeDao tests"
```

---

### Task 5: New DAO Queries for Challenge Generators

Some generators need queries that don't exist yet. Add suspend variants to `ListeningEventDao`.

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt`

**Step 1: Add new suspend queries**

Add these queries to `ListeningEventDao`:

```kotlin
// New songs discovered between two timestamps
@Query("SELECT COUNT(*) FROM songs WHERE firstHeardAt >= :from AND firstHeardAt < :until")
suspend fun getNewSongsDiscoveredBetweenSuspend(from: Long, until: Long): Int

// New artists discovered between two timestamps
@Query("SELECT COUNT(*) FROM artists WHERE firstHeardAt >= :from AND firstHeardAt < :until")
suspend fun getNewArtistsDiscoveredBetweenSuspend(from: Long, until: Long): Int

// Total events (completed + skipped) in a period — for skip rate calculation
@Query("SELECT COUNT(*) FROM listening_events WHERE startedAt >= :from AND startedAt < :until")
suspend fun getTotalEventCountBetweenSuspend(from: Long, until: Long): Int

// Skip count in a period
@Query("SELECT COUNT(*) FROM listening_events WHERE completed = 0 AND startedAt >= :from AND startedAt < :until")
suspend fun getSkipCountBetweenSuspend(from: Long, until: Long): Int

// Completed play count in a period
@Query("SELECT COUNT(*) FROM listening_events WHERE completed = 1 AND startedAt >= :from AND startedAt < :until")
suspend fun getPlayCountBetweenSuspend(from: Long, until: Long): Int

// Unique artist count in a period
@Query("""
    SELECT COUNT(DISTINCT s.artist) FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1 AND e.startedAt >= :from AND e.startedAt < :until
""")
suspend fun getUniqueArtistCountBetweenSuspend(from: Long, until: Long): Int

// Unique song count in a period
@Query("""
    SELECT COUNT(DISTINCT e.songId) FROM listening_events e
    WHERE e.completed = 1 AND e.startedAt >= :from AND e.startedAt < :until
""")
suspend fun getUniqueSongCountBetweenSuspend(from: Long, until: Long): Int

// Days with listening activity in a period
@Query("""
    SELECT COUNT(DISTINCT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime'))
    FROM listening_events
    WHERE completed = 1 AND startedAt >= :from AND startedAt < :until
""")
suspend fun getDaysWithListeningBetweenSuspend(from: Long, until: Long): Int

// Average daily listening ms in a period
@Query("""
    SELECT COALESCE(
        SUM(durationMs) / NULLIF(COUNT(DISTINCT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime')), 0),
        0
    )
    FROM listening_events
    WHERE completed = 1 AND startedAt >= :from AND startedAt < :until
""")
suspend fun getAvgDailyListeningMsBetweenSuspend(from: Long, until: Long): Long

// Listening time in a period (suspend)
@Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events WHERE startedAt >= :from AND startedAt < :until")
suspend fun getListeningTimeBetweenSuspend(from: Long, until: Long): Long

// Longest session in a period
@Query("SELECT COALESCE(MAX(durationMs), 0) FROM listening_events WHERE completed = 1 AND startedAt >= :from AND startedAt < :until")
suspend fun getLongestSessionBetweenSuspend(from: Long, until: Long): Long

// Hourly listening in a period (for time-of-day analysis)
@Query("""
    SELECT CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) AS hour,
           SUM(durationMs) AS totalDurationMs,
           COUNT(*) AS eventCount
    FROM listening_events
    WHERE completed = 1 AND startedAt >= :from AND startedAt < :until
    GROUP BY hour
""")
suspend fun getHourlyListeningBetweenSuspend(from: Long, until: Long): List<HourlyListening>

// Weekend listening in a period
@Query("""
    SELECT COALESCE(SUM(durationMs), 0) FROM listening_events
    WHERE completed = 1 AND startedAt >= :from AND startedAt < :until
    AND CAST(strftime('%w', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) IN (0, 6)
""")
suspend fun getWeekendListeningMsBetweenSuspend(from: Long, until: Long): Long

// Weekday listening in a period
@Query("""
    SELECT COALESCE(SUM(durationMs), 0) FROM listening_events
    WHERE completed = 1 AND startedAt >= :from AND startedAt < :until
    AND CAST(strftime('%w', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) NOT IN (0, 6)
""")
suspend fun getWeekdayListeningMsBetweenSuspend(from: Long, until: Long): Long

// Top artists by play count in a period (already exists as getTopArtistsByPlayCountInPeriod — reuse)

// Distinct source apps in a period
@Query("""
    SELECT DISTINCT sourceApp FROM listening_events
    WHERE startedAt >= :from AND startedAt < :until
""")
suspend fun getDistinctSourceAppsBetweenSuspend(from: Long, until: Long): List<String>

// Songs with high all-time play count but no plays in a period
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
           COUNT(CASE WHEN e.completed = 1 THEN 1 END) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1
    GROUP BY e.songId
    HAVING playCount >= :minPlays
    AND e.songId NOT IN (
        SELECT DISTINCT songId FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
    )
    ORDER BY playCount DESC
    LIMIT :limit
""")
suspend fun getSongsNotPlayedSince(minPlays: Int, since: Long, limit: Int): List<SongPlayStats>

// Top song by play count in a period
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
           COUNT(CASE WHEN e.completed = 1 THEN 1 END) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1 AND e.startedAt >= :from AND e.startedAt < :until
    GROUP BY e.songId
    ORDER BY playCount DESC
    LIMIT :limit
""")
suspend fun getTopSongsInPeriodSuspend(from: Long, until: Long, limit: Int): List<SongPlayStats>

// Artists with shallow plays in a period (played but fewer than threshold)
@Query("""
    SELECT s.artist,
           COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
           COUNT(CASE WHEN e.completed = 1 THEN 1 END) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1 AND e.startedAt >= :from AND e.startedAt < :until
    GROUP BY s.artist
    HAVING playCount BETWEEN 1 AND :maxPlays
    ORDER BY RANDOM()
    LIMIT :limit
""")
suspend fun getShallowArtistsInPeriodSuspend(from: Long, until: Long, maxPlays: Int, limit: Int): List<ArtistPlayStats>
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt
git commit -m "feat: add suspend DAO queries for challenge generators"
```

---

### Task 6: ChallengeType Enum + Week Utilities

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/ChallengeType.kt`

**Step 1: Create ChallengeType enum and week utility**

```kotlin
package com.musicstats.app.service

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ChallengeType {
    DISCOVERY_STRETCH,
    GENRE_EXPLORER,
    ARTIST_DEEP_DIVE,
    REDISCOVERY,
    CONSISTENCY,
    TIME_STRETCH,
    NO_SKIP_RUN,
    NIGHT_OWL_EARLY_BIRD,
    MARATHON_SESSION,
    ARTIST_VARIETY,
    LOYALTY_TEST,
    SOURCE_SWAP,
    OLD_FAVORITE,
    WEEKEND_WARRIOR;
}

object WeekUtils {
    private val zone = ZoneId.systemDefault()

    /** Returns epoch millis of Monday 00:00 of the current week. */
    fun currentWeekStart(): Long {
        val today = Instant.now().atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Returns epoch millis of Monday 00:00 of the previous week. */
    fun previousWeekStart(): Long {
        val today = Instant.now().atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val prevMonday = monday.minusWeeks(1)
        return prevMonday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Returns epoch millis of the Sunday 23:59:59.999 (i.e. next Monday 00:00) of the given week start. */
    fun weekEnd(weekStart: Long): Long {
        val monday = Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate()
        return monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Days remaining in the current week (including today). */
    fun daysRemainingInWeek(): Int {
        val today = Instant.now().atZone(zone).toLocalDate()
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return (sunday.toEpochDay() - today.toEpochDay()).toInt() + 1
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/ChallengeType.kt
git commit -m "feat: add ChallengeType enum and WeekUtils"
```

---

### Task 7: ChallengeGenerator Service

This is the core service with all 14 generators. Each generator analyzes last week's data and returns a `Challenge?`.

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/ChallengeGenerator.kt`

**Step 1: Create ChallengeGenerator**

```kotlin
package com.musicstats.app.service

import com.musicstats.app.data.dao.ChallengeDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.model.Challenge
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChallengeGenerator @Inject constructor(
    private val eventDao: ListeningEventDao,
    private val challengeDao: ChallengeDao
) {
    /**
     * Generate this week's challenges.
     * Returns the list of challenges that were created, or empty if already generated or gated.
     */
    suspend fun generateWeeklyChallenges(): List<Challenge> {
        val weekStart = WeekUtils.currentWeekStart()

        // Already generated for this week?
        if (challengeDao.countForWeek(weekStart) > 0) return emptyList()

        // Gate: need 5+ hours total listening
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        if (totalMs < MomentPriority.GATE_MS) return emptyList()

        // Gate: need 2+ weeks of data
        val prevWeekStart = WeekUtils.previousWeekStart()
        val twoWeeksAgo = prevWeekStart - 7L * 24 * 3600 * 1000
        val oldDataCount = eventDao.getPlayCountBetweenSuspend(twoWeeksAgo, prevWeekStart)
        if (oldDataCount == 0) return emptyList()

        val lastWeekEnd = weekStart
        val lastWeekStart = prevWeekStart

        // Cooldown: types used last week can't repeat
        val cooldownTypes = challengeDao.getTypesForWeek(prevWeekStart).toSet()

        // Run all generators
        val candidates = listOfNotNull(
            generateDiscoveryStretch(lastWeekStart, lastWeekEnd, weekStart),
            generateGenreExplorer(lastWeekStart, lastWeekEnd, weekStart),
            generateArtistDeepDive(lastWeekStart, lastWeekEnd, weekStart),
            generateRediscovery(lastWeekStart, lastWeekEnd, weekStart),
            generateConsistency(lastWeekStart, lastWeekEnd, weekStart),
            generateTimeStretch(lastWeekStart, lastWeekEnd, weekStart),
            generateNoSkipRun(lastWeekStart, lastWeekEnd, weekStart),
            generateNightOwlEarlyBird(lastWeekStart, lastWeekEnd, weekStart),
            generateMarathonSession(lastWeekStart, lastWeekEnd, weekStart),
            generateArtistVariety(lastWeekStart, lastWeekEnd, weekStart),
            generateLoyaltyTest(lastWeekStart, lastWeekEnd, weekStart),
            generateSourceSwap(lastWeekStart, lastWeekEnd, weekStart),
            generateOldFavorite(lastWeekStart, lastWeekEnd, weekStart),
            generateWeekendWarrior(lastWeekStart, lastWeekEnd, weekStart),
        ).filter { it.type !in cooldownTypes }

        if (candidates.isEmpty()) return emptyList()

        // Pick 2-3 random challenges
        val count = if (candidates.size >= 3) 3 else candidates.size.coerceAtMost(2)
        val selected = candidates.shuffled().take(count)

        selected.forEach { challengeDao.insert(it) }
        return selected
    }

    // --- Generators ---

    private suspend fun generateDiscoveryStretch(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val discovered = eventDao.getNewSongsDiscoveredBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (discovered < 1) return null
        val target = (discovered * 2).coerceAtLeast(4).toFloat()
        return Challenge(
            type = ChallengeType.DISCOVERY_STRETCH.name,
            weekStart = weekStart,
            title = "Discovery stretch",
            description = "You found $discovered new songs last week. Discover ${target.toInt()} this week.",
            targetValue = target,
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateGenreExplorer(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        // Check if top artist dominated last week (>70% of plays)
        val topArtists = eventDao.getTopArtistsByPlayCountInPeriod(lastWeekStart, lastWeekEnd, 5)
        val totalPlays = eventDao.getPlayCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (totalPlays < 10 || topArtists.isEmpty()) return null
        val topArtistPlays = topArtists[0].playCount
        val concentration = topArtistPlays.toFloat() / totalPlays
        if (concentration < 0.5f) return null
        val targetMinutes = 20
        return Challenge(
            type = ChallengeType.GENRE_EXPLORER.name,
            weekStart = weekStart,
            title = "Branch out",
            description = "${(concentration * 100).toInt()}% of last week was ${topArtists[0].artist}. Listen to $targetMinutes min of someone new.",
            targetValue = (targetMinutes * 60 * 1000).toFloat(),
            generatedAt = System.currentTimeMillis(),
            metadata = """{"dominantArtist":"${topArtists[0].artist}"}"""
        )
    }

    private suspend fun generateArtistDeepDive(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val shallow = eventDao.getShallowArtistsInPeriodSuspend(lastWeekStart, lastWeekEnd, 3, 5)
        if (shallow.isEmpty()) return null
        val pick = shallow[0]
        return Challenge(
            type = ChallengeType.ARTIST_DEEP_DIVE.name,
            weekStart = weekStart,
            title = "Deep dive",
            description = "You played ${pick.artist} ${pick.playCount} times last week. Give them a real chance — 5 plays.",
            targetValue = 5f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"artist":"${pick.artist}"}"""
        )
    }

    private suspend fun generateRediscovery(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val thirtyDaysAgo = lastWeekStart - 30L * 24 * 3600 * 1000
        val forgotten = eventDao.getSongsNotPlayedSince(10, thirtyDaysAgo, 5)
        if (forgotten.isEmpty()) return null
        val pick = forgotten.random()
        return Challenge(
            type = ChallengeType.REDISCOVERY.name,
            weekStart = weekStart,
            title = "Rediscovery",
            description = "You used to love ${pick.title} by ${pick.artist} (${pick.playCount} plays). Revisit it.",
            targetValue = 1f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"songId":${pick.songId},"songTitle":"${pick.title}","artist":"${pick.artist}"}"""
        )
    }

    private suspend fun generateConsistency(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val daysActive = eventDao.getDaysWithListeningBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (daysActive >= 6) return null // already consistent
        if (daysActive < 2) return null // too little data
        return Challenge(
            type = ChallengeType.CONSISTENCY.name,
            weekStart = weekStart,
            title = "Every day counts",
            description = "You listened on $daysActive days last week. Hit all 7 this week.",
            targetValue = 7f,
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateTimeStretch(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val avgDailyMs = eventDao.getAvgDailyListeningMsBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (avgDailyMs < 5 * 60 * 1000) return null // less than 5 min/day, skip
        val avgMinutes = avgDailyMs / 60000
        val targetMinutes = (avgMinutes * 1.5).toLong().coerceAtLeast(avgMinutes + 15)
        return Challenge(
            type = ChallengeType.TIME_STRETCH.name,
            weekStart = weekStart,
            title = "Time stretch",
            description = "You averaged ${avgMinutes}min/day last week. Push for ${targetMinutes}min/day.",
            targetValue = (targetMinutes * 60 * 1000 * 7).toFloat(), // weekly total target
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateNoSkipRun(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val totalEvents = eventDao.getTotalEventCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        val skips = eventDao.getSkipCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (totalEvents < 20) return null
        val skipRate = skips.toFloat() / totalEvents
        if (skipRate < 0.25f) return null // low skip rate, not interesting
        return Challenge(
            type = ChallengeType.NO_SKIP_RUN.name,
            weekStart = weekStart,
            title = "No-skip run",
            description = "You skipped ${(skipRate * 100).toInt()}% of songs last week. Try a no-skip session — 10 songs straight.",
            targetValue = 10f,
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateNightOwlEarlyBird(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val hourly = eventDao.getHourlyListeningBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (hourly.isEmpty()) return null
        val totalMs = hourly.sumOf { it.totalDurationMs }
        if (totalMs < 30 * 60 * 1000) return null // less than 30 min total

        val morningMs = hourly.filter { it.hour in 6..11 }.sumOf { it.totalDurationMs }
        val eveningMs = hourly.filter { it.hour in 20..23 }.sumOf { it.totalDurationMs }

        return when {
            eveningMs > totalMs * 0.6 -> Challenge(
                type = ChallengeType.NIGHT_OWL_EARLY_BIRD.name,
                weekStart = weekStart,
                title = "Early bird",
                description = "All your listening was after 8pm last week. Try a morning session before noon.",
                targetValue = (15 * 60 * 1000).toFloat(), // 15 min morning listening
                generatedAt = System.currentTimeMillis(),
                metadata = """{"direction":"morning"}"""
            )
            morningMs > totalMs * 0.6 -> Challenge(
                type = ChallengeType.NIGHT_OWL_EARLY_BIRD.name,
                weekStart = weekStart,
                title = "Night owl",
                description = "Most of your listening was before noon last week. Try an evening session after 8pm.",
                targetValue = (15 * 60 * 1000).toFloat(),
                generatedAt = System.currentTimeMillis(),
                metadata = """{"direction":"evening"}"""
            )
            else -> null
        }
    }

    private suspend fun generateMarathonSession(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val longestMs = eventDao.getLongestSessionBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (longestMs < 10 * 60 * 1000) return null // less than 10 min
        val longestMin = longestMs / 60000
        val targetMin = (longestMin * 1.5).toLong().coerceAtLeast(longestMin + 10).coerceAtMost(120)
        return Challenge(
            type = ChallengeType.MARATHON_SESSION.name,
            weekStart = weekStart,
            title = "Marathon session",
            description = "Your longest session was ${longestMin}min. Go for ${targetMin}min.",
            targetValue = (targetMin * 60 * 1000).toFloat(),
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateArtistVariety(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val uniqueArtists = eventDao.getUniqueArtistCountBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (uniqueArtists < 3 || uniqueArtists > 20) return null
        val target = (uniqueArtists * 2).coerceAtLeast(8)
        return Challenge(
            type = ChallengeType.ARTIST_VARIETY.name,
            weekStart = weekStart,
            title = "Artist variety",
            description = "You listened to $uniqueArtists artists last week. Explore $target different ones this week.",
            targetValue = target.toFloat(),
            generatedAt = System.currentTimeMillis()
        )
    }

    private suspend fun generateLoyaltyTest(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val topSongs = eventDao.getTopSongsInPeriodSuspend(lastWeekStart, lastWeekEnd, 1)
        if (topSongs.isEmpty()) return null
        val top = topSongs[0]
        if (top.playCount < 5) return null
        val target = (top.playCount * 1.5).toInt().coerceAtLeast(top.playCount + 3)
        return Challenge(
            type = ChallengeType.LOYALTY_TEST.name,
            weekStart = weekStart,
            title = "Loyalty test",
            description = "${top.title} is your current obsession at ${top.playCount} plays. Can you hit $target?",
            targetValue = target.toFloat(),
            generatedAt = System.currentTimeMillis(),
            metadata = """{"songId":${top.songId},"songTitle":"${top.title}","artist":"${top.artist}"}"""
        )
    }

    private suspend fun generateSourceSwap(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val apps = eventDao.getDistinctSourceAppsBetweenSuspend(lastWeekStart, lastWeekEnd)
        if (apps.size != 1) return null // already using multiple or none
        val appName = apps[0].substringAfterLast('.').replaceFirstChar { it.uppercase() }
        return Challenge(
            type = ChallengeType.SOURCE_SWAP.name,
            weekStart = weekStart,
            title = "Source swap",
            description = "You've only used $appName. Try discovering something on a different app.",
            targetValue = 1f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"currentApp":"${apps[0]}"}"""
        )
    }

    private suspend fun generateOldFavorite(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val thirtyDaysAgo = lastWeekStart - 30L * 24 * 3600 * 1000
        val forgotten = eventDao.getSongsNotPlayedSince(20, thirtyDaysAgo, 10)
        if (forgotten.isEmpty()) return null
        val pick = forgotten.random()
        return Challenge(
            type = ChallengeType.OLD_FAVORITE.name,
            weekStart = weekStart,
            title = "Old favorite",
            description = "You used to love ${pick.title} (${pick.playCount} plays). It's been a while — play it again.",
            targetValue = 1f,
            generatedAt = System.currentTimeMillis(),
            metadata = """{"songId":${pick.songId},"songTitle":"${pick.title}","artist":"${pick.artist}"}"""
        )
    }

    private suspend fun generateWeekendWarrior(lastWeekStart: Long, lastWeekEnd: Long, weekStart: Long): Challenge? {
        val weekendMs = eventDao.getWeekendListeningMsBetweenSuspend(lastWeekStart, lastWeekEnd)
        val weekdayMs = eventDao.getWeekdayListeningMsBetweenSuspend(lastWeekStart, lastWeekEnd)
        val totalMs = weekendMs + weekdayMs
        if (totalMs < 30 * 60 * 1000) return null
        val weekendPct = weekendMs.toFloat() / totalMs
        if (weekendPct > 0.2f) return null // already listening on weekends
        return Challenge(
            type = ChallengeType.WEEKEND_WARRIOR.name,
            weekStart = weekStart,
            title = "Weekend warrior",
            description = "You barely listen on weekends. Hit 30 min on Saturday or Sunday.",
            targetValue = (30 * 60 * 1000).toFloat(),
            generatedAt = System.currentTimeMillis()
        )
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/ChallengeGenerator.kt
git commit -m "feat: add ChallengeGenerator with 14 pattern-reactive generators"
```

---

### Task 8: ChallengeProgressUpdater

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/ChallengeProgressUpdater.kt`

**Step 1: Create the progress updater**

This service recalculates `currentValue` for each active challenge by querying existing data for the current week.

```kotlin
package com.musicstats.app.service

import com.musicstats.app.data.dao.ChallengeDao
import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.model.Challenge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChallengeProgressUpdater @Inject constructor(
    private val challengeDao: ChallengeDao,
    private val eventDao: ListeningEventDao
) {
    suspend fun updateAll() {
        val weekStart = WeekUtils.currentWeekStart()
        val weekEnd = WeekUtils.weekEnd(weekStart)
        val challenges = challengeDao.getChallengesForWeekSuspend(weekStart)

        for (challenge in challenges) {
            if (challenge.completed) continue
            val currentValue = calculateProgress(challenge, weekStart, weekEnd)
            val completed = currentValue >= challenge.targetValue
            val completedAt = if (completed) System.currentTimeMillis() else null
            challengeDao.updateProgress(challenge.id, currentValue, completed, completedAt)
        }
    }

    private suspend fun calculateProgress(challenge: Challenge, weekStart: Long, weekEnd: Long): Float {
        val meta = challenge.metadata?.let {
            runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
        }

        return when (challenge.type) {
            ChallengeType.DISCOVERY_STRETCH.name -> {
                eventDao.getNewSongsDiscoveredBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.GENRE_EXPLORER.name -> {
                val dominantArtist = meta?.get("dominantArtist")?.jsonPrimitive?.content ?: return 0f
                // Listening time to non-dominant artists
                val totalMs = eventDao.getListeningTimeBetweenSuspend(weekStart, weekEnd)
                val topArtists = eventDao.getTopArtistsByPlayCountInPeriod(weekStart, weekEnd, 100)
                val dominantMs = topArtists.filter { it.artist == dominantArtist }.sumOf { it.totalDurationMs }
                (totalMs - dominantMs).toFloat()
            }

            ChallengeType.ARTIST_DEEP_DIVE.name -> {
                val artist = meta?.get("artist")?.jsonPrimitive?.content ?: return 0f
                eventDao.getArtistPlayCountSinceSuspend(artist, weekStart).toFloat()
            }

            ChallengeType.REDISCOVERY.name, ChallengeType.OLD_FAVORITE.name -> {
                val songId = meta?.get("songId")?.jsonPrimitive?.long ?: return 0f
                eventDao.getSongPlayCountSinceSuspend(songId, weekStart).toFloat()
            }

            ChallengeType.CONSISTENCY.name -> {
                eventDao.getDaysWithListeningBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.TIME_STRETCH.name -> {
                eventDao.getListeningTimeBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.NO_SKIP_RUN.name -> {
                // Count longest no-skip streak in current week
                // Simplified: count completed plays (actual no-skip detection would need ordered events)
                eventDao.getPlayCountBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.NIGHT_OWL_EARLY_BIRD.name -> {
                val direction = meta?.get("direction")?.jsonPrimitive?.content ?: return 0f
                val hourly = eventDao.getHourlyListeningBetweenSuspend(weekStart, weekEnd)
                val targetMs = if (direction == "morning") {
                    hourly.filter { it.hour in 6..11 }.sumOf { it.totalDurationMs }
                } else {
                    hourly.filter { it.hour in 20..23 }.sumOf { it.totalDurationMs }
                }
                targetMs.toFloat()
            }

            ChallengeType.MARATHON_SESSION.name -> {
                eventDao.getLongestSessionBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.ARTIST_VARIETY.name -> {
                eventDao.getUniqueArtistCountBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            ChallengeType.LOYALTY_TEST.name -> {
                val songId = meta?.get("songId")?.jsonPrimitive?.long ?: return 0f
                eventDao.getSongPlayCountSinceSuspend(songId, weekStart).toFloat()
            }

            ChallengeType.SOURCE_SWAP.name -> {
                val currentApp = meta?.get("currentApp")?.jsonPrimitive?.content ?: return 0f
                val apps = eventDao.getDistinctSourceAppsBetweenSuspend(weekStart, weekEnd)
                if (apps.any { it != currentApp }) 1f else 0f
            }

            ChallengeType.WEEKEND_WARRIOR.name -> {
                eventDao.getWeekendListeningMsBetweenSuspend(weekStart, weekEnd).toFloat()
            }

            else -> 0f
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/ChallengeProgressUpdater.kt
git commit -m "feat: add ChallengeProgressUpdater for on-the-fly progress recalculation"
```

---

### Task 9: ChallengeWorker (WorkManager)

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/ChallengeWorker.kt`

**Step 1: Create the WorkManager worker**

```kotlin
package com.musicstats.app.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class ChallengeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val generator: ChallengeGenerator,
    private val progressUpdater: ChallengeProgressUpdater
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            generator.generateWeeklyChallenges()
            progressUpdater.updateAll()
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "weekly_challenge_daily"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ChallengeWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/ChallengeWorker.kt
git commit -m "feat: add ChallengeWorker for daily generation and progress updates"
```

---

### Task 10: Integrate into HomeViewModel

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt`

**Step 1: Add challenge state to HomeViewModel**

Add these constructor parameters:
```kotlin
private val challengeGenerator: ChallengeGenerator,
private val challengeProgressUpdater: ChallengeProgressUpdater,
private val challengeDao: ChallengeDao
```

Add these imports:
```kotlin
import com.musicstats.app.data.dao.ChallengeDao
import com.musicstats.app.data.model.Challenge
import com.musicstats.app.service.ChallengeGenerator
import com.musicstats.app.service.ChallengeProgressUpdater
import com.musicstats.app.service.ChallengeWorker
import com.musicstats.app.service.WeekUtils
```

Add this StateFlow for active challenges:
```kotlin
val activeChallenges: StateFlow<List<Challenge>> =
    challengeDao.getChallengesForWeek(WeekUtils.currentWeekStart())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

Add this function to generate + update on app open:
```kotlin
fun refreshChallengesOnOpen() {
    viewModelScope.launch(Dispatchers.IO) {
        challengeGenerator.generateWeeklyChallenges()
        challengeProgressUpdater.updateAll()
    }
}
```

In the `init` block, add:
```kotlin
ChallengeWorker.schedule(context)
```

**Step 2: Call `refreshChallengesOnOpen()` from the existing `detectMomentsOnOpen()` function**

Add `refreshChallengesOnOpen()` call inside the existing `detectMomentsOnOpen()` coroutine, or call it separately from `HomeScreen` in a `LaunchedEffect`.

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt
git commit -m "feat: expose weekly challenges in HomeViewModel"
```

---

### Task 11: ChallengeCard Composable

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/components/ChallengeCard.kt`

**Step 1: Create the challenge card composable**

```kotlin
package com.musicstats.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicstats.app.data.model.Challenge

@Composable
fun ChallengeCard(
    challenge: Challenge,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress by animateFloatAsState(
        targetValue = (challenge.currentValue / challenge.targetValue).coerceIn(0f, 1f),
        label = "challenge_progress"
    )

    Card(
        onClick = onTap,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (challenge.completed)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = challenge.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (challenge.completed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = challenge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!challenge.completed) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/ChallengeCard.kt
git commit -m "feat: add ChallengeCard composable with progress bar"
```

---

### Task 12: ChallengeDetailSheet

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/home/ChallengeDetailSheet.kt`

**Step 1: Create the bottom sheet**

```kotlin
package com.musicstats.app.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musicstats.app.data.model.Challenge
import com.musicstats.app.service.WeekUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeDetailSheet(
    challenge: Challenge,
    onDismiss: () -> Unit
) {
    val progress = (challenge.currentValue / challenge.targetValue).coerceIn(0f, 1f)
    val daysLeft = WeekUtils.daysRemainingInWeek()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = challenge.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (challenge.completed) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Text(
                text = challenge.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!challenge.completed) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatProgress(challenge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$daysLeft days left",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatProgress(challenge: Challenge): String {
    val current = challenge.currentValue
    val target = challenge.targetValue
    // For time-based challenges (targets > 60000ms), format as minutes
    return if (target > 60000f) {
        "${(current / 60000).toInt()}min / ${(target / 60000).toInt()}min"
    } else {
        "${current.toInt()} / ${target.toInt()}"
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/ChallengeDetailSheet.kt
git commit -m "feat: add ChallengeDetailSheet bottom sheet"
```

---

### Task 13: ChallengesStrip on HomeScreen

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Step 1: Add challenges section to HomeScreen**

In `HomeScreen`, collect the new state:
```kotlin
val activeChallenges by viewModel.activeChallenges.collectAsState()
```

Add a `var selectedChallenge by remember { mutableStateOf<Challenge?>(null) }` for the detail sheet.

After the MomentsStrip section, add a ChallengesStrip:

```kotlin
// Challenges strip
if (activeChallenges.isNotEmpty()) {
    Column {
        Text(
            text = "This Week's Challenges",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            activeChallenges.forEach { challenge ->
                ChallengeCard(
                    challenge = challenge,
                    onTap = { selectedChallenge = challenge }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// Challenge detail sheet
selectedChallenge?.let { challenge ->
    ChallengeDetailSheet(
        challenge = challenge,
        onDismiss = { selectedChallenge = null }
    )
}
```

Add a `LaunchedEffect` to refresh challenges on open:
```kotlin
LaunchedEffect(Unit) {
    viewModel.refreshChallengesOnOpen()
}
```

Add imports for `ChallengeCard`, `ChallengeDetailSheet`, and `Challenge`.

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt
git commit -m "feat: add challenges strip to HomeScreen with detail sheet"
```

---

### Task 14: Challenge History Screen

**Files:**
- Create: `app/src/main/java/com/musicstats/app/ui/challenges/ChallengeHistoryScreen.kt`
- Create: `app/src/main/java/com/musicstats/app/ui/challenges/ChallengeHistoryViewModel.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/navigation/NavGraph.kt`

**Step 1: Create ChallengeHistoryViewModel**

```kotlin
package com.musicstats.app.ui.challenges

import androidx.lifecycle.ViewModel
import com.musicstats.app.data.dao.ChallengeDao
import com.musicstats.app.data.model.Challenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ChallengeHistoryViewModel @Inject constructor(
    private val challengeDao: ChallengeDao
) : ViewModel() {
    val allChallenges: Flow<List<Challenge>> = challengeDao.getAllChallenges()
}
```

**Step 2: Create ChallengeHistoryScreen**

```kotlin
package com.musicstats.app.ui.challenges

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.musicstats.app.data.model.Challenge
import com.musicstats.app.ui.components.ChallengeCard
import com.musicstats.app.ui.home.ChallengeDetailSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeHistoryScreen(
    onBack: () -> Unit,
    viewModel: ChallengeHistoryViewModel = hiltViewModel()
) {
    val allChallenges by viewModel.allChallenges.collectAsState(initial = emptyList())
    var selectedChallenge by remember { mutableStateOf<Challenge?>(null) }

    // Group by weekStart
    val grouped = allChallenges.groupBy { it.weekStart }
        .toSortedMap(compareByDescending { it })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Challenge History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            grouped.forEach { (weekStart, challenges) ->
                item {
                    Text(
                        text = formatWeekLabel(weekStart),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(challenges, key = { it.id }) { challenge ->
                    ChallengeCard(
                        challenge = challenge,
                        onTap = { selectedChallenge = challenge }
                    )
                }
            }
        }
    }

    selectedChallenge?.let { challenge ->
        ChallengeDetailSheet(
            challenge = challenge,
            onDismiss = { selectedChallenge = null }
        )
    }
}

private fun formatWeekLabel(weekStartMs: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(weekStartMs).atZone(zone).toLocalDate()
    val end = start.plusDays(6)
    val fmt = DateTimeFormatter.ofPattern("MMM d")
    return "${start.format(fmt)} – ${end.format(fmt)}"
}
```

**Step 3: Add route in NavGraph.kt**

Add to the `NavHost`:
```kotlin
composable("challenge_history") {
    ChallengeHistoryScreen(onBack = { navController.popBackStack() })
}
```

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/challenges/ChallengeHistoryViewModel.kt app/src/main/java/com/musicstats/app/ui/challenges/ChallengeHistoryScreen.kt app/src/main/java/com/musicstats/app/ui/navigation/NavGraph.kt
git commit -m "feat: add ChallengeHistoryScreen with grouped week view"
```

---

### Task 15: Wire "See all" Link + Navigation

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Step 1: Add "See all" link to the challenges strip header**

Change the challenges strip header from a simple `Text` to a `Row` with "See all" button:

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 4.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        text = "This Week's Challenges",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    TextButton(onClick = onSeeAllChallenges) {
        Text("See all", style = MaterialTheme.typography.labelMedium)
    }
}
```

Add `onSeeAllChallenges: () -> Unit = {}` parameter to `HomeScreen`.

**Step 2: Wire in NavGraph.kt**

In the `composable("home")` block, pass the callback:
```kotlin
HomeScreen(
    // ... existing callbacks ...
    onSeeAllChallenges = { navController.navigate("challenge_history") }
)
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt app/src/main/java/com/musicstats/app/ui/navigation/NavGraph.kt
git commit -m "feat: wire challenge history navigation from home screen"
```

---

### Task 16: Build & Verify

**Step 1: Build the project**

Run: `./gradlew assembleDebug`

Expected: BUILD SUCCESSFUL

**Step 2: Fix any compilation errors**

Address any missing imports, type mismatches, or wiring issues.

**Step 3: Run unit tests**

Run: `./gradlew test`

Expected: All existing tests pass. No regressions.

**Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: resolve compilation issues from weekly challenges integration"
```
