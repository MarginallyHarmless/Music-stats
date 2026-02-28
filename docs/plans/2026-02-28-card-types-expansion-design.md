# Moment Card Types Expansion

**Date:** 2026-02-28

## Problem

The current 38 moment types are weighted toward milestones (play counts, hour thresholds) and lack coverage for behavioral patterns and listening personality archetypes. Additionally, many existing cards show no statline chips, and the single `statLine: String?` field cannot support multiple chips.

## Goals

- Add 10 new moment types (4 archetypes + 6 behavioral)
- Upgrade the `Moment` schema to support multiple stat chips per card
- Enrich all existing cards with relevant statline chips

---

## Section 1: Schema Change

### `Moment.kt`

Replace `statLine: String?` with `statLines: List<String>`. Empty list = no chips.

Requires a Room `TypeConverter` for `List<String>` — stored as a JSON array string via kotlinx-serialization (already in the project stack).

**Migration:** Rename `stat_line` column to `stat_lines`. Convert existing non-null values to single-element JSON arrays (`["8h 24m total"]`). Set `[]` for null rows.

### `MomentCard.kt`

Where the single pill is rendered, iterate `moment.statLines` and render one pill per entry in a `Row` with a small gap (4dp) between chips. Same style: frosted white pill (`Color.White.copy(alpha = 0.15f)`), SemiBold labelSmall.

---

## Section 2: New Card Types

### New Archetypes

Detected monthly. Entity key: `yyyy-MM`. Window: last 30 days unless noted.

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `ARCHETYPE_WEEKEND_WARRIOR` | Weekend (Sat+Sun) listening > 60% of weekly avg over last 4 weeks | `"Weekend Warrior"` | `"Most of your listening happens on weekends"` | `["{pct}% on weekends", "{hours}h on weekends/week"]` | Dark placeholder |
| `ARCHETYPE_WIDE_TASTE` | Top artist < 15% of last-30-day listening AND ≥ 15 unique artists | `"Wide Taste"` | `"No single artist dominates your listening"` | `["{unique_artists} artists this month", "top artist: {pct}%"]` | Dark placeholder |
| `ARCHETYPE_REPEAT_OFFENDER` | Top 3 songs > 40% of plays in last 30 days | `"Repeat Offender"` | `"You found your songs. You're not letting go."` | `["top 3 songs: {pct}% of plays", "{top_song} × {count}"]` | Album art of most-played song |
| `ARCHETYPE_ALBUM_LISTENER` | ≥ 3 sessions in last 30 days with 5+ consecutive songs from same artist | `"Album Listener"` | `"You don't shuffle. You commit."` | `["{count} album runs this month", "avg {avg} songs per run"]` | Album art of most-album-run artist |

### New Behavioral

| Type | Trigger | Title | Description | StatLines | Entity Key | Visual |
|------|---------|-------|-------------|-----------|------------|--------|
| `RESURRECTION` | Song unplayed for 30+ days gets 5+ plays in one calendar day | `"It's back"` | `"{title} went quiet for {days} days. Today it's all you're playing."` | `["{days} days away", "{count} plays today"]` | `{songId}:{yyyy-MM-dd}` | Album art |
| `NIGHT_BINGE` | 2+ hours of listening between midnight and 4am in one night | `"Night binge"` | `"You listened for {duration} after midnight"` | `["{duration} after midnight", "{song_count} songs"]` | `{yyyy-MM-dd}` | Dark placeholder |
| `COMFORT_ZONE` | Top 5 songs this week account for ≥ 80% of weekly plays | `"Comfort zone"` | `"Your top 5 songs made up {pct}% of your listening this week"` | `["{pct}% from 5 songs", "{total_plays} plays this week"]` | `W{yyyy-ww}` | Album art of most-played song |
| `REDISCOVERY` | Artist unplayed for 60+ days becomes top artist this week with ≥ 5 plays | `"You're back"` | `"You hadn't played {artist} in {days} days. Welcome back."` | `["{days} days away", "{plays} plays this week"]` | `{artist}:W{yyyy-ww}` | Artist image |
| `SLOW_BURN` | Song first heard 60+ days ago with < 5 lifetime plays before this week, now gets 5+ plays this week | `"Slow burn"` | `"{title} has been in your library for {days} days. It just clicked."` | `["{days} days to click", "{total_plays} plays now"]` | `{songId}:W{yyyy-ww}` | Album art |
| `MARATHON_WEEK` | Total listening this week beats all-time personal weekly record | `"Marathon week"` | `"New record: {duration} this week"` | `["{duration} this week", "{song_count} songs · {artist_count} artists"]` | `W{yyyy-ww}` | Dark placeholder |

---

## Section 3: Statline Enrichment for Existing Cards

All existing `statLine: String?` values migrate to `statLines: List<String>`.

### Song Play Milestones
`["{duration} total", "#{rank} all-time"]`

### Artist Hour Milestones
`["{play_count} plays", "{unique_songs} songs heard"]`

### Streak Milestones
`["avg {avg_mins}min/day", "{unique_songs} songs this streak"]`

### Total Hour Milestones
`["{unique_songs} unique songs", "{unique_artists} artists"]`

### Discovery Milestones
`["from {unique_artists} artists", "{total_hours}h of music"]`

### Archetypes

| Type | StatLines |
|------|-----------|
| `ARCHETYPE_NIGHT_OWL` | `["{pct}% of your listening", "peak: {peak_hour}"]` |
| `ARCHETYPE_MORNING_LISTENER` | `["{pct}% of your listening", "avg start: {hour}am"]` |
| `ARCHETYPE_COMMUTE_LISTENER` | `["{pct}% during commute hours", "{sessions} commute sessions"]` |
| `ARCHETYPE_COMPLETIONIST` | `["{pct}% skip rate", "{total_plays} plays"]` |
| `ARCHETYPE_CERTIFIED_SKIPPER` | `["{pct}% skip rate", "{total_skips} skips"]` |
| `ARCHETYPE_DEEP_CUT_DIGGER` | `["{play_count} plays", "{hours}h total"]` |
| `ARCHETYPE_LOYAL_FAN` | `["{pct}% of listening", "{play_count} plays"]` |
| `ARCHETYPE_EXPLORER` | `["{count} new artists", "{new_songs} new songs"]` |

### Behavioral

| Type | StatLines |
|------|-----------|
| `OBSESSION_DAILY` | `["{count} plays today", "{total_plays} all-time"]` |
| `DAILY_RITUAL` | `["{total_plays} all-time plays", "{total_duration} total"]` |
| `BREAKUP_CANDIDATE` | `["{count} skips this week", "{plays} plays this week"]` |
| `FAST_OBSESSION` | `["{count} plays", "{days} days · #{rank} all-time"]` |
| `LONGEST_SESSION` | `["{duration}", "{song_count} songs"]` |
| `QUICK_OBSESSION` | `["#{rank} all-time", "{days} days since discovered"]` |
| `DISCOVERY_WEEK` | `["{count} new artists", "{new_songs} new songs"]` |

---

## Files Changed

| File | Change |
|------|--------|
| `data/model/Moment.kt` | Replace `statLine: String?` with `statLines: List<String>` |
| `data/MusicStatsDatabase.kt` | Add migration version, `List<String>` TypeConverter |
| `ui/components/MomentCard.kt` | Render `Row` of pills from `statLines` |
| `service/MomentDetector.kt` | Add 10 new type detectors; update all existing statLine → statLines |
| `docs/moments/card-types.md` | Add new types, update all statLine columns |
