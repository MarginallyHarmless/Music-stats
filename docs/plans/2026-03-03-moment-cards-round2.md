# Moment Cards Round 2: Main Character Energy & The Marathon

## 1. Main Character Energy

A "flex" moment triggered when today becomes one of the user's top 3 listening days ever.

**Type:** `BEHAVIORAL_MAIN_CHARACTER`
**Entity key:** `mce_{date}` (e.g. `mce_2026-03-03`)

**Detection:**
- Calculate today's total listening time (sum of `durationMs` for completed events since start of day)
- Get all daily listening totals across history, sorted descending
- If fewer than 30 distinct listening days exist, skip (avoid early false positives)
- If today's total ranks in the top 3, trigger

**Copy:**
- Title: "Main Character Energy"
- Description: "{todayDuration} of music today. That's your #{rank} biggest listening day ever."
- Stat lines: `["#{rank} all-time", "{todayDuration} today", "{songCount} songs"]`

## 2. The Marathon

A "flex" moment for single-artist deep dives without switching to another artist.

**Type:** `BEHAVIORAL_ARTIST_MARATHON`
**Entity key:** `marathon_{artistName}_{date}`

**Detection:**
- Scan today's completed listening events ordered by `startedAt`
- Group consecutive events by artist (a different artist breaks the streak)
- For each consecutive group, sum `durationMs`
- If any group totals 2+ hours (7,200,000ms), trigger
- Only fire once per artist per day

**Copy:**
- Title: "The Marathon"
- Description: "{duration} of nothing but **{artistName}**. No breaks, no distractions. Just dedication."
- Stat lines: `["{duration} straight", "{songCount} songs", "no interruptions"]`

## Implementation Notes

- Both use existing DAO queries (daily listening, events ordered by time)
- Main Character Energy needs a new suspend query: daily totals across all history (group events by date, sum duration)
- The Marathon reuses `getOrderedSongArtistEventsSuspend` or similar event-ordered queries
- Both are day-scoped detections (run during daily detection cycle)
- No schema changes needed
