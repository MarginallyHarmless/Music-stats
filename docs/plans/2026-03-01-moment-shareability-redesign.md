# Moment Cards Shareability Redesign

**Date:** 2026-03-01
**Status:** Approved

## Problem

Moment cards read like database query results. The copy is dry, all cards look identical regardless of achievement rarity, and there's no personal context that makes a moment feel like a genuine discovery worth sharing.

## Design Pillars

### 1. Copy & Tone — Playful, Cheeky Voice

Replace database-output copy with personality-driven writing via a centralized `MomentCopywriter.kt`.

**Structure per moment:**
- **Title** — Short, punchy headline ("Certified Obsession", "Beyond Help", "3am Vibes")
- **Description** — Cheeky context line with personality ("100 plays. At this point, The Weeknd owes you royalties.")
- **Stat reframes** — Soft estimations that make numbers relatable ("~4 full days of music", "Enough to fill a road trip playlist 3x over")

**Copy examples:**

| Type | Title | Description |
|------|-------|-------------|
| SONG_PLAYS_10 | On Repeat | 10 plays and counting — this one's got its hooks in you. |
| SONG_PLAYS_100 | Certified Obsession | 100 plays. At this point, {artist} owes you royalties. |
| SONG_PLAYS_500 | Beyond Help | 500 plays. This isn't a song anymore, it's a lifestyle. |
| ARCHETYPE_NIGHT_OWL | Night Owl | Most of your listening happens when normal people are asleep. No judgment. |
| ARCHETYPE_CERTIFIED_SKIPPER | Certified Skipper | You skip more songs than you finish. Bold strategy. |
| OBSESSION_DAILY | Today's Fixation | You played this {count} times today. The neighbors definitely know the lyrics. |
| NIGHT_BINGE | 3am Vibes | {duration} of music after midnight. Tomorrow-you is going to have opinions about this. |
| STREAK_30 | Unstoppable | 30 days straight. That's a whole month without silence. |
| COMFORT_ZONE | Comfort Zone | Your top 5 songs made up {percent}% of your week. You know what you like. |
| LONGEST_SESSION | Marathon Mode | {duration} in one sitting — that's longer than most movies. |

**Evolving copy for repeat archetypes:**
- 1st occurrence: "Night Owl unlocked — most of your listening happens when normal people are asleep."
- 2nd+: "Night Owl — again. At this point it's just who you are."

**Soft estimation examples for stat pills:**
- Duration → "~X full days of music"
- Play count → "Enough to fill a road trip playlist Nx over"
- Streak → "That's longer than most New Year's resolutions last"

### 2. Visual Tiers — Bronze / Silver / Gold

Each moment type maps to a rarity tier. The tier determines the card's visual treatment.

**Tier assignments:**

| Tier | Criteria | Types |
|------|----------|-------|
| Bronze | Entry-level milestones, common behaviors | SONG_PLAYS_10/25, ARTIST_HOURS_1, STREAK_3/7, TOTAL_HOURS_24, SONGS_DISCOVERED_50, OBSESSION_DAILY, COMFORT_ZONE, DAILY_RITUAL |
| Silver | Meaningful achievements, notable patterns | SONG_PLAYS_50/100, ARTIST_HOURS_5/10, STREAK_14/30, TOTAL_HOURS_100, SONGS_DISCOVERED_100/250, all archetypes, FAST_OBSESSION, QUICK_OBSESSION, DISCOVERY_WEEK, NIGHT_BINGE, BREAKUP_CANDIDATE |
| Gold | Genuinely impressive, flex-worthy | SONG_PLAYS_250/500, ARTIST_HOURS_24, STREAK_100, TOTAL_HOURS_500/1000, SONGS_DISCOVERED_500, MARATHON_WEEK, LONGEST_SESSION, SLOW_BURN, RESURRECTION |

**Visual treatment:**

- **Bronze** — Current card style. Clean dark background, standard scrim, white text. Subtle accent color from album art palette.
- **Silver** — Subtle shimmer/gradient border (1dp) using album palette dominant color. Stronger radial glow (alpha 0.4 vs 0.25). Stat pills use palette accent color.
- **Gold** — Animated gradient border shifting between warm gold tones. Subtle noise/grain texture overlay. Title text uses gradient fill (white to gold). "RARE" label in top-right corner. Share card gets extra flair.

**Implementation:** `MomentTier` enum with `tierFor(type: String): MomentTier` mapping function. `MomentCard` and `MomentShareCard` accept tier and adjust visuals.

### 3. Self-Comparative Context — Personal Records

Make moments feel like genuine self-discovery by comparing against the user's own history.

**Personal best flags:**
- When a moment surpasses the user's own records, flag it with `isPersonalBest = true`
- Examples: "Fastest obsession ever — 20 plays in just 4 days", "Your longest streak by far — previous record was 14 days", "New #1 most-played song, dethroning {previous}"
- Query existing moments of same type during detection to compare

**First-time markers:**
- First moment of each type gets special copy
- Tracked via `copyVariant` counter (how many times this type has triggered)

**Personal best + tier interaction:**
- A personal best bumps a moment up one visual tier (bronze → silver, silver → gold)
- Even a "10 plays" moment can look special if it's the user's first-ever song milestone

## Data Model Changes

Add 3 fields to `Moment` entity:

```kotlin
tier: String          // "BRONZE", "SILVER", "GOLD" — computed at detection time
isPersonalBest: Boolean  // true if surpasses user's own record for this type category
copyVariant: Int      // count of previous moments of this type, for evolving copy
```

**Room migration:** Add columns with defaults (`tier = 'BRONZE'`, `isPersonalBest = 0`, `copyVariant = 0`). Backfill existing moments with computed tiers.

## New Components

### `MomentCopywriter.kt`
Single responsibility: given moment type, tier, entity name, stats, and copy variant, produce title + description + reframed stat pills. Returns `MomentCopy(title, description, statLines)`.

### `MomentTier.kt`
Enum (BRONZE, SILVER, GOLD) with `tierFor(type)` mapping and visual properties (border style, glow alpha, text style).

### Visual composables
- `GoldCardBorder` — animated gradient border
- `SilverCardBorder` — shimmer gradient border
- Gold title gradient via `Brush.linearGradient`
- Tier badge pill composable

## Detection Flow Changes

In `MomentDetector.kt`, after building a moment candidate:
1. Query `momentDao.countByType(type)` → `copyVariant`
2. Query previous best for type category → determine `isPersonalBest`
3. Call `MomentTier.tierFor(type)`, bump up if personal best
4. Call `MomentCopywriter.generate(...)` for final title/description/stats
5. Persist fully-formed moment

## What Stays the Same

- 48 moment types and detection logic — unchanged
- Share flow (bottom sheet → render → share intent)
- Home screen MomentsStrip layout
- AllMomentsScreen structure
- Idempotency via (type, entityKey) uniqueness
