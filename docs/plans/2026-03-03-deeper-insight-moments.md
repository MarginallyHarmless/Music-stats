# New Moment Cards

New moment types that surface non-obvious patterns and flex-worthy stats from listening data.

## 1. The Grower

A song the user barely noticed at first that slowly climbed to become heavily played.

**Type:** `BEHAVIORAL_GROWER`
**Entity key:** `song_{songId}`

**Detection:**
- Song has 50+ total plays
- Play rate in the most recent 30 days is at least 3x its play rate in the first 30 days after discovery
- "Play rate" = plays per day in the window

**Copy:**
- Title: "The Grower"
- Description: "You barely noticed **{songName}** at first — {earlyPlays} plays in the first month. Now it's one of your most-played. Some things take time."
- Stat lines: `["{totalPlays} total plays", "First month: {earlyPlays} plays", "Last month: {recentPlays} plays"]`

## 2. Taste Drift

The user's top artists changed dramatically in a short window.

**Type:** `BEHAVIORAL_TASTE_DRIFT`
**Entity key:** `drift_{windowEndEpoch}`

**Detection:**
- Compare top 5 artists by play count in window [-60d, -30d] vs. [-30d, today]
- Trigger when overlap is 0-1 artists between the two windows
- Must have listening data spanning at least 60 days

**Copy:**
- Title: "Taste Drift"
- Description: "Your top artists a month ago? Completely different lineup. Your taste shifted and you probably didn't even notice."
- Stat lines: `["Then: {oldTop1}, {oldTop2}, {oldTop3}", "Now: {newTop1}, {newTop2}, {newTop3}"]`

## 3. Locked In

The inverse of Taste Drift — the user's top artists are remarkably stable.

**Type:** `BEHAVIORAL_LOCKED_IN`
**Entity key:** `locked_{windowEndEpoch}`

**Detection:**
- Same comparison as Taste Drift (top 5 artists across two 30-day windows)
- Trigger when overlap is 4-5 artists
- Must have listening data spanning at least 60 days

**Copy:**
- Title: "Locked In"
- Description: "Your top artists haven't budged in a month. You know exactly what you like."
- Stat lines: `["{overlapCount}/5 top artists unchanged", "Top: {top1}, {top2}, {top3}"]`

## 4. The Replacement

One artist's plays spiked right as another's dropped — an implied substitution.

**Type:** `BEHAVIORAL_REPLACEMENT`
**Entity key:** `replace_{artistAId}_{artistBId}`

**Detection:**
- In a 14-day window, Artist A's plays dropped 50%+ from their prior 14-day average
- In the same window, Artist B's plays rose 50%+ from their prior 14-day average
- Both artists must have 30+ total plays
- Only triggers once per (A, B) pair

**Copy:**
- Title: "The Replacement"
- Description: "**{artistB}** showed up right as **{artistA}** faded out. One door closes, another opens."
- Stat lines: `["{artistA}: {oldPlays} → {newPlays} plays", "{artistB}: {oldPlays} → {newPlays} plays"]`

## 5. Main Character Energy

A "flex" moment triggered when today becomes one of the user's top 3 listening days ever.

**Type:** `BEHAVIORAL_MAIN_CHARACTER`
**Entity key:** `mce_{date}` (e.g. `mce_2026-03-03`)

**Detection:**
- Calculate today's total listening time (sum of durationMs for completed events since start of day)
- Get all daily listening totals across history, sorted descending
- If fewer than 30 distinct listening days exist, skip (avoid early false positives)
- If today's total ranks in the top 3, trigger

**Copy:**
- Title: "Main Character Energy"
- Description: "{todayDuration} of music today. That's your #{rank} biggest listening day ever."
- Stat lines: `["#{rank} all-time", "{todayDuration} today", "{songCount} songs"]`

## 6. The Marathon

A "flex" moment for single-artist deep dives without switching to another artist.

**Type:** `BEHAVIORAL_ARTIST_MARATHON`
**Entity key:** `marathon_{artistName}_{date}`

**Detection:**
- Scan today's completed listening events ordered by startedAt
- Group consecutive events by artist (a different artist breaks the streak)
- For each consecutive group, sum durationMs
- If any group totals 2+ hours (7,200,000ms), trigger
- Only fire once per artist per day

**Copy:**
- Title: "The Marathon"
- Description: "{duration} of nothing but **{artistName}**. No breaks, no distractions. Just dedication."
- Stat lines: `["{duration} straight", "{songCount} songs", "no interruptions"]`

## Background Images

All types use the dark placeholder background (Color 0xFF1A1A28 with radial palette glow) unless a bundled background image is assigned later.

## Implementation Notes

- Add types to `MomentType` constants
- Add detection functions in `MomentDetector.kt`
- Add copy generation in `MomentCopywriter.kt`
- Add preview entries in `AllMomentsViewModel`
- Taste Drift and Locked In share detection logic (compare windows, branch on overlap count)
- The Replacement detection should run after other detections since it compares artist pairs
- Main Character Energy needs a new DAO query for daily totals across all history
- The Marathon reuses event-ordered queries, groups consecutive same-artist events
