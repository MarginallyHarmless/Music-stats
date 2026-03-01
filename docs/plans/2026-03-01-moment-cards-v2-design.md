# Moment Cards v2: Narrative Moments + Pruning + Share Polish

**Date:** 2026-03-01
**Status:** Approved

## Problem

The current moment system has three issues:
1. Too many low-value moments fire early and dilute the feed
2. Moments are stat-based, not story-based — they don't tell you something surprising
3. Share cards all look the same and aren't worth posting to socials

## Solution

Three changes: prune low-value moments, add 12 narrative moment types that tell stories across multiple data points, and visually refresh the share card layout.

---

## 1. Prune Low-Value Moments

Remove 5 moment types that fire too easily and add noise:

| Type | Reason |
|------|--------|
| `SONG_PLAYS_10` | 10 plays is trivial |
| `SONG_PLAYS_25` | Still too easy for any song you like |
| `ARTIST_HOURS_1` | 1 hour of any artist is not noteworthy |
| `STREAK_3` | 3 consecutive days is barely a pattern |
| `SONGS_DISCOVERED_50` | Low bar for any active listener |

Keep `TOTAL_HOURS_24` (first full day of all-time listening is meaningful for new users).

Remove these from: MomentDetector thresholds, MomentCopywriter templates, AllMomentsViewModel preview moments.

---

## 2. Add 12 Narrative Moments

Narrative moments connect 2-3 data points across time into a story. They need a time arc — a beginning and end — not just a current stat.

### Discovery Arcs

#### NARRATIVE_ORIGIN_STORY
> "You discovered Radiohead through Everything In Its Right Place, 47 days ago. They just became your #3 most-listened artist."

- **Trigger:** Artist enters all-time top 5 by listening time for the first time
- **Entity key:** `{artist}` (once per artist, ever)
- **Data needed:** Artist's first song heard (first ListeningEvent by that artist → song title), artist's current rank by total duration, days since `Artist.firstHeardAt`, unique song count, total hours
- **New DAO queries:** `getFirstSongByArtist(artist)` — first event for an artist joined with song title; `getArtistRankByDuration(artist)` — rank among all artists by total duration
- **Stats:** `{uniqueSongs} songs` | `{totalHours} hours` | `discovered {daysAgo}d ago`
- **Image:** Artist image

#### NARRATIVE_GATEWAY
> "Do I Wanna Know? was your gateway to Arctic Monkeys. But Fluorescent Adolescent became your real favorite — 3x more plays."

- **Trigger:** Artist with 10+ hours total AND most-played song by that artist is NOT the first song heard by that artist
- **Entity key:** `{artist}` (once per artist)
- **Data needed:** First song heard by artist, most-played song by artist, play counts for both
- **New DAO queries:** `getFirstSongByArtist(artist)` (shared with Origin Story); `getMostPlayedSongByArtist(artist)` — top song by play count for an artist
- **Stats:** `first: {firstSong}` | `favorite: {topSong} ({ratio}x more)`
- **Image:** Artist image

#### NARRATIVE_COLLECTION
> "You've heard 23 songs by Arctic Monkeys — from Do I Wanna Know? to the deep cuts."

- **Trigger:** 15+ unique songs completed by a single artist AND 30+ days since `Artist.firstHeardAt`
- **Entity key:** `{artist}` (once per artist)
- **Data needed:** Unique song count for artist (existing: `getUniqueSongCountForArtistSuspend`), first song heard, total hours, days since first heard
- **Stats:** `{songCount} songs` | `{totalHours} hours` | `first heard {daysAgo}d ago`
- **Image:** Artist image

### Obsession Arcs

#### NARRATIVE_TAKEOVER
> "3 weeks ago, Espresso didn't exist in your library. Now it's #4 all-time."

- **Trigger:** Song enters all-time top 10 by play count within 30 days of `Song.firstHeardAt`
- **Entity key:** `{songId}` (once per song)
- **Data needed:** `Song.firstHeardAt`, current rank (existing: `getSongRankByPlayCountSuspend`), play count, days since discovery
- **Stats:** `#{rank} all-time` | `{playCount} plays in {daysAgo} days`
- **Image:** Album art

#### NARRATIVE_SLOW_BUILD
> "Week 1: 2 plays. Week 2: 5. Week 3: 11. Week 4: 19. You didn't binge it — you fell for it gradually."

- **Trigger:** Song with 4 consecutive weeks of strictly increasing play counts, final week has 15+ plays
- **Entity key:** `{songId}:{endWeekKey}` (can recur if pattern repeats later)
- **Data needed:** Weekly play counts per song over last 4 weeks
- **New DAO queries:** `getWeeklyPlayCountsForSong(songId, since)` — play count per ISO week for a song
- **Stats:** `{w1} → {w2} → {w3} → {w4} plays`
- **Image:** Album art

#### NARRATIVE_BINGE_AND_FADE
> "You played Heat Waves 47 times in its first 2 weeks. In the 3 weeks since? Twice. Burned bright, burned fast."

- **Trigger:** Song with 30+ completed plays in first 14 days after `Song.firstHeardAt`, then <5 plays in the following 21 days. Song must be 35+ days old.
- **Entity key:** `{songId}` (once per song)
- **Data needed:** `Song.firstHeardAt`, play count in days 0-14 (existing: `getSongPlayCountSinceSuspend` with range), play count in days 14-35
- **New DAO queries:** `getSongPlayCountBetween(songId, from, until)` — play count in a date range
- **Stats:** `{bingePlays} plays first 2 weeks` | `{fadePlays} plays since`
- **Image:** Album art

### Comeback Arcs

#### NARRATIVE_FULL_CIRCLE
> "You hadn't touched Daft Punk in 73 days. This week: 12 plays. Some things come back around."

- **Trigger:** Artist unplayed for 60+ days, then 10+ completed plays in last 7 days
- **Entity key:** `{artist}:W{yyyy-ww}` (can recur per comeback)
- **Data needed:** Last play before gap (existing: `getArtistLastPlayedBeforeSuspend`), plays this week (existing: `getArtistPlayCountSinceSuspend`), gap duration
- **Stats:** `{gapDays} days away` | `{playsThisWeek} plays this week`
- **Image:** Artist image
- **Note:** Replaces/upgrades existing `REDISCOVERY` moment — same concept, better framing

#### NARRATIVE_ONE_THAT_GOT_AWAY
> "Tyler, The Creator was your most-played in February — 34 plays. This month? Just 2."

- **Trigger:** Artist was in top 3 by play count in any past calendar month (30-day window), now has <5 plays in last 30 days. Must be 60+ days since the peak month to avoid firing during natural cooldowns.
- **Entity key:** `{artist}:{yyyy-MM}` (once per artist per decline detection)
- **Data needed:** Historical monthly play counts per artist, current 30-day play count
- **New DAO queries:** `getArtistMonthlyPlayCounts(artist, since)` — play count per month for an artist; `getTopArtistsByPlayCountInPeriod(from, until, limit)` — top N artists by play count in a window
- **Stats:** `peak: {peakPlays} plays ({peakMonth})` | `now: {currentPlays} plays`
- **Image:** Artist image

### Consistency Arcs

#### NARRATIVE_SOUNDTRACK
> "Blinding Lights has been with you for 6 months. Played on 47 different days, through summer and into fall."

- **Trigger:** Song played on 30+ distinct days AND `Song.firstHeardAt` is 90+ days ago
- **Entity key:** `{songId}` (once per song)
- **Data needed:** Distinct days played (existing: `getDistinctDaysForSong`), `Song.firstHeardAt`, total play count, month span calculation
- **Stats:** `{distinctDays} days` | `{monthSpan} months` | `{playCount} plays`
- **Image:** Album art

### Session Arcs

#### NARRATIVE_RABBIT_HOLE
> "Yesterday you went deep: 14 Radiohead songs back-to-back, 58 minutes without coming up for air."

- **Trigger:** 8+ consecutive completed plays by the same artist in chronological order, with no gap >30 minutes between events and no other artist's events between them
- **Entity key:** `{artist}:{yyyy-MM-dd}` (once per artist per day)
- **Data needed:** Ordered events (existing: `getOrderedSongArtistEventsSuspend`), same session-gap logic as album listener detection
- **Stats:** `{songCount} songs` | `{duration}` | `no interruptions`
- **Image:** Artist image

### Contrast Arcs

#### NARRATIVE_NIGHT_AND_DAY
> "By day: Taylor Swift. After midnight: Bon Iver. Your 2am self has different taste."

- **Trigger:** Top artist during 8am-8pm ≠ top artist during 10pm-4am, each with 5+ hours in their respective time window over last 30 days
- **Entity key:** `{yyyy-MM}` (monthly)
- **Data needed:** Top artist by duration in daytime hours, top artist by duration in nighttime hours
- **New DAO queries:** `getTopArtistInTimeWindow(since, hourStart, hourEnd, limit)` — top artist by duration filtered to specific hours of day
- **Stats:** `day: {dayArtist} ({dayHours}h)` | `night: {nightArtist} ({nightHours}h)`
- **Image:** Dark placeholder with glow

#### NARRATIVE_PARALLEL_LIVES
> "Radiohead and Coldplay — you listen to both, but never in the same session. 30 days, zero overlap."

- **Trigger:** Two artists each with 3+ hours in last 30 days that have never appeared within the same listening session (events within 30-min gap window) in that period
- **Entity key:** `{artist1}:{artist2}:{yyyy-MM}` (monthly, sorted alphabetically)
- **Data needed:** All events in last 30 days grouped into sessions (reuse session-gap logic), then check for co-occurrence
- **New DAO queries:** None needed — uses existing `getOrderedSongArtistEventsSuspend` and session grouping in Kotlin
- **Stats:** `{artist1}: {hours1}h` | `{artist2}: {hours2}h` | `0 shared sessions`
- **Image:** Dark placeholder with glow

---

## 3. Share Card Visual Refresh

Single layout, fully polished. No new layout variants.

### Typography
- Title: larger, bolder weight — should feel editorial, not app-generated
- Description: better line height for readability, especially for narrative moments which have longer copy
- Stat pills: tighter sizing, more confident weight
- Date/branding: more subtle, less visual weight

### Palette-Driven Colors
- Cards with album art use extracted palette colors from Song entity (`paletteDominant`, `paletteVibrant`, `paletteMuted`, etc.)
- Apply palette to: scrim tint color, pill background tint, subtle accent on text
- Cards without art (dark placeholder) continue using `LocalAlbumPalette.accent` for glow

### Composition
- More horizontal and vertical padding throughout
- Title gets breathing room — not crammed against stat pills
- Stat pills row: better spacing, consider wrapping to 2 rows if >3 pills
- Blurred background image: slightly less blur for more texture, slightly more scrim for readability balance
- "vibes" branding: smaller, more integrated

### Narrative Copy Accommodation
- Narrative moments have longer descriptions (2-3 sentences)
- Card should handle this gracefully: slightly smaller title, or auto-adjust text sizes
- Consider a max of 4 lines for description with ellipsis

---

## New DAO Queries Needed

| Query | Used By |
|-------|---------|
| `getFirstSongByArtist(artist): SongPlayStats?` | Origin Story, Gateway |
| `getMostPlayedSongByArtist(artist): SongPlayStats?` | Gateway |
| `getArtistRankByDuration(artist): Int` | Origin Story |
| `getWeeklyPlayCountsForSong(songId, since): List<WeeklyPlays>` | Slow Build |
| `getSongPlayCountBetween(songId, from, until): Int` | Binge and Fade |
| `getArtistMonthlyPlayCounts(artist, since): List<MonthlyPlays>` | One That Got Away |
| `getTopArtistsByPlayCountInPeriod(from, until, limit): List<ArtistPlayStats>` | One That Got Away |
| `getTopArtistInTimeWindow(since, hourStart, hourEnd, limit): List<ArtistPlayStats>` | Night and Day |

---

## Moment Overlap with Existing Types

Two narrative moments overlap with existing behavioral types:

- **NARRATIVE_FULL_CIRCLE** overlaps with **REDISCOVERY** (artist comeback)
- **NARRATIVE_BINGE_AND_FADE** is conceptually inverse of **SLOW_BURN**

Decision: Keep both existing and narrative versions. The narrative versions fire at different thresholds and have different copy. REDISCOVERY fires for any artist unplayed 60+ days with 5+ plays; FULL_CIRCLE requires 10+ plays, creating a higher bar for the narrative version.

---

## Final Moment Count

- Existing (after pruning): 43 types
- New narrative: 12 types
- **Total: 55 moment types**
