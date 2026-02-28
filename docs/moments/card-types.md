# Moment Card Types Reference

**Source of truth for all moment types: trigger conditions, copy, stat lines, and visual treatment.**

- Detection logic: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`
- Card rendering: `app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt`
- Detail sheet: `app/src/main/java/com/musicstats/app/ui/moments/MomentDetailBottomSheet.kt`
- Share card: `app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt`

Total types: **48** across **7 categories**
_(Song Play: 6 · Artist Hours: 4 · Streaks: 5 · Total Hours: 4 · Discovery: 4 · Archetypes: 12 · Behavioral: 13 = 48)_

---

## 1. Song Play Milestones (6 types)

Triggered when a song's all-time play count crosses a threshold.
Thresholds: `10 / 25 / 50 / 100 / 250 / 500`

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `SONG_PLAYS_10` | Song played ≥ 10 times | `"10 plays"` | `"You've played {title} 10 times"` | `"{duration} total", "#{rank} all-time"` | Album art background |
| `SONG_PLAYS_25` | Song played ≥ 25 times | `"25 plays"` | `"You've played {title} 25 times"` | `"{duration} total", "#{rank} all-time"` | Album art background |
| `SONG_PLAYS_50` | Song played ≥ 50 times | `"50 plays"` | `"You've played {title} 50 times"` | `"{duration} total", "#{rank} all-time"` | Album art background |
| `SONG_PLAYS_100` | Song played ≥ 100 times | `"100 plays"` | `"You've played {title} 100 times"` | `"{duration} total", "#{rank} all-time"` | Album art background |
| `SONG_PLAYS_250` | Song played ≥ 250 times | `"250 plays"` | `"You've played {title} 250 times"` | `"{duration} total", "#{rank} all-time"` | Album art background |
| `SONG_PLAYS_500` | Song played ≥ 500 times | `"500 plays"` | `"You've played {title} 500 times"` | `"{duration} total", "#{rank} all-time"` | Album art background |

**Entity key:** `{songId}:{threshold}`
**StatLine format:** `formatDuration(totalDurationMs) + " total"` (e.g. `"8h 24m total"`)

---

## 2. Artist Hour Milestones (4 types)

Triggered when cumulative listening time for an artist crosses a threshold.
Thresholds: `1 / 5 / 10 / 24` hours

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `ARTIST_HOURS_1` | Artist listened to ≥ 1 hour total | `"1 hour of {artist}"` | `"You've spent 1 hour listening to {artist}"` | `"{play_count} plays", "{unique_songs} songs heard"` | Artist image background |
| `ARTIST_HOURS_5` | Artist listened to ≥ 5 hours total | `"5 hours of {artist}"` | `"You've spent 5 hours listening to {artist}"` | `"{play_count} plays", "{unique_songs} songs heard"` | Artist image background |
| `ARTIST_HOURS_10` | Artist listened to ≥ 10 hours total | `"10 hours of {artist}"` | `"You've spent 10 hours listening to {artist}"` | `"{play_count} plays", "{unique_songs} songs heard"` | Artist image background |
| `ARTIST_HOURS_24` | Artist listened to ≥ 24 hours total | `"24 hours of {artist}"` | `"You've spent 24 hours listening to {artist}"` | `"{play_count} plays", "{unique_songs} songs heard"` | Artist image background |

**Entity key:** `{artist}:{hours}`
**Note:** Singular "hour" only for the 1-hour threshold; plural "hours" for all others.

---

## 3. Streak Milestones (5 types)

Triggered when the current consecutive-day listening streak reaches a threshold.
Thresholds: `3 / 7 / 14 / 30 / 100` days

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `STREAK_3` | 3 consecutive days with listening | `"3-day streak"` | `"3 days in a row — you're on fire"` | `"avg {avg_mins}min/day", "{unique_songs} songs this streak"` | Dark placeholder bg (radial gradient accent) |
| `STREAK_7` | 7 consecutive days with listening | `"7-day streak"` | `"7 days in a row — you're on fire"` | `"avg {avg_mins}min/day", "{unique_songs} songs this streak"` | Dark placeholder bg |
| `STREAK_14` | 14 consecutive days with listening | `"14-day streak"` | `"14 days in a row — you're on fire"` | `"avg {avg_mins}min/day", "{unique_songs} songs this streak"` | Dark placeholder bg |
| `STREAK_30` | 30 consecutive days with listening | `"30-day streak"` | `"30 days in a row — you're on fire"` | `"avg {avg_mins}min/day", "{unique_songs} songs this streak"` | Dark placeholder bg |
| `STREAK_100` | 100 consecutive days with listening | `"100-day streak"` | `"100 days in a row — you're on fire"` | `"avg {avg_mins}min/day", "{unique_songs} songs this streak"` | Dark placeholder bg |

**Entity key:** `{threshold}`
**Streak logic:** Walks backwards day-by-day from today; stops on first day with 0 plays.

---

## 4. Total Hour Milestones (4 types)

Triggered when cumulative all-time listening time crosses a threshold.
Thresholds: `24 / 100 / 500 / 1000` hours

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `TOTAL_HOURS_24` | Total listening ≥ 24h | `"24 hours"` | `"You've listened to 24h of music in total"` | `"{unique_songs} unique songs", "{unique_artists} artists"` | Dark placeholder bg |
| `TOTAL_HOURS_100` | Total listening ≥ 100h | `"100 hours"` | `"You've listened to 100h of music in total"` | `"{unique_songs} unique songs", "{unique_artists} artists"` | Dark placeholder bg |
| `TOTAL_HOURS_500` | Total listening ≥ 500h | `"500 hours"` | `"You've listened to 500h of music in total"` | `"{unique_songs} unique songs", "{unique_artists} artists"` | Dark placeholder bg |
| `TOTAL_HOURS_1000` | Total listening ≥ 1000h | `"1000 hours"` | `"You've listened to 1000h of music in total"` | `"{unique_songs} unique songs", "{unique_artists} artists"` | Dark placeholder bg |

**Entity key:** `{hours}`

---

## 5. Discovery Milestones (4 types)

Triggered when the number of unique songs ever heard crosses a threshold.
Thresholds: `50 / 100 / 250 / 500` unique songs

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `SONGS_DISCOVERED_50` | ≥ 50 unique songs heard | `"50 songs"` | `"You've discovered 50 unique songs"` | `"from {unique_artists} artists", "{total_hours}h of music"` | Dark placeholder bg |
| `SONGS_DISCOVERED_100` | ≥ 100 unique songs heard | `"100 songs"` | `"You've discovered 100 unique songs"` | `"from {unique_artists} artists", "{total_hours}h of music"` | Dark placeholder bg |
| `SONGS_DISCOVERED_250` | ≥ 250 unique songs heard | `"250 songs"` | `"You've discovered 250 unique songs"` | `"from {unique_artists} artists", "{total_hours}h of music"` | Dark placeholder bg |
| `SONGS_DISCOVERED_500` | ≥ 500 unique songs heard | `"500 songs"` | `"You've discovered 500 unique songs"` | `"from {unique_artists} artists", "{total_hours}h of music"` | Dark placeholder bg |

**Entity key:** `{threshold}`

---

## 6. Archetypes (12 types)

Detected monthly; idempotent by `yyyy-MM` entity key (fires at most once per calendar month per type).
Window: last 30 days of listening data.

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `ARCHETYPE_NIGHT_OWL` | 10pm–3am > 50% of listening time | `"Night Owl"` | `"You do most of your listening after 10pm"` | `"{pct}% of your listening", "peak: {peak_hour}"` | Dark placeholder bg |
| `ARCHETYPE_MORNING_LISTENER` | 5am–8am > 50% of listening time | `"Morning Listener"` | `"You do most of your listening before 9am"` | `"{pct}% of your listening", "peak: {peak_hour}am"` | Dark placeholder bg |
| `ARCHETYPE_COMMUTE_LISTENER` | (7–8am + 5–6pm) > 30% of listening time | `"Commute Listener"` | `"Your listening peaks at 7–9am and 5–7pm"` | `"{pct}% during commute hours", "{days} days this month"` | Dark placeholder bg |
| `ARCHETYPE_COMPLETIONIST` | Skip rate < 5% | `"Completionist"` | `"You skip less than 5% of songs — truly dedicated"` | `"{pct}% skip rate", "{total_plays} plays"` | Dark placeholder bg |
| `ARCHETYPE_CERTIFIED_SKIPPER` | Skip rate > 40% | `"Certified Skipper"` | `"You skip more than 40% of songs. Nothing is good enough."` | `"{pct}% skip rate", "{total_skips} skips"` | Dark placeholder bg |
| `ARCHETYPE_DEEP_CUT_DIGGER` | Any song with ≥ 50 plays | `"Deep Cut Digger"` | `"You've listened to {song} over 50 times"` | `"{play_count} plays", "{hours}h total"` | Album art of top deep-cut song |
| `ARCHETYPE_LOYAL_FAN` | Top artist > 50% of all-time listening | `"Loyal Fan"` | `"Over 50% of your listening is {artist}"` | `"{pct}% of listening", "{play_count} plays"` | Artist image background |
| `ARCHETYPE_EXPLORER` | ≥ 5 new artists discovered in past 7 days | `"Explorer"` | `"You discovered {count} new artists this week"` | `"{count} new artists", "{new_songs} new songs"` | Dark placeholder bg |
| `ARCHETYPE_WEEKEND_WARRIOR` | Weekend listening > 60% of weekly avg over last 4 weeks | `"Weekend Warrior"` | `"Most of your listening happens on weekends"` | `"{pct}% on weekends", "{hours}h on weekends/week"` | Dark placeholder bg |
| `ARCHETYPE_WIDE_TASTE` | Top artist < 15% of last-30-day listening AND ≥ 15 unique artists | `"Wide Taste"` | `"No single artist dominates your listening"` | `"{unique_artists} artists this month", "top artist: {pct}%"` | Dark placeholder bg |
| `ARCHETYPE_REPEAT_OFFENDER` | Top 3 songs > 40% of plays in last 30 days | `"Repeat Offender"` | `"You found your songs. You're not letting go."` | `"top 3 songs: {pct}% of plays", "{top_song} × {count}"` | Album art of most-played song |
| `ARCHETYPE_ALBUM_LISTENER` | ≥ 3 sessions in last 30 days with 5+ consecutive songs from same artist | `"Album Listener"` | `"You don't shuffle. You commit."` | `"{count} album runs this month", "avg {avg} songs per run"` | Album art of most-album-run artist |

**Entity key:** `yyyy-MM` (e.g. `2026-02`)
**Skip rate formula:** `totalSkips / (totalPlays + totalSkips)` — all-time, not windowed
**Night Owl hours:** 22, 23, 0, 1, 2, 3
**Morning hours:** 5, 6, 7, 8
**Commute hours:** AM = 7–8, PM = 17–18 (hours as integers)

---

## 7. Behavioral / Daily-Weekly (13 types)

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `OBSESSION_DAILY` | Song played ≥ 5× in today's calendar day | `"{count}x in one day"` | `"You played {title} {count} times today. Are you okay?"` | `"{count} plays today", "{total_plays} all-time"` | Album art background |
| `DAILY_RITUAL` | Song played on all 7 of the last 7 consecutive days | `"Daily ritual"` | `"You've listened to {title} every day for 7 days"` | `"{total_plays} all-time plays", "{total_duration} total"` | Album art background |
| `BREAKUP_CANDIDATE` | Artist skipped ≥ 10× in past 7 days | `"Maybe break up?"` | `"You've skipped {artist} {count} times this week"` | `"{count} skips this week", "{plays} plays this week"` | Artist image background |
| `FAST_OBSESSION` | Song has ≥ 20 plays AND age ≤ 30 days | `"{count} plays in {days} days"` | `"{title} came into your life {days} days ago. You've played it {count} times."` | `"{count} plays", "{days} days · #{rank} all-time"` | Album art background |
| `LONGEST_SESSION` | Single listening session ≥ 1 hour (triggers each new personal best) | `"New record: {duration}"` | `"New personal best: {duration} in one sitting"` | `"{duration}", "new personal best"` | Dark placeholder bg |
| `QUICK_OBSESSION` | Song is in all-time top-5 AND first heard within past 7 days | `"Fast obsession"` | `"You discovered {title} {days} days ago. It's already in your top 5."` | `"#{rank} all-time", "{days} days since discovered"` | Album art background |
| `DISCOVERY_WEEK` | ≥ 8 new artists discovered in past 7 days | `"{count} new artists"` | `"You discovered {count} new artists this week"` | `"{count} new artists", "{new_songs} new songs"` | Dark placeholder bg |
| `RESURRECTION` | Song unplayed for 30+ days gets 5+ plays in one calendar day | `"It's back"` | `"{title} went quiet for {days} days. Today it's all you're playing."` | `"{days} days away", "{count} plays today"` | Album art |
| `NIGHT_BINGE` | 2+ hours of listening midnight–4am in one night | `"Night binge"` | `"You listened for {duration} after midnight"` | `"{duration} after midnight", "{song_count} songs"` | Dark placeholder bg |
| `COMFORT_ZONE` | Top 5 songs this week ≥ 80% of weekly plays | `"Comfort zone"` | `"Your top 5 songs made up {pct}% of your listening this week"` | `"{pct}% from 5 songs", "{total_plays} plays this week"` | Album art of most-played song |
| `REDISCOVERY` | Artist unplayed for 60+ days becomes top artist this week with ≥ 5 plays | `"You're back"` | `"You hadn't played {artist} in {days} days. Welcome back."` | `"{days} days away", "{plays} plays this week"` | Artist image |
| `SLOW_BURN` | Song first heard 60+ days ago with < 5 lifetime plays before this week, now gets 5+ plays this week | `"Slow burn"` | `"{title} has been in your library for {days} days. It just clicked."` | `"{days} days to click", "{total_plays} plays now"` | Album art |
| `MARATHON_WEEK` | Total listening this week beats all-time personal weekly record | `"Marathon week"` | `"New record: {duration} this week"` | `"{duration} this week", "{song_count} songs · {artist_count} artists"` | Dark placeholder bg |

**Entity keys:**
- `OBSESSION_DAILY`: `{songId}:{yyyy-MM-dd}` (one per song per day)
- `DAILY_RITUAL`: `{songId}:{yyyy-MM-dd}` (one per song per detection date)
- `BREAKUP_CANDIDATE`: `{artist}:W{yyyy-ww}` (one per artist per week)
- `FAST_OBSESSION`: `{songId}` (fires once, on first qualifying detection)
- `LONGEST_SESSION`: `{longestMs}` (new entity key = new record = new moment)
- `QUICK_OBSESSION`: `{songId}` (fires once)
- `DISCOVERY_WEEK`: `W{yyyy-ww}` (one per week)
- `RESURRECTION`: `{songId}:{yyyy-MM-dd}` (one per song per day)
- `NIGHT_BINGE`: `{yyyy-MM-dd}` (one per night)
- `COMFORT_ZONE`: `W{yyyy-ww}` (one per week)
- `REDISCOVERY`: `{artist}:W{yyyy-ww}` (one per artist per week)
- `SLOW_BURN`: `{songId}:W{yyyy-ww}` (one per song per week)
- `MARATHON_WEEK`: `W{yyyy-ww}` (one per week)

---

## Visual Treatment Summary

| Background | Used by |
|-----------|---------|
| **Album art** (`moment.imageUrl` = album art URL) | Song Play Milestones, Obsession Daily, Daily Ritual, Fast Obsession, Quick Obsession, Deep Cut Digger, Resurrection, Comfort Zone (most-played song), Slow Burn, Repeat Offender (most-played song), Album Listener (most-album-run artist) |
| **Artist image** (`moment.imageUrl` = artist image URL) | Artist Hour Milestones, Breakup Candidate, Loyal Fan, Rediscovery |
| **Dark placeholder** (`imageUrl = null` → `Color(0xFF1A1A28)` + radial gradient) | Streaks, Total Hours, Discovery Milestones, most Archetypes, Longest Session, Discovery Week, Night Binge, Marathon Week, Weekend Warrior, Wide Taste |

All cards share the same layout: full-bleed background → vertical scrim → text anchored to bottom (entity name label, bold title, body description, optional stat pill, date + unseen dot).

---

## Notes / Ideas

### Copy observations
- **Streak description is identical across all thresholds.** `"N days in a row — you're on fire"` works fine for 3 days but feels under-scaled for 100 days. Consider escalating: `"3 days in a row — just getting started"` / `"100 days in a row — you're unstoppable"`.
- **Discovery milestones are plain.** `"You've discovered 500 unique songs"` — no personality. Could add a comparison: `"That's a full festival lineup"` or `"{N} songs — most people never get there"`.
- **`QUICK_OBSESSION` title says "Fast obsession"** but the type is `QUICK_OBSESSION`. Inconsistency worth aligning.
- **`ARCHETYPE_EXPLORER` vs `DISCOVERY_WEEK`** are nearly identical (both ≥5/≥8 new artists this week). EXPLORER fires monthly (≥5), DISCOVERY_WEEK fires weekly (≥8). This overlap could confuse users — worth differentiating more clearly in copy.

### Visual gaps
- **No image for Streak, Total Hours, Discovery, Longest Session, Discovery Week** — these get the dark placeholder. These are also the most "global" moments (not tied to a song/artist), so no obvious image exists, but the radial gradient accent is subtle. Could try a text-forward or iconographic design instead.
- **`momentBackgroundDrawable()` always returns null.** Hook exists in `MomentCard.kt` for local drawable backgrounds by type, but nothing is wired up. Could use this for archetype cards that lack images.

### Trigger / logic observations
- **`ARCHETYPE_COMPLETIONIST` and `CERTIFIED_SKIPPER` use all-time skip rates**, not the 30-day window used for other archetypes. A user who skipped a lot years ago but stopped will still trigger COMPLETIONIST slowly.
- **`LONGEST_SESSION` entity key is the duration in ms** — so every personal best creates a new moment, which is correct. But with no upper bound, a user could accumulate many of these.
- **`FAST_OBSESSION` age window is strictly `> 0`** — songs heard today (ageMs = ~0) are excluded. Songs from 1–30 days ago qualify.
- **`BREAKUP_CANDIDATE` could fire on artists the user listened to once** — if they happened to skip the same artist 10 times in a week across different discovery sessions.
