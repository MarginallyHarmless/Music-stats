# Moment Card Gating System — Design

**Date:** 2026-03-03
**Status:** Approved

## Problem

The app has ~60 moment types with no global gating. A new user who listens actively on day 1 can trigger several low-threshold moments immediately (daily obsession, longest session, night binge), making the feature feel cheap and spammy rather than special.

## Design Goals

- Moments should feel significant and earned, not like spam
- Active listeners should unlock moments faster than passive ones
- Each moment should feel like an event, not a notification flood
- The user should know the feature exists before it activates

## Solution: Queue-Based Release System

All detected moments are persisted immediately but remain **queued** (invisible to the user). A release scheduler picks the single highest-priority moment from the queue once per day and "releases" it, making it visible.

### Data Model

Add one column to the `Moment` entity:

```kotlin
val releasedAt: Long? = null  // epoch millis; null = queued/invisible
```

UI queries filter by `releasedAt IS NOT NULL` — only released moments appear on the home screen, all-moments screen, and count toward the unseen badge.

### Priority Tiers

Each moment type maps to a priority tier (1 = rarest, 5 = most common):

| Tier | Label | Examples |
|---|---|---|
| 1 | Legendary | `SONG_PLAYS_500`, `STREAK_100`, `TOTAL_HOURS_1000`, `FLEX_COLLECTOR_5000` |
| 2 | Epic | `SONG_PLAYS_250`, `STREAK_30`, `TOTAL_HOURS_500`, `NARRATIVE_SOUNDTRACK`, `NARRATIVE_PARALLEL_LIVES` |
| 3 | Milestone | `SONG_PLAYS_100`, `ARTIST_HOURS_24`, `STREAK_14`, narrative moments, behavioral grower/drift |
| 4 | Notable | `SONG_PLAYS_50`, `ARTIST_HOURS_10`, `STREAK_7`, archetypes, most behavioral moments |
| 5 | Common | `OBSESSION_DAILY`, `LONGEST_SESSION`, `NIGHT_BINGE`, `COMFORT_ZONE` |

Priority is stored as a static lookup map (`MomentPriority` object), not in the database.

### Gate: 5-Hour Minimum

No moments are released until the user has accumulated **5 hours of total tracked listening time**. This ensures enough data exists for moments to feel meaningful.

- Casual listeners (~1-2h/day): gate opens after ~3-4 days
- Active listeners (~3-4h/day): gate opens after ~1-2 days
- The gate is checked by the release scheduler, not by the detector. Detection always runs so moments aren't missed.

### Daily Limit: 1 Moment Per Day

After the gate passes, the scheduler releases at most **1 moment per day**. "Day" = device local midnight-to-midnight.

### Release Priority Logic

When the scheduler runs:

1. Check total tracked hours >= 5. If not, return.
2. Check if a moment was already released today. If yes, return.
3. Query all unreleased moments (`releasedAt IS NULL`), ordered by:
   - Priority tier ASC (tier 1 first)
   - `triggeredAt` DESC (newest first within same tier)
4. Release the top result: set `releasedAt = now()`.

A freshly triggered tier-1 moment always "jumps the line" ahead of older common moments sitting in the queue.

### Queue Expiry

- **Tier 1-2 (legendary/epic):** Never expire. Too significant to silently drop.
- **Tier 3-5:** Expire after **14 days** unreleased. Prevents stale moments like "you played X 5 times today" from surfacing weeks later. Expired moments are silently deleted or marked as expired (never shown).

### Push Notifications

When the scheduler releases a **tier 1-3** moment, a push notification is sent to draw the user's attention. **Tier 4-5** moments release silently — the user discovers them on the home screen.

### Pre-Gate Teaser UI

Before the 5-hour gate is reached, the home screen shows a **locked teaser card** in the moments section:

- **Header:** "Moments" (same as the regular section)
- **Card:** Lock icon + "Keep listening to unlock your first Moment" + progress bar (`currentHours / 5h`)
- **Style:** Same card dimensions as a moment card but muted/dimmed. Accent color for the progress bar fill.
- **Disappears permanently** once the gate is passed and the first moment is released.

The `HomeViewModel` exposes a `GateState` sealed class:
- `Locked(progressHours: Float)` — show teaser
- `Unlocked` — show regular moments strip

### Migration & Existing Users

- **Room migration:** Add `releasedAt` column with default `NULL`
- **Backfill:** Set `releasedAt = triggeredAt` for all existing moments so they remain visible
- **Gate bypass:** If total hours >= 5 at migration time, the gate is already passed. Scheduler operates normally for new moments.

## Components Changed

| Component | Change |
|---|---|
| `Moment` entity | Add `releasedAt: Long?` column |
| Room migration | Add column + backfill existing rows |
| `MomentPriority` (new) | Object mapping each type string to tier 1-5 |
| `MomentReleaseScheduler` (new) | Gate check, daily limit, priority pick, 14-day expiry for tier 3-5 |
| `MomentDetector` | After detection, call `releaseScheduler.releaseNext()` |
| `MomentWorker` | After detection + release, check tier for push notification (1-3 only) |
| `MomentDao` | Add `releasedAt IS NOT NULL` filter to display queries; add `getUnreleasedByPriority()` query |
| `MomentsRepository` | Expose gate state (total hours vs threshold) |
| `HomeViewModel` | Expose `GateState` for teaser card |
| `MomentsStrip` | Show teaser card when locked, regular moments when unlocked |
| `AllMomentsScreen` | Only show released moments |
