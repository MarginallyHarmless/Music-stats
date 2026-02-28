# Moments & Shareable Stats — Design Doc

**Date:** 2026-02-28
**Status:** Approved

## Overview

Add a "Moments" system that detects interesting events in a user's listening history and surfaces them as shareable cards. Moments are real-time (detected on app open and via daily background job), visually distinct, and designed for social media sharing.

---

## Moment Types

### Milestone Achievements

| Type key | Trigger | Example text |
|---|---|---|
| `SONG_PLAYS_10/25/50/100/250/500` | Song play count crosses threshold | "You've played *Blinding Lights* 100 times" |
| `ARTIST_HOURS_1/5/10/24` | Artist total listening time crosses threshold | "You've spent 24 hours listening to The Weeknd" |
| `STREAK_3/7/14/30/100` | Consecutive listening days | "7-day listening streak" |
| `TOTAL_HOURS_24/100/500/1000` | All-time total listening time | "You've hit 100 total hours of music" |
| `SONGS_DISCOVERED_50/100/250/500` | Cumulative unique songs heard | "You've discovered 100 new songs" |

### Personality Archetypes

Computed from 30-day behavior window, re-evaluated monthly. Only one archetype per category is active at a time.

| Type key | Condition |
|---|---|
| `ARCHETYPE_NIGHT_OWL` | >50% of listening between 10pm–4am |
| `ARCHETYPE_MORNING_LISTENER` | >50% of listening before 9am |
| `ARCHETYPE_COMMUTE_LISTENER` | Peaks at 7–9am AND 5–7pm |
| `ARCHETYPE_COMPLETIONIST` | Skip rate < 5% |
| `ARCHETYPE_CERTIFIED_SKIPPER` | Skip rate > 40% |
| `ARCHETYPE_DEEP_CUT_DIGGER` | Any song with 50+ plays |
| `ARCHETYPE_LOYAL_FAN` | One artist accounts for >50% of total duration |
| `ARCHETYPE_EXPLORER` | ≥5 new artists discovered in the past week |

### Weird / Funny Outliers

Detected in a daily scan over the past 7 days.

| Type key | Trigger | Example text |
|---|---|---|
| `OBSESSION_DAILY` | Same song played 5+ times in one day | "You played *X* 7 times today. Are you okay?" |
| `DAILY_RITUAL` | Song played every day for 7+ consecutive days | "You've started every day with *X* for 10 days" |
| `BREAKUP_CANDIDATE` | Artist skipped >10 times in a week | "You skipped [Artist] 15 times this month. Maybe it's time to break up?" |
| `FAST_OBSESSION` | First heard N days ago + high play count | "*X* came into your life 14 days ago. You've played it 42 times." |
| `LONGEST_SESSION` | New personal best single listening session | "New personal best: 3h 47m in one sitting" |

### Discovery Moments

| Type key | Trigger | Example text |
|---|---|---|
| `ARTIST_UNLOCKED` | First ever listen to a new artist | "New artist unlocked: [Artist]" |
| `QUICK_OBSESSION` | Song enters top 5 within 7 days of first hear | "You discovered [Song] 7 days ago. It's already in your top 5." |
| `DISCOVERY_WEEK` | ≥8 new artists discovered in a week | "You discovered 8 new artists this week" |

---

## Architecture

### Detection: B + C hybrid

- **On app open** (`MomentDetector` called from HomeViewModel): batch queries detect any new moments since last check timestamp. Results persisted immediately and shown in the home screen feed.
- **Background** (`MomentWorker` via WorkManager, scheduled daily): same `MomentDetector` runs without the app open; fires push notifications for new moments.

Detection is **idempotent**: a `(type, entityId)` pair can only trigger once. The `MomentDetector` queries existing moments before inserting new ones.

### New Components

```
data/
  model/Moment.kt               # Room entity
  dao/MomentDao.kt              # CRUD + Flow queries
  repository/MomentsRepository.kt

service/
  MomentDetector.kt             # Pure detection logic, no Android deps
  MomentWorker.kt               # CoroutineWorker, calls detector + notifications

ui/
  home/MomentsStrip.kt          # Horizontal swipeable strip composable
  components/MomentCard.kt      # Individual card (home feed + full share preview)
  components/MomentShareCard.kt # Offscreen-renderable 360x640 shareable bitmap card
```

---

## Data Model

```kotlin
@Entity(tableName = "moments")
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,            // e.g. "SONG_PLAYS_100"
    val triggeredAt: Long,       // epoch millis
    val seenAt: Long? = null,    // null = unseen
    val sharedAt: Long? = null,
    val payloadJson: String,     // JSON with context (song title, count, etc.)
    val title: String,           // "100 plays"
    val description: String,     // "You've played Blinding Lights 100 times"
    val songId: Long? = null,    // for album art + palette
    val artistId: Long? = null
)
```

A `(type, entityId)` unique index prevents duplicate triggers. `entityId` is derived from `songId` or `artistId` where applicable; for global milestones (streak, total hours), it is the threshold value as a string.

---

## Home Screen Integration

A **moments strip** is inserted above the weekly bar chart on the home screen:

- Shows the 3 most recent unseen moments as horizontal swipeable cards
- An unseen badge dot appears until the card is tapped
- "See all" button at the end of the strip opens a full-screen moments list
- Tapping a card marks it as seen and opens the share card preview

---

## Shareable Card Design

Cards are **360×640 composable** (portrait, story format), rendered offscreen as a bitmap and handed to the Android share sheet.

**Visual system:**
- Background: 2-stop gradient using the song's `dominantColor` → `darkMutedColor` from the existing palette fields on `Song`
- Archetype cards use a themed gradient (e.g., deep navy for Night Owl, warm amber for Morning Listener)
- **Hero content**: large bold number or archetype name fills the upper 60% of the card
- **Subtext**: one punchy line below the hero
- **Album art / artist image**: circular, 120dp, top-right quadrant (milestone/discovery cards)
- **Archetype cards**: large emoji/icon instead of album art
- **Watermark**: "vibes" in small muted text at the bottom

**Share flow**: tap moment → full-screen card preview composable → native `ShareCompat` sheet (same mechanism as existing share FAB).

---

## Notifications

Each new moment detected by `MomentWorker` fires a single notification in the "Moments" channel:

- **Title**: moment title (e.g., "100 plays 🎵")
- **Body**: moment description
- **Tap action**: opens app to the moments list
- Notifications are grouped under a single summary if multiple fire at once

---

## Out of Scope

- Wrapped-style yearly summaries
- Server-side sharing or public profiles
- Social graph / comparing with friends
- Per-genre stats (genre data is sparse from notifications)
