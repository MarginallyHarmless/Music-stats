# Stats Reliability Fixes

## Date
2026-03-02

## Problem

Three related bugs affect the reliability of listening stats:

1. **Phantom playback** — "time listened today" ticker keeps running after music stops. The tracker's `isPlaying` stays `true` when a music app (observed with Apple Music) fails to send a pause/stop state change (e.g., Bluetooth disconnect, app killed, end of queue).

2. **Duplicate events** — A single play gets recorded multiple times with the same timestamp. Rapid-fire metadata/state callbacks from the music app trigger `saveCurrentIfPlaying()` multiple times, each with a slightly different `startedAt` that bypasses the 5-second dedup window.

3. **Inflated duration** — Events show 10 minutes for a short song. With `isPlaying` stuck true, `estimateCurrentPositionMs()` extrapolates from the last known position using wall-clock time, producing durations far exceeding the actual song length. Position-based duration has no cap.

## Root Cause

All three bugs share a common root: the tracker trusts the media session state too much and has no way to detect stale sessions or cap unreasonable durations.

## Design

### 1. Heartbeat Watchdog

**File:** `MediaSessionTracker.kt`

A coroutine launched when playback starts, cancelled when playback stops/resets. Runs every 30 seconds while `isPlaying == true`:

1. Snapshot `lastKnownPositionMs` and `lastPositionUpdateRealtime`
2. Wait 30 seconds
3. Check if either value changed (media session sent a position update)
4. If no change for 2 consecutive checks (60s total) → playback is dead → call `saveCurrentIfPlaying()` and `resetTracking()`

Requires storing a `Job` reference, cancelled in `resetTracking()`.

Uses 2 consecutive missed checks (60s) to avoid false positives with apps that are slow to send position updates.

### 2. Duration Cap Using Media Length

**File:** `MediaSessionTracker.kt`, `calculateDuration()`

Currently only wall-clock fallback has a cap. Fix: when `currentMediaDurationMs` is known, cap both position-based and wall-clock duration at `mediaLength * 1.5` (150%). This is generous enough for slow playback speeds but prevents a 3-minute song from recording as 10 minutes.

### 3. Wider Dedup Window

**File:** `MusicRepository.kt` → call to `findBySongNearTime()`

Change the dedup window from 5 seconds to 60 seconds. Catches rapid-fire callbacks that create multiple events with slightly different `startedAt` timestamps for the same actual play.

### 4. Guard Against Rapid Re-tracking

**File:** `MediaSessionTracker.kt`, `onMetadataChanged()` and `startTracking()`

When `startTracking` is called from `onMetadataChanged` (no PlaybackState available), it sets `playStartPositionMs = lastKnownPositionMs ?: 0L`. If `lastKnownPositionMs` was just reset by `saveCurrentIfPlaying()` → `resetTracking()`, the start position becomes 0, causing inflated duration on the next save.

Fix: when calling `startTracking(null)` after a metadata-triggered save, preserve the last known position as the new start position instead of resetting to 0. Concretely, `startTracking` already reads `lastKnownPositionMs`, but `resetTracking()` clears it before `startTracking` runs. Reorder so position is captured before reset, or pass it explicitly.

## Scope

All changes are app-agnostic. Nothing checks `sourceApp` or has app-specific branching. The bugs were observed with Apple Music but the fixes protect against the same patterns from any music app.

## Files Changed

- `app/src/main/java/com/musicstats/app/service/MediaSessionTracker.kt` — watchdog, duration cap, re-tracking guard
- `app/src/main/java/com/musicstats/app/data/repository/MusicRepository.kt` — wider dedup window
