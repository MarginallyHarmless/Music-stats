# Moment Card Gating System — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Gate moment cards behind a 5-hour listening threshold, release max 1/day by priority, and show a locked teaser card before the gate opens.

**Architecture:** Queue-based release system. Detection stays unchanged — all moments persist immediately with `releasedAt = null`. A `MomentReleaseScheduler` runs after detection and picks the highest-priority unreleased moment to release (set `releasedAt`). UI queries filter by `releasedAt IS NOT NULL`. Pre-gate, the home screen shows a progress teaser card.

**Tech Stack:** Room (migration + column), Kotlin, Hilt DI, Jetpack Compose, WorkManager

**Design doc:** `docs/plans/2026-03-03-moment-card-gating-design.md`

---

### Task 1: Add `releasedAt` Column to Moment Entity + Room Migration

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/model/Moment.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt` (version 16→17, add migration)
- Modify: `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt` (register migration)

**Step 1: Add `releasedAt` field to Moment entity**

In `Moment.kt`, add after the `copyVariant` field (last field, line 27):

```kotlin
val releasedAt: Long? = null
```

**Step 2: Add Room migration 16→17**

In `MusicStatsDatabase.kt`, bump `version = 17` (line 21). Add a new migration in the companion object after `MIGRATION_15_16`:

```kotlin
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE moments ADD COLUMN releasedAt INTEGER DEFAULT NULL")
        // Backfill: all existing moments become released (visible)
        db.execSQL("UPDATE moments SET releasedAt = triggeredAt")
    }
}
```

**Step 3: Register migration in DatabaseModule**

In `DatabaseModule.kt`, add `MusicStatsDatabase.MIGRATION_16_17` to the `.addMigrations(...)` call (after line 46).

**Step 4: Build to verify migration compiles**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: add releasedAt column to Moment entity with migration 16→17
```

---

### Task 2: Create `MomentPriority` Object

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/MomentPriority.kt`

**Step 1: Create the priority tier lookup**

```kotlin
package com.musicstats.app.service

/**
 * Static priority tiers for moment types.
 * Tier 1 = rarest/legendary, Tier 5 = most common.
 * Used by MomentReleaseScheduler to pick which queued moment to release first.
 */
object MomentPriority {

    private val tiers: Map<String, Int> = mapOf(
        // Tier 1 — Legendary
        "SONG_PLAYS_500" to 1,
        "STREAK_100" to 1,
        "TOTAL_HOURS_1000" to 1,
        "FLEX_COLLECTOR_5000" to 1,

        // Tier 2 — Epic
        "SONG_PLAYS_250" to 2,
        "STREAK_30" to 2,
        "TOTAL_HOURS_500" to 2,
        "FLEX_COLLECTOR_2000" to 2,
        "NARRATIVE_SOUNDTRACK" to 2,
        "NARRATIVE_PARALLEL_LIVES" to 2,
        "NARRATIVE_NIGHT_AND_DAY" to 2,

        // Tier 3 — Milestone
        "SONG_PLAYS_100" to 3,
        "ARTIST_HOURS_24" to 3,
        "STREAK_14" to 3,
        "TOTAL_HOURS_100" to 3,
        "SONGS_DISCOVERED_500" to 3,
        "FLEX_COLLECTOR_1000" to 3,
        "BEHAVIORAL_GROWER" to 3,
        "BEHAVIORAL_TASTE_DRIFT" to 3,
        "BEHAVIORAL_LOCKED_IN" to 3,
        "BEHAVIORAL_REPLACEMENT" to 3,
        "NARRATIVE_ORIGIN_STORY" to 3,
        "NARRATIVE_GATEWAY" to 3,
        "NARRATIVE_COLLECTION" to 3,
        "NARRATIVE_TAKEOVER" to 3,
        "NARRATIVE_SLOW_BUILD" to 3,
        "NARRATIVE_BINGE_AND_FADE" to 3,
        "NARRATIVE_FULL_CIRCLE" to 3,
        "NARRATIVE_ONE_THAT_GOT_AWAY" to 3,
        "NARRATIVE_RABBIT_HOLE" to 3,
        "FLEX_BIGGEST_MONTH" to 3,
        "FLEX_SPEED_RUN" to 3,

        // Tier 4 — Notable
        "SONG_PLAYS_50" to 4,
        "ARTIST_HOURS_10" to 4,
        "ARTIST_HOURS_5" to 4,
        "STREAK_7" to 4,
        "TOTAL_HOURS_24" to 4,
        "SONGS_DISCOVERED_250" to 4,
        "SONGS_DISCOVERED_100" to 4,
        "ARCHETYPE_NIGHT_OWL" to 4,
        "ARCHETYPE_MORNING_LISTENER" to 4,
        "ARCHETYPE_COMMUTE_LISTENER" to 4,
        "ARCHETYPE_COMPLETIONIST" to 4,
        "ARCHETYPE_CERTIFIED_SKIPPER" to 4,
        "ARCHETYPE_DEEP_CUT_DIGGER" to 4,
        "ARCHETYPE_LOYAL_FAN" to 4,
        "ARCHETYPE_EXPLORER" to 4,
        "ARCHETYPE_WEEKEND_WARRIOR" to 4,
        "ARCHETYPE_WIDE_TASTE" to 4,
        "ARCHETYPE_REPEAT_OFFENDER" to 4,
        "ARCHETYPE_ALBUM_LISTENER" to 4,
        "DAILY_RITUAL" to 4,
        "FAST_OBSESSION" to 4,
        "QUICK_OBSESSION" to 4,
        "DISCOVERY_WEEK" to 4,
        "REDISCOVERY" to 4,
        "SLOW_BURN" to 4,
        "MARATHON_WEEK" to 4,
        "BEHAVIORAL_MAIN_CHARACTER" to 4,
        "BEHAVIORAL_ARTIST_MARATHON" to 4,
        "BEHAVIORAL_ONE_HIT_WONDER" to 4,
        "BEHAVIORAL_CLOCK_WORK" to 4,
        "BEHAVIORAL_ANTHEM" to 4,
        "FLEX_LOOP" to 4,
        "FLEX_POWER_HOUR" to 4,
        "FLEX_AFTER_HOURS" to 4,

        // Tier 5 — Common
        "OBSESSION_DAILY" to 5,
        "BREAKUP_CANDIDATE" to 5,
        "LONGEST_SESSION" to 5,
        "RESURRECTION" to 5,
        "NIGHT_BINGE" to 5,
        "COMFORT_ZONE" to 5,
    )

    /** Default tier for unknown types */
    private const val DEFAULT_TIER = 4

    fun tierOf(type: String): Int = tiers[type] ?: DEFAULT_TIER

    /** Whether this tier's moments should expire from the queue after 14 days */
    fun canExpire(tier: Int): Boolean = tier >= 3

    /** Whether releasing this tier should trigger a push notification */
    fun shouldNotify(tier: Int): Boolean = tier <= 3

    companion object {
        const val GATE_HOURS = 5f
        const val GATE_MS = (GATE_HOURS * 3_600_000).toLong()
        const val EXPIRY_DAYS = 14
        const val EXPIRY_MS = EXPIRY_DAYS * 24L * 3_600_000L
    }
}
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: add MomentPriority tier lookup for moment release scheduling
```

---

### Task 3: Add DAO Queries for Release Scheduling

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/MomentDao.kt`

**Step 1: Add release-related queries to MomentDao**

Add these methods to `MomentDao.kt`:

```kotlin
@Query("SELECT * FROM moments WHERE releasedAt IS NULL ORDER BY triggeredAt DESC")
suspend fun getUnreleasedMoments(): List<Moment>

@Query("SELECT COUNT(*) FROM moments WHERE releasedAt >= :dayStartMs AND releasedAt < :dayEndMs")
suspend fun countReleasedInRange(dayStartMs: Long, dayEndMs: Long): Int

@Query("UPDATE moments SET releasedAt = :releasedAt WHERE id = :id")
suspend fun setReleasedAt(id: Long, releasedAt: Long)

@Query("DELETE FROM moments WHERE releasedAt IS NULL AND triggeredAt < :cutoffMs AND type NOT IN (SELECT type FROM moments WHERE 0)")
suspend fun deleteExpiredUnreleased(cutoffMs: Long): Int
```

Note: The `deleteExpiredUnreleased` query will be called with filtering in Kotlin code (via `MomentReleaseScheduler`) rather than in SQL, because the tier lookup is in Kotlin. Instead use a simpler approach — fetch unreleased, filter in Kotlin, delete by ID:

```kotlin
@Query("DELETE FROM moments WHERE id IN (:ids)")
suspend fun deleteByIds(ids: List<Long>)
```

**Step 2: Update display queries to filter by releasedAt**

Modify existing queries in `MomentDao.kt`:

Change `getAllMoments()` (line 17):
```kotlin
@Query("SELECT * FROM moments WHERE type != 'ARTIST_UNLOCKED' AND releasedAt IS NOT NULL ORDER BY releasedAt DESC")
fun getAllMoments(): Flow<List<Moment>>
```

Change `getRecentMoments()` (line 23):
```kotlin
@Query("SELECT * FROM moments WHERE type != 'ARTIST_UNLOCKED' AND releasedAt IS NOT NULL ORDER BY releasedAt DESC LIMIT :limit")
fun getRecentMoments(limit: Int): Flow<List<Moment>>
```

Change `getUnseenMoments()` (line 20):
```kotlin
@Query("SELECT * FROM moments WHERE seenAt IS NULL AND releasedAt IS NOT NULL ORDER BY releasedAt DESC")
fun getUnseenMoments(): Flow<List<Moment>>
```

Change `getUnseenCount()` (line 35):
```kotlin
@Query("SELECT COUNT(*) FROM moments WHERE seenAt IS NULL AND type != 'ARTIST_UNLOCKED' AND releasedAt IS NOT NULL")
fun getUnseenCount(): Flow<Int>
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: add release scheduling queries to MomentDao, filter display by releasedAt
```

---

### Task 4: Create `MomentReleaseScheduler`

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/MomentReleaseScheduler.kt`

**Step 1: Create the scheduler class**

```kotlin
package com.musicstats.app.service

import com.musicstats.app.data.dao.ListeningEventDao
import com.musicstats.app.data.dao.MomentDao
import com.musicstats.app.data.model.Moment
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MomentReleaseScheduler @Inject constructor(
    private val momentDao: MomentDao,
    private val eventDao: ListeningEventDao
) {
    /**
     * Attempts to release the next highest-priority moment.
     * Returns the released moment (for notification), or null if nothing was released.
     *
     * Rules:
     * 1. Total listening must be >= 5 hours (gate)
     * 2. Max 1 release per calendar day
     * 3. Pick highest priority (lowest tier number), then newest within tier
     * 4. Expire tier 3-5 moments older than 14 days
     */
    suspend fun releaseNext(): Moment? {
        // 1. Check gate
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        if (totalMs < MomentPriority.GATE_MS) return null

        // 2. Check daily limit
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val todayEnd = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        if (momentDao.countReleasedInRange(todayStart, todayEnd) > 0) return null

        // 3. Expire stale moments (tier 3-5, older than 14 days)
        val expiryCutoff = now - MomentPriority.EXPIRY_MS
        val unreleased = momentDao.getUnreleasedMoments()
        val expiredIds = unreleased
            .filter { it.triggeredAt < expiryCutoff && MomentPriority.canExpire(MomentPriority.tierOf(it.type)) }
            .map { it.id }
        if (expiredIds.isNotEmpty()) {
            momentDao.deleteByIds(expiredIds)
        }

        // 4. Pick the best remaining unreleased moment
        val candidates = momentDao.getUnreleasedMoments() // re-query after expiry
        val best = candidates
            .sortedWith(compareBy<Moment> { MomentPriority.tierOf(it.type) }.thenByDescending { it.triggeredAt })
            .firstOrNull()
            ?: return null

        // 5. Release it
        momentDao.setReleasedAt(best.id, now)
        return best.copy(releasedAt = now)
    }

    /** Returns total tracked listening time in milliseconds. Used by UI for gate progress. */
    suspend fun getTotalListeningMs(): Long = eventDao.getTotalListeningTimeMsSuspend()
}
```

**Step 2: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```
feat: add MomentReleaseScheduler for queue-based moment release
```

---

### Task 5: Integrate Scheduler into MomentDetector and MomentWorker

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt` (lines 15-29: add scheduler dep + call after detection)
- Modify: `app/src/main/java/com/musicstats/app/service/MomentWorker.kt` (lines 23-39: use scheduler, gate notifications)

**Step 1: Add scheduler to MomentDetector**

In `MomentDetector.kt`, add `MomentReleaseScheduler` to the constructor:

```kotlin
@Singleton
class MomentDetector @Inject constructor(
    private val eventDao: ListeningEventDao,
    private val artistDao: ArtistDao,
    private val momentDao: MomentDao,
    private val releaseScheduler: MomentReleaseScheduler
) {
```

At the end of `detectAndPersistNewMoments()` (before the `return newMoments` on ~line 94), add:

```kotlin
// Release the next queued moment (if gate passed + daily limit not reached)
releaseScheduler.releaseNext()
```

**Step 2: Update MomentWorker to use tier-based notifications**

In `MomentWorker.kt`, change `doWork()` to use the scheduler result for notifications instead of notifying for every new moment:

```kotlin
override suspend fun doWork(): Result {
    return try {
        val newMoments = detector.detectAndPersistNewMoments()
        // detectAndPersistNewMoments already calls releaseScheduler.releaseNext()
        // But we need the released moment to decide about notifications.
        // The detector's call already released one; query the most recently released.
        val now = System.currentTimeMillis()
        val zone = java.time.ZoneId.systemDefault()
        val todayStart = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()

        // Find today's released moment (if any) and notify if tier 1-3
        // We get the recently released moments and check tier
        // Simple approach: if detector returned new moments AND a release happened,
        // the release scheduler already ran. We just need to check what was released.
        // For simplicity, query all moments released today.
        // Actually, the cleanest approach: have the detector return the released moment.
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { Result.retry() }
}
```

**Better approach:** Have `detectAndPersistNewMoments` return a pair: `(newMoments, releasedMoment)`. But that changes the return type which is used in HomeViewModel too. Cleaner: just call the scheduler separately in the worker and fire notification based on its result.

Revise the integration:

In `MomentDetector.kt`, do NOT call the scheduler (keep detection pure). Instead:

In `MomentWorker.kt`:
```kotlin
@HiltWorker
class MomentWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val detector: MomentDetector,
    private val releaseScheduler: MomentReleaseScheduler
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            detector.detectAndPersistNewMoments()
            val released = releaseScheduler.releaseNext()
            if (released != null && MomentPriority.shouldNotify(MomentPriority.tierOf(released.type))) {
                fireNotification(released)
            }
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.retry() }
    }
    // ... rest stays the same
}
```

In `HomeViewModel.kt`, update `detectMomentsOnOpen()` to also call the scheduler:

```kotlin
fun detectMomentsOnOpen() {
    viewModelScope.launch(Dispatchers.IO) {
        momentDetector.detectAndPersistNewMoments()
        releaseScheduler.releaseNext()
    }
}
```

This requires adding `releaseScheduler` to `HomeViewModel`'s constructor:

```kotlin
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: MusicRepository,
    val mediaSessionTracker: MediaSessionTracker,
    @ApplicationContext private val context: Context,
    private val momentDetector: MomentDetector,
    private val momentsRepository: MomentsRepository,
    private val releaseScheduler: MomentReleaseScheduler
) : ViewModel() {
```

**Step 3: Remove scheduler dep from MomentDetector**

Since we're calling the scheduler from Worker and ViewModel instead, revert the MomentDetector change from Step 1. MomentDetector stays unchanged — no new constructor param needed.

**Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: integrate MomentReleaseScheduler into MomentWorker and HomeViewModel
```

---

### Task 6: Add Gate State to HomeViewModel and MomentsRepository

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/repository/MomentsRepository.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeViewModel.kt`

**Step 1: Add gate state to MomentsRepository**

Add a method to `MomentsRepository.kt` that exposes total listening as a Flow (for the progress bar):

```kotlin
@Singleton
class MomentsRepository @Inject constructor(
    private val momentDao: MomentDao,
    private val eventDao: ListeningEventDao
) {
    // ... existing methods ...

    fun getTotalListeningTimeMs(): Flow<Long> = eventDao.getTotalListeningTimeMs()
}
```

Note: `ListeningEventDao` must be injected into `MomentsRepository`. It currently only has `MomentDao`.

**Step 2: Add GateState sealed class and expose it from HomeViewModel**

In `HomeViewModel.kt`, add after the `TopArtistInfo` data class at the bottom:

```kotlin
sealed class MomentGateState {
    data class Locked(val progressHours: Float) : MomentGateState()
    data object Unlocked : MomentGateState()
}
```

In `HomeViewModel`, add a new StateFlow:

```kotlin
val momentGateState: StateFlow<MomentGateState> =
    momentsRepository.getTotalListeningTimeMs()
        .map { totalMs ->
            val hours = totalMs / 3_600_000f
            if (hours >= MomentPriority.GATE_HOURS) MomentGateState.Unlocked
            else MomentGateState.Locked(hours)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MomentGateState.Locked(0f))
```

**Step 3: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```
feat: add MomentGateState to HomeViewModel for pre-gate teaser UI
```

---

### Task 7: Build Pre-Gate Teaser Card in MomentsStrip

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/home/MomentsStrip.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/home/HomeScreen.kt`

**Step 1: Update MomentsStrip to accept gate state and show teaser**

Rewrite `MomentsStrip.kt` to handle both locked and unlocked states:

```kotlin
@Composable
fun MomentsStrip(
    moments: List<Moment>,
    gateState: MomentGateState,
    onMomentTap: (Moment) -> Unit,
    onShareMoment: (Moment) -> Unit,
    onSeeAll: () -> Unit
) {
    // Show teaser when locked, OR show moments when unlocked and non-empty
    when (gateState) {
        is MomentGateState.Locked -> {
            MomentsLockedTeaser(progressHours = gateState.progressHours)
        }
        is MomentGateState.Unlocked -> {
            if (moments.isEmpty()) return
            // ... existing moments strip code (header + cards) ...
        }
    }
}
```

**Step 2: Create the locked teaser composable**

Inside `MomentsStrip.kt`, add:

```kotlin
@Composable
private fun MomentsLockedTeaser(progressHours: Float) {
    Column {
        Text(
            text = "Moments",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Locked",
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Keep listening to unlock your first Moment",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Progress bar
                val progress = (progressHours / MomentPriority.GATE_HOURS).coerceIn(0f, 1f)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "%.1f / %.0f hours".format(progressHours, MomentPriority.GATE_HOURS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
```

**Step 3: Update HomeScreen to pass gate state**

In `HomeScreen.kt`, collect the new state (~after line 91):

```kotlin
val momentGateState by viewModel.momentGateState.collectAsState()
```

Update the `MomentsStrip` call (~line 432) to pass the gate state:

```kotlin
MomentsStrip(
    moments = recentMoments,
    gateState = momentGateState,
    onMomentTap = { moment -> ... },
    onShareMoment = { moment -> ... },
    onSeeAll = onSeeAllMoments
)
```

**Step 4: Build to verify**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```
feat: add locked teaser card for moments when gate not yet reached
```

---

### Task 8: Update AllMomentsScreen for Released-Only Display

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsScreen.kt` (minor — the DAO query change in Task 3 already handles this)
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt` (if needed)

**Step 1: Verify AllMomentsScreen needs no changes**

The `AllMomentsViewModel` calls `momentsRepository.getAllMoments()` which calls `momentDao.getAllMoments()`. Since we already updated that query in Task 3 to filter by `releasedAt IS NOT NULL`, the AllMomentsScreen automatically only shows released moments. No code change needed here.

**Step 2: Verify by building**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL — no changes needed for this task.

**Step 3: Commit (skip if no changes)**

No commit needed — this was covered by Task 3's DAO changes.

---

### Task 9: Run Tests and Fix Any Breakages

**Files:**
- Modify: `app/src/androidTest/java/com/musicstats/app/data/dao/MomentDaoTest.kt` (update tests for releasedAt)
- Modify: `app/src/androidTest/java/com/musicstats/app/service/MomentDetectorTest.kt` (if needed)

**Step 1: Update MomentDaoTest**

Tests that insert Moment objects and then query via `getAllMoments()`, `getRecentMoments()`, etc. will now return empty unless `releasedAt` is set. Update test helper moments to include `releasedAt = System.currentTimeMillis()`:

For every `Moment(...)` created in tests that is expected to appear in display queries, add `releasedAt = System.currentTimeMillis()`.

For tests specifically about unseen/seen behavior, ensure `releasedAt` is set.

**Step 2: Add a test for unreleased moments not appearing**

```kotlin
@Test
fun unreleasedMomentsAreHiddenFromDisplayQueries() = runTest {
    val released = testMoment("TYPE_A", "key1", releasedAt = System.currentTimeMillis())
    val unreleased = testMoment("TYPE_B", "key2", releasedAt = null)
    dao.insert(released)
    dao.insert(unreleased)

    val all = dao.getAllMoments().first()
    assertEquals(1, all.size)
    assertEquals("TYPE_A", all[0].type)
}
```

**Step 3: Run tests**

Run: `./gradlew connectedDebugAndroidTest` (or run on emulator)
Expected: All tests pass

**Step 4: Commit**

```
test: update MomentDao tests for releasedAt gating
```

---

### Task 10: Write MomentReleaseScheduler Tests

**Files:**
- Create: `app/src/androidTest/java/com/musicstats/app/service/MomentReleaseSchedulerTest.kt`

**Step 1: Write tests for the scheduler**

Key test cases:

```kotlin
@Test
fun releaseNext_returnsNullWhenGateNotReached()
// Insert moments, set total listening < 5h, verify releaseNext() returns null

@Test
fun releaseNext_releasesHighestPriorityFirst()
// Insert tier-5 and tier-2 moments, verify tier-2 is released first

@Test
fun releaseNext_respectsDailyLimit()
// Release one moment, call again same day, verify returns null

@Test
fun releaseNext_expiresOldTier3to5Moments()
// Insert a tier-5 moment triggered 15 days ago, verify it's deleted

@Test
fun releaseNext_neverExpiresTier1or2()
// Insert a tier-1 moment triggered 30 days ago, verify it's still available

@Test
fun releaseNext_newerMomentJumpsQueue()
// Insert old tier-5, then new tier-2, verify tier-2 released first
```

These tests use Room in-memory database, similar to the existing `MomentDaoTest` and `MomentDetectorTest` patterns. They need to insert `ListeningEvent` rows to control the total listening time for the gate check.

**Step 2: Run tests**

Run: `./gradlew connectedDebugAndroidTest`
Expected: All tests pass

**Step 3: Commit**

```
test: add MomentReleaseScheduler tests
```

---

### Task 11: Final Integration Build + Manual Smoke Test

**Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Run all tests**

Run: `./gradlew test`
Expected: All pass

**Step 3: Manual smoke test checklist**

- [ ] Fresh install: moments section shows locked teaser with progress bar at 0.0/5.0h
- [ ] After some listening: progress bar advances
- [ ] After 5h total: first moment appears (highest priority from queue)
- [ ] Next day: second moment appears
- [ ] Tier 1-3 release triggers push notification
- [ ] Tier 4-5 release is silent
- [ ] AllMomentsScreen only shows released moments
- [ ] Existing users (migration): all prior moments remain visible

**Step 4: Commit any fixes from smoke testing**
