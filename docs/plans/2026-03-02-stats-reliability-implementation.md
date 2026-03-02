# Stats Reliability Fixes Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix three related bugs — phantom playback, duplicate events, and inflated duration — by adding a heartbeat watchdog, duration cap, wider dedup window, and re-tracking guard.

**Architecture:** All fixes are in `MediaSessionTracker.kt` (watchdog, duration cap, re-tracking guard) and `MusicRepository.kt` (dedup window). The watchdog is a coroutine `Job` that runs while `isPlaying == true` and auto-saves + resets if no position updates are received for 60 seconds.

**Tech Stack:** Kotlin, Coroutines (Job, delay), MockK for tests, JUnit 4

---

### Task 1: Wider Dedup Window

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt:87`
- Test: `app/src/test/java/com/musicstats/app/data/repository/MusicRepositoryTest.kt`

**Step 1: Write the failing test**

Add a test to `MusicRepositoryTest.kt` that verifies events within 60s are deduplicated:

```kotlin
@Test
fun `recordPlay deduplicates events within 60 second window`() = runTest {
    val existingSong = Song(id = 10, title = "Song", artist = "Artist", firstHeardAt = 1000L)
    val existingEvent = ListeningEvent(id = 100, songId = 10, startedAt = 1000L, durationMs = 60_000, sourceApp = "com.apple.music", completed = true)

    coEvery { artistDao.findByName("Artist") } returns Artist(1, "Artist", 1000L)
    coEvery { songDao.findByTitleAndArtist("Song", "Artist") } returns existingSong
    coEvery { eventDao.findBySongNearTime(10L, 1030_000L, 60_000L) } returns existingEvent

    val event = repository.recordPlay("Song", "Artist", null, "com.apple.music", 1030_000L, 60_000L, true)

    assertEquals(100L, event.id)
    coVerify(exactly = 0) { eventDao.insert(any()) }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.musicstats.app.data.repository.MusicRepositoryTest.recordPlay deduplicates events within 60 second window"`
Expected: FAIL — the current code calls `findBySongNearTime` with default 5000ms window, not 60000ms.

**Step 3: Change the dedup window**

In `MusicRepository.kt` line 87, change:
```kotlin
val existing = eventDao.findBySongNearTime(songId, startedAt)
```
to:
```kotlin
val existing = eventDao.findBySongNearTime(songId, startedAt, windowMs = 60_000L)
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.musicstats.app.data.repository.MusicRepositoryTest"`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt app/src/test/java/com/musicstats/app/data/repository/MusicRepositoryTest.kt
git commit -m "fix: widen dedup window from 5s to 60s to prevent duplicate events"
```

---

### Task 2: Duration Cap Using Media Length

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt:85-112`
- Test: `app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt`

**Step 1: Write the failing test**

The existing test helpers create `PlaybackState` mocks without position data. We need a richer helper. Add these to `MediaSessionTrackerTest.kt`:

```kotlin
private fun createMetadataWithDuration(title: String, artist: String, durationMs: Long): MediaMetadata {
    val metadata = createMetadata(title, artist)
    every { metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) } returns durationMs
    return metadata
}

private fun createPlaybackStateWithPosition(state: Int, position: Long, updateTime: Long = android.os.SystemClock.elapsedRealtime()): PlaybackState {
    val playbackState = mockk<PlaybackState>()
    every { playbackState.state } returns state
    every { playbackState.position } returns position
    every { playbackState.lastPositionUpdateTime } returns updateTime
    every { playbackState.playbackSpeed } returns 1.0f
    return playbackState
}
```

Then add the test:

```kotlin
@Test
fun `duration is capped at 150 percent of media duration`() = runTest {
    coEvery { repository.recordPlay(any(), any(), any(), any(), any(), any(), any(), any()) } returns mockk()

    // Set up a 3-minute song (180_000ms)
    val metadata = createMetadataWithDuration("Short Song", "Artist", 180_000L)
    tracker.onMetadataChanged(metadata, "com.apple.music", scope)

    // Start playing at position 0
    val playState = createPlaybackStateWithPosition(PlaybackState.STATE_PLAYING, 0L)
    tracker.onPlaybackStateChanged(playState, "com.apple.music", scope)

    // Pause with position claiming 10 minutes (600_000ms) — way beyond the 3min song
    val pauseState = createPlaybackStateWithPosition(PlaybackState.STATE_PAUSED, 600_000L)
    tracker.onPlaybackStateChanged(pauseState, "com.apple.music", scope)

    // Should record with duration capped at 180_000 * 1.5 = 270_000ms, NOT 600_000ms
    coVerify {
        repository.recordPlay(
            title = "Short Song",
            artist = "Artist",
            album = any(),
            sourceApp = "com.apple.music",
            startedAt = any(),
            durationMs = match { it <= 270_000L },
            completed = any(),
            albumArtUrl = any()
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest.duration is capped at 150 percent of media duration"`
Expected: FAIL — position-based duration currently has no cap, so it records 600_000ms.

**Step 3: Add media duration cap to calculateDuration()**

In `MediaSessionTracker.kt`, replace the `calculateDuration()` method (lines 85-112) with:

```kotlin
private fun calculateDuration(): Long {
    val startPos = playStartPositionMs
    val currentPos = estimateCurrentPositionMs()
    val mediaDur = currentMediaDurationMs
    val maxByMedia = if (mediaDur != null && mediaDur > 0) (mediaDur * 1.5).toLong() else null

    // Position-based: use media positions if both are available
    if (startPos != null && currentPos != null && currentPos >= startPos) {
        val positionDuration = currentPos - startPos
        Log.d(TAG, "  Duration from position: ${positionDuration}ms (startPos=$startPos, currentPos=$currentPos)")
        DebugLog.log(DebugEventType.TRACKING, "Duration: ${positionDuration}ms (position-based, start=$startPos, cur=$currentPos)")
        // Cap position-based duration at 150% of media length when known
        if (maxByMedia != null && positionDuration > maxByMedia) {
            Log.d(TAG, "  Capped position duration from ${positionDuration}ms to ${maxByMedia}ms (media=${mediaDur}ms)")
            DebugLog.log(DebugEventType.TRACKING, "Capped: ${positionDuration}ms -> ${maxByMedia}ms (media=${mediaDur}ms)")
            return maxByMedia
        }
        return positionDuration
    }

    // Fallback: wall-clock time
    val startTime = playStartTime ?: return 0
    val wallClockDuration = System.currentTimeMillis() - startTime
    Log.d(TAG, "  Duration from wall-clock (fallback): ${wallClockDuration}ms")
    DebugLog.log(DebugEventType.TRACKING, "Duration: ${wallClockDuration}ms (wall-clock fallback)")

    // Apply cap to wall-clock fallback
    return if (maxByMedia != null && wallClockDuration > maxByMedia) {
        maxByMedia
    } else if (wallClockDuration > MAX_DURATION_MS) {
        MAX_DURATION_MS
    } else {
        wallClockDuration
    }
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest"`
Expected: ALL PASS

**Step 5: Also update createMetadata to stub getLong for METADATA_KEY_DURATION**

The existing `createMetadata()` helper doesn't stub `getLong`. Add to the existing `createMetadata` method:

```kotlin
every { metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) } returns 0L
```

This prevents the other tests from breaking when `onMetadataChanged` reads the duration.

**Step 6: Run all tests**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest"`
Expected: ALL PASS

**Step 7: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt
git commit -m "fix: cap duration at 150% of media length for both position and wall-clock"
```

---

### Task 3: Guard Against Rapid Re-tracking

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt:160-191` and `saveCurrentIfPlaying`
- Test: `app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt`

**Step 1: Write the failing test**

```kotlin
@Test
fun `metadata track change preserves position for new track start`() = runTest {
    coEvery { repository.recordPlay(any(), any(), any(), any(), any(), any(), any(), any()) } returns mockk()

    // Play Song 1 with position data
    val metadata1 = createMetadataWithDuration("Song 1", "Artist", 200_000L)
    tracker.onMetadataChanged(metadata1, "com.spotify", scope)
    val playState = createPlaybackStateWithPosition(PlaybackState.STATE_PLAYING, 0L)
    tracker.onPlaybackStateChanged(playState, "com.spotify", scope)

    // Simulate position advancing to 120s
    val posUpdate = createPlaybackStateWithPosition(PlaybackState.STATE_PLAYING, 120_000L)
    tracker.onPlaybackStateChanged(posUpdate, "com.spotify", scope)

    // Metadata changes to Song 2 while playing — triggers save + re-track
    val metadata2 = createMetadataWithDuration("Song 2", "Artist", 180_000L)
    tracker.onMetadataChanged(metadata2, "com.spotify", scope)

    // Song 1 should have been saved with ~120s duration (not inflated)
    coVerify {
        repository.recordPlay(
            title = "Song 1",
            artist = "Artist",
            album = any(),
            sourceApp = "com.spotify",
            startedAt = any(),
            durationMs = match { it in 100_000L..140_000L },
            completed = any(),
            albumArtUrl = any()
        )
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest.metadata track change preserves position for new track start"`
Expected: FAIL — `saveCurrentIfPlaying` calls `resetTracking()` which clears `lastKnownPositionMs`, then `startTracking(null)` sets `playStartPositionMs = 0L`, so the next save will have inflated duration.

**Step 3: Fix the re-tracking issue**

In `MediaSessionTracker.kt`, modify the track-change block in `onMetadataChanged` (lines 160-192). The fix: capture the last known position *before* saving (which resets tracking). Change:

```kotlin
        if (title != currentTitle || artist != currentArtist) {
            Log.d(TAG, "  NEW track: $title by $artist")
            DebugLog.log(DebugEventType.METADATA, "NEW track: $title by $artist")
            saveCurrentIfPlaying(scope)
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentSourceApp = sourceApp

            // Extract media duration for completed detection
            val mediaDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            currentMediaDurationMs = if (mediaDuration > 0) mediaDuration else null

            // Extract album art ...
            // (keep existing album art code unchanged)

            if (isPlaying) {
                startTracking(null)
            }
        }
```

to:

```kotlin
        if (title != currentTitle || artist != currentArtist) {
            Log.d(TAG, "  NEW track: $title by $artist")
            DebugLog.log(DebugEventType.METADATA, "NEW track: $title by $artist")
            // Capture position before save+reset clears it
            val positionBeforeReset = lastKnownPositionMs
            saveCurrentIfPlaying(scope)
            currentTitle = title
            currentArtist = artist
            currentAlbum = album
            currentSourceApp = sourceApp

            // Extract media duration for completed detection
            val mediaDuration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            currentMediaDurationMs = if (mediaDuration > 0) mediaDuration else null

            // Extract album art ...
            // (keep existing album art code unchanged)

            if (isPlaying) {
                // Use captured position as the new start position since
                // resetTracking() (called by save) cleared lastKnownPositionMs
                startTracking(null, overrideStartPosition = positionBeforeReset)
            }
        }
```

And update `startTracking` to accept an optional override:

```kotlin
private fun startTracking(state: PlaybackState?, overrideStartPosition: Long? = null) {
    playStartTime = System.currentTimeMillis()
    updatePositionFromState(state)
    playStartPositionMs = overrideStartPosition ?: lastKnownPositionMs ?: 0L
    _isPlayingFlow.value = true
    _currentSessionStartMs.value = playStartTime!!
    Log.d(TAG, "  Started tracking: startTime=$playStartTime, startPosition=$playStartPositionMs")
    DebugLog.log(DebugEventType.TRACKING, "Started | startPos=$playStartPositionMs | lastKnown=$lastKnownPositionMs")
}
```

**Step 4: Run tests**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest"`
Expected: ALL PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt
git commit -m "fix: preserve position across metadata-triggered track changes"
```

---

### Task 4: Heartbeat Watchdog

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt`
- Test: `app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt`

This is the most involved change. The watchdog is a coroutine `Job` that:
- Starts when `startTracking()` is called
- Cancels when `resetTracking()` is called
- Every 30s, checks if `lastPositionUpdateRealtime` changed
- After 2 consecutive stale checks (60s), auto-saves and resets

**Step 1: Write the failing test**

```kotlin
@Test
fun `watchdog auto-saves when position stops updating for 60 seconds`() = runTest {
    coEvery { repository.recordPlay(any(), any(), any(), any(), any(), any(), any(), any()) } returns mockk()

    val metadata = createMetadataWithDuration("Song", "Artist", 180_000L)
    tracker.onMetadataChanged(metadata, "com.apple.music", scope)

    // Start playing at position 0
    val playState = createPlaybackStateWithPosition(PlaybackState.STATE_PLAYING, 50_000L)
    tracker.onPlaybackStateChanged(playState, "com.apple.music", scope)

    // Advance virtual time past two watchdog intervals (60s)
    advanceTimeBy(61_000L)
    runCurrent()

    // Watchdog should have auto-saved and reset
    coVerify(exactly = 1) {
        repository.recordPlay(
            title = "Song",
            artist = "Artist",
            album = any(),
            sourceApp = "com.apple.music",
            startedAt = any(),
            durationMs = any(),
            completed = any(),
            albumArtUrl = any()
        )
    }

    // isPlayingFlow should be false now
    assertEquals(false, tracker.isPlayingFlow.value)
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest.watchdog auto-saves when position stops updating for 60 seconds"`
Expected: FAIL — no watchdog exists yet.

**Step 3: Implement the watchdog**

Add to `MediaSessionTracker.kt`:

1. New import at top:
```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
```

2. New field after `lastPlaybackSpeed`:
```kotlin
private var watchdogJob: Job? = null
```

3. New constant in companion object:
```kotlin
private const val WATCHDOG_INTERVAL_MS = 30_000L  // check every 30s
private const val WATCHDOG_STALE_CHECKS = 2        // 2 missed checks = 60s
```

4. New method:
```kotlin
private fun launchWatchdog(scope: CoroutineScope) {
    watchdogJob?.cancel()
    watchdogJob = scope.launch {
        var staleCount = 0
        var previousUpdateTime = lastPositionUpdateRealtime
        while (true) {
            delay(WATCHDOG_INTERVAL_MS)
            val currentUpdateTime = lastPositionUpdateRealtime
            if (currentUpdateTime == previousUpdateTime) {
                staleCount++
                Log.d(TAG, "  Watchdog: no position update ($staleCount/$WATCHDOG_STALE_CHECKS)")
                DebugLog.log(DebugEventType.TRACKING, "Watchdog: stale $staleCount/$WATCHDOG_STALE_CHECKS")
                if (staleCount >= WATCHDOG_STALE_CHECKS) {
                    Log.d(TAG, "  Watchdog: position stale for ${staleCount * WATCHDOG_INTERVAL_MS}ms, auto-saving")
                    DebugLog.log(DebugEventType.TRACKING, "Watchdog: auto-save, position stale")
                    synchronized(this@MediaSessionTracker) {
                        if (isPlaying) {
                            saveCurrentIfPlaying(scope)
                            isPlaying = false
                        }
                    }
                    break
                }
            } else {
                staleCount = 0
                previousUpdateTime = currentUpdateTime
            }
        }
    }
}
```

5. Call `launchWatchdog(scope)` at the end of `startTracking`. But `startTracking` doesn't have `scope` — we need to thread it through. Update `startTracking` signature:

```kotlin
private fun startTracking(state: PlaybackState?, overrideStartPosition: Long? = null, scope: CoroutineScope? = null) {
    playStartTime = System.currentTimeMillis()
    updatePositionFromState(state)
    playStartPositionMs = overrideStartPosition ?: lastKnownPositionMs ?: 0L
    _isPlayingFlow.value = true
    _currentSessionStartMs.value = playStartTime!!
    Log.d(TAG, "  Started tracking: startTime=$playStartTime, startPosition=$playStartPositionMs")
    DebugLog.log(DebugEventType.TRACKING, "Started | startPos=$playStartPositionMs | lastKnown=$lastKnownPositionMs")
    scope?.let { launchWatchdog(it) }
}
```

6. Update all call sites of `startTracking` to pass `scope`:
- `onPlaybackStateChanged` line 207: `startTracking(state)` → `startTracking(state, scope = scope)`
- `onPlaybackStateChanged` line 216: `startTracking(state)` → `startTracking(state, scope = scope)`
- `onPlaybackStateChanged` line 238: `startTracking(state)` → `startTracking(state, scope = scope)`
- `onMetadataChanged` line ~191: `startTracking(null, overrideStartPosition = positionBeforeReset)` → `startTracking(null, overrideStartPosition = positionBeforeReset, scope = scope)`

7. Cancel watchdog in `resetTracking()`:
```kotlin
private fun resetTracking() {
    watchdogJob?.cancel()
    watchdogJob = null
    playStartTime = null
    playStartPositionMs = null
    lastKnownPositionMs = null
    lastPositionUpdateRealtime = null
    lastPlaybackSpeed = 1.0f
    _isPlayingFlow.value = false
    _currentSessionStartMs.value = 0L
}
```

**Step 4: Run tests**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest"`
Expected: ALL PASS

**Step 5: Write a second test — watchdog resets when position updates arrive**

```kotlin
@Test
fun `watchdog does not auto-save when position keeps updating`() = runTest {
    coEvery { repository.recordPlay(any(), any(), any(), any(), any(), any(), any(), any()) } returns mockk()

    val metadata = createMetadataWithDuration("Song", "Artist", 300_000L)
    tracker.onMetadataChanged(metadata, "com.spotify", scope)

    val playState = createPlaybackStateWithPosition(PlaybackState.STATE_PLAYING, 0L)
    tracker.onPlaybackStateChanged(playState, "com.spotify", scope)

    // Simulate position updates every 20s (before 30s watchdog interval)
    repeat(4) { i ->
        advanceTimeBy(20_000L)
        val posUpdate = createPlaybackStateWithPosition(
            PlaybackState.STATE_PLAYING,
            (i + 1) * 20_000L
        )
        tracker.onPlaybackStateChanged(posUpdate, "com.spotify", scope)
        runCurrent()
    }

    // No auto-save should have happened — position was updating
    coVerify(exactly = 0) { repository.recordPlay(any(), any(), any(), any(), any(), any(), any(), any()) }

    // Still playing
    assertEquals(true, tracker.isPlayingFlow.value)
}
```

**Step 6: Run all tests**

Run: `./gradlew test --tests "com.musicstats.app.service.MediaSessionTrackerTest"`
Expected: ALL PASS

**Step 7: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt
git commit -m "fix: add heartbeat watchdog to detect stale playback sessions"
```

---

### Task 5: Final Integration Test and Cleanup

**Files:**
- Test: `app/src/test/java/com/musicstats/app/service/MediaSessionTrackerTest.kt`
- Test: `app/src/test/java/com/musicstats/app/data/repository/MusicRepositoryTest.kt`

**Step 1: Run the full test suite**

Run: `./gradlew test`
Expected: ALL PASS

**Step 2: Verify no regressions in existing tests**

Check that all 6 original tests in `MediaSessionTrackerTest` and all 3 in `MusicRepositoryTest` still pass.

**Step 3: Final commit if any cleanup needed**

If tests revealed issues, fix and commit with an appropriate message.
