# Weekly Challenges — Design Document

**Date:** 2026-03-04
**Status:** Approved

## Overview

App-generated weekly challenges that nudge users to explore, diversify, or deepen their listening habits. Challenges are generated every Monday based on the user's actual listening patterns from the previous week. The system is fully passive — no user setup required.

## Core Principles

- **Pattern-reactive:** Challenges are generated from real listening data, not random templates
- **Effortless:** No user input to create or configure challenges
- **Lightweight completion:** Simple checkmark on completion, no reward systems or badges
- **2-3 per week:** Enough variety without being overwhelming

## Challenge Generators (14 total)

Each generator analyzes a specific dimension of last week's listening and returns a challenge or `null` if the pattern isn't interesting enough.

| # | Generator | Analyzes | Example Challenge |
|---|-----------|----------|-------------------|
| 1 | **Discovery Stretch** | New songs discovered last week | "You found 3 new songs last week. Discover 6 this week." |
| 2 | **Genre Explorer** | Genre concentration | "90% of your week was hip-hop. Listen to 20 min of something else." |
| 3 | **Artist Deep Dive** | Shallow artist plays | "You played [Artist] twice. Give them a real chance — 5 plays this week." |
| 4 | **Rediscovery** | Songs unplayed 30+ days | "You haven't played [Song] since January. Revisit it." |
| 5 | **Consistency** | Daily listening variance | "You only listened on 4 days last week. Hit all 7 this week." |
| 6 | **Time Stretch** | Total listening time trend | "You averaged 45 min/day. Push for 1 hour today." |
| 7 | **No-Skip Run** | Skip rate last week | "You skipped 40% of songs. Try a no-skip hour — play an album front to back." |
| 8 | **Night Owl / Early Bird** | Listening time-of-day distribution | "All your listening was after 8pm. Try a morning session before noon." |
| 9 | **Marathon Session** | Longest session length | "Your longest session was 35 min. Go for a 1-hour marathon." |
| 10 | **Artist Variety** | Unique artist count | "You only listened to 6 artists. Explore 12 different ones this week." |
| 11 | **Loyalty Test** | Top song play trajectory | "[Song] is your current obsession at 15 plays. Can you hit 25?" |
| 12 | **Source Swap** | Source app concentration | "You've only used Spotify. Try discovering something on YouTube Music." |
| 13 | **Old Favorite** | High play count songs with no recent plays | "You used to love [Song] (47 plays). It's been 2 months — play it again." |
| 14 | **Weekend Warrior** | Weekday vs weekend listening gap | "You barely listen on weekends. Hit 30 min on Saturday." |

### Generation Logic

1. Run all 14 generators against last week's data.
2. Each returns a `Challenge?` — `null` if the pattern isn't applicable or data is insufficient.
3. Filter out any challenge type that appeared last week (cooldown rule — same type can't repeat consecutive weeks).
4. Randomly pick 2-3 from the remaining non-null results.
5. Persist to Room.

### Gating

- **5-hour total listening gate:** Shares the existing Moments gate — challenges don't appear until 5 hours total.
- **2-week minimum:** Generators need at least 2 full weeks of listening data to analyze patterns.

## Data Model

### Challenge Entity

```kotlin
@Entity(
    tableName = "challenges",
    indices = [Index(value = ["type", "weekStart"], unique = true)]
)
data class Challenge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,              // e.g., "DISCOVERY_STRETCH", "GENRE_EXPLORER"
    val weekStart: Long,           // Monday midnight epoch millis (identifies the week)
    val title: String,             // "Discover 6 new songs"
    val description: String,       // "You found 3 new songs last week..."
    val targetValue: Float,        // 6.0 (the goal number)
    val currentValue: Float,       // 4.0 (progress so far, cached)
    val completed: Boolean,        // true when currentValue >= targetValue
    val completedAt: Long?,        // epoch millis when completed
    val generatedAt: Long,         // epoch millis when created
    val metadata: String?          // JSON blob for generator-specific context
)
```

### Key Design Decisions

- **Immutable targets:** Challenge targets can't change mid-week.
- **Cached progress:** `currentValue` is recalculated from existing data (ListeningEvents, Songs, etc.) on app open and by the daily worker. It's cached in the entity for display.
- **Keep history forever:** Old challenges are tiny rows, no cleanup needed.
- **Unique constraint:** `type + weekStart` — max one challenge of each type per week.

## Architecture

```
ChallengeGenerator (service)
├── 14 generator functions, each returns Challenge?
├── Called by ChallengeWorker (Monday) or HomeViewModel (app open)
├── Picks 2-3 random challenges from non-null results (with cooldown)
└── Persists to Room via ChallengeDao

ChallengeDao
├── insertChallenge()
├── getActiveChallenges(weekStart) -> Flow<List<Challenge>>
├── getChallengeHistory() -> Flow<List<Challenge>>
├── updateProgress(id, currentValue, completed, completedAt)

ChallengeProgressUpdater (service)
├── Recalculates currentValue for each active challenge
├── Uses existing DAOs (ListeningEventDao, SongDao, ArtistDao)
├── Called on app open + daily by worker
└── Sets completed = true when currentValue >= targetValue

ChallengeWorker (WorkManager)
├── Monday: generate new challenges
├── Daily: update progress
└── Shares scheduling pattern with MomentWorker

HomeViewModel
├── Exposes Flow<List<Challenge>> for active challenges
├── Triggers generation on Monday if needed
├── Triggers progress update on app open
```

## UI Design

### 1. Home Screen — Challenge Strip

Below the existing Moments strip:
- Section header: "This Week's Challenges"
- 2-3 horizontal challenge cards
- Each card shows: title, progress bar (currentValue / targetValue), checkmark if completed
- Tapping a card opens the detail bottom sheet

### 2. Challenge Detail Bottom Sheet

Similar to existing Moment detail sheet:
- Full title and description
- Progress bar with numeric values
- What the generator noticed ("You discovered 3 new songs last week")
- Time remaining ("4 days left")

### 3. Challenge History

Accessible from the Stats screen:
- Scrollable list of past weeks
- Each week shows 2-3 challenges with completion status
- Simple list view — no complex visualizations

### UI Placement

- No new bottom nav tab
- Challenges live within the Home screen (strip) and Stats screen (history)
- Follows existing bottom sheet pattern for detail views

## New Files

| File | Purpose |
|------|---------|
| `data/model/Challenge.kt` | Room entity |
| `data/dao/ChallengeDao.kt` | Room DAO |
| `service/ChallengeGenerator.kt` | 14 generators + selection logic |
| `service/ChallengeProgressUpdater.kt` | Progress recalculation |
| `service/ChallengeWorker.kt` | WorkManager periodic task |
| `ui/components/ChallengeCard.kt` | Challenge card composable |
| `ui/home/ChallengeDetailSheet.kt` | Detail bottom sheet |
| `ui/challenges/ChallengeHistoryScreen.kt` | History view |
| Room migration | New `challenges` table |

## Dependencies on Existing Code

- `ListeningEventDao` — queries for time, sessions, skips, daily counts
- `SongDao` / `ArtistDao` — queries for discovery, genre, play counts
- `MomentReleaseScheduler` — shares the 5-hour gate check
- `HomeViewModel` — hosts challenge state alongside moments
- `MusicStatsDatabase` — new migration for challenges table
