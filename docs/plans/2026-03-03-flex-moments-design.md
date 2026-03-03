# Flex Moments — Shareable Stat Cards (Round 4)

6 new moment types designed for screenshots and sharing. Each surfaces a stat that makes users want to post it.

## 1. Top 1% (Biggest Month Ever)

**Type:** `FLEX_BIGGEST_MONTH`
**Entity key:** `bigmonth_{monthKey}`

**Detection:**
- New query: `getMonthlyListeningTotalsSuspend(since)` returning `List<MonthlyListening(monthKey: String, totalMs: Long)>`
- Group listening by calendar month, compare current month to all previous months
- Trigger when current month's total exceeds all previous months
- Require 3+ months of history to avoid early false positives

**Copy:**
- Title: "Top 1%"
- Description: "{duration} this month. That's more music than any month before. New personal record."
- Stat lines: `["{duration} this month", "previous best: {prevBest}", "{songCount} songs"]`

## 2. The Loop (15+ plays in one day)

**Type:** `FLEX_LOOP`
**Entity key:** `loop_{songId}_{date}`

**Detection:**
- Reuse `getSongsPlayedOnDay(todayStart, todayEnd)` (existing query)
- If any song has 15+ plays today, trigger
- Calculate interval: hours elapsed since first event today / play count

**Copy:**
- Title: "The Loop"
- Description: "You played **{song}** {plays} times today. That's roughly once every {interval}."
- Stat lines: `["{plays} plays today", "~every {interval}", "{allTimePlays} all-time"]`

**Overlap note:** OBSESSION_DAILY triggers at 5+ plays with generic copy. The Loop triggers at 15+ with the "once every X minutes" angle that's designed for sharing.

## 3. The Collector (unique song milestones)

**Type:** `FLEX_COLLECTOR_1000`, `FLEX_COLLECTOR_2000`, `FLEX_COLLECTOR_5000`
**Entity key:** `collector_{threshold}`

**Detection:**
- Reuse `getUniqueSongCountSuspend()` (existing query)
- Check against thresholds [1000, 2000, 5000]
- Same milestone pattern as SONGS_DISCOVERED but at much higher tiers

**Copy:**
- Title (1000): "The Collector"
- Title (2000): "Music Hoarder"
- Title (5000): "Living Archive"
- Description (1000): "1,000 unique songs. You could run a radio station."
- Description (2000): "2,000 songs. You don't listen to music — you curate it."
- Description (5000): "5,000 songs. That's not a library, that's an institution."
- Stat lines: `["{count} songs", "from {artistCount} artists", "{totalHours}h of music"]`

## 4. Speed Run (50 plays in 7 days)

**Type:** `FLEX_SPEED_RUN`
**Entity key:** `speedrun_{songId}`

**Detection:**
- Get songs with 50+ plays via `getSongsWithMinPlays(50)` (existing query)
- New query: `getNthPlayTimestamp(songId, n)` — returns startedAt of the Nth completed play: `SELECT startedAt FROM listening_events WHERE songId = :songId AND completed = 1 ORDER BY startedAt ASC LIMIT 1 OFFSET :offset`
- Calculate `daysTaken = (50thPlayTimestamp - firstHeardAt) / 86400000`
- Trigger if daysTaken <= 7

**Copy:**
- Title: "Speed Run"
- Description: "50 plays of **{song}** in {days} days. Most relationships don't move that fast."
- Stat lines: `["{days} days to 50 plays", "#{rank} all-time", "discovered {daysAgo}d ago"]`

**Overlap note:** FAST_OBSESSION triggers at 20 plays in 14 days. Speed Run is 2.5x harder (50 plays in half the time) and framed as a competitive achievement rather than just "instant classic."

## 5. Power Hour (15+ songs in 60 minutes)

**Type:** `FLEX_POWER_HOUR`
**Entity key:** `phour_{date}`

**Detection:**
- Reuse `getOrderedSongArtistEventsSuspend(todayStart)` (existing query)
- Sliding window: for each event, count completed events with `startedAt` within the next 60 minutes
- If any window has 15+ events, trigger
- Record the peak hour for the stat line

**Copy:**
- Title: "Power Hour"
- Description: "{count} songs in a single hour. That's one every {seconds} seconds."
- Stat lines: `["{count} songs", "~every {seconds}s", "{peakHour}"]`

## 6. After Hours (3h+ listening midnight-6am)

**Type:** `FLEX_AFTER_HOURS`
**Entity key:** `afterhrs_{date}`

**Detection:**
- New query: `getAfterHoursListeningByDay(since)` — sum durationMs for completed events in hours 0-5 (midnight to 6am), grouped by calendar day
- Trigger when any day has 3+ hours (10,800,000ms)
- Estimate song count from duration / 210,000ms (avg 3.5min song)

**Copy:**
- Title: "After Hours"
- Description: "{duration} of music between midnight and 6am. Your neighbors have opinions."
- Stat lines: `["{duration} after midnight", "~{songCount} songs", "{peakHour} peak"]`

**Overlap note:** Night Binge covers hours 0-3 at 2h threshold. After Hours covers 0-5 at 3h threshold. The wider window and higher bar mean they rarely co-trigger, and when they do the After Hours framing is distinct (flex vs observation).

## New DAO Queries Needed

1. `getMonthlyListeningTotalsSuspend(since: Long): List<MonthlyListening>` — for Top 1%
2. `getNthPlayTimestamp(songId: Long, offset: Int): Long?` — for Speed Run
3. `getAfterHoursListeningByDay(since: Long): List<DayNightListening>` — for After Hours (reuses existing DayNightListening data class, hours 0-5)

## Existing Queries Reused

- `getDailyListeningTotalsSuspend()` — potential stat line support
- `getSongsPlayedOnDay()` — The Loop
- `getUniqueSongCountSuspend()` — The Collector
- `getSongsWithMinPlays()` — Speed Run
- `getOrderedSongArtistEventsSuspend()` — Power Hour
- `getSongRankByPlayCountSuspend()` — Speed Run stat line
- `getUniqueArtistCountSuspend()` — Collector stat line
- `getTotalListeningTimeMsSuspend()` — Collector stat line
- `getUniqueSongCountInPeriodSuspend()` — Top 1% stat line

## Preview Entries

IDs -119 through -127 (3 for Collector tiers, 1 each for the other 5). Total preview count: 64 + 8 = 72.
