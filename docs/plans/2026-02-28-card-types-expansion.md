# Card Types Expansion Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 10 new moment types (4 archetypes + 6 behavioral), migrate the `Moment` schema from a single `statLine: String?` to `statLines: List<String>`, and enrich all existing cards with relevant multi-chip statlines.

**Architecture:** Schema migration (DB v11 → v12) adds a `stat_lines TEXT NOT NULL DEFAULT '[]'` column and clears all moments (re-detected on next run, following the established pattern). `MomentCard.kt` renders a `Row` of pills from `statLines`. `ListeningEventDao` gets new suspend queries. `MomentDetector` gets updated detectors for all existing types plus 10 new private methods.

**Tech Stack:** Kotlin, Room (SQLite), Hilt, Jetpack Compose, kotlinx-serialization JSON (already in project)

**Design doc:** `docs/plans/2026-02-28-card-types-expansion-design.md`

---

### Task 1: Update `card-types.md`

**Files:**
- Modify: `docs/moments/card-types.md`

**Step 1: Update the total count header**

Change the header line from `Total types: **38** across **7 categories**` to `Total types: **48** across **7 categories**`.

**Step 2: Replace all StatLine column values**

Update every row's StatLine column to reflect multi-chip format (comma-separated list notation).

For categories 1–5, update as follows (full table replacements):

**Section 1 — Song Play Milestones:** All 6 rows, StatLine: `"{duration} total", "#{rank} all-time"`

**Section 2 — Artist Hour Milestones:** All 4 rows, StatLine: `"{play_count} plays", "{unique_songs} songs heard"`

**Section 3 — Streak Milestones:** All 5 rows, StatLine: `"avg {avg_mins}min/day", "{unique_songs} songs this streak"`

**Section 4 — Total Hour Milestones:** All 4 rows, StatLine: `"{unique_songs} unique songs", "{unique_artists} artists"`

**Section 5 — Discovery Milestones:** All 4 rows, StatLine: `"from {unique_artists} artists", "{total_hours}h of music"`

**Section 6 — Archetypes:** Update all 8 rows:

| Type | StatLines |
|------|-----------|
| `ARCHETYPE_NIGHT_OWL` | `"{pct}% of your listening", "peak: {peak_hour}"` |
| `ARCHETYPE_MORNING_LISTENER` | `"{pct}% of your listening", "peak: {peak_hour}am"` |
| `ARCHETYPE_COMMUTE_LISTENER` | `"{pct}% during commute hours", "{days} days this month"` |
| `ARCHETYPE_COMPLETIONIST` | `"{pct}% skip rate", "{total_plays} plays"` |
| `ARCHETYPE_CERTIFIED_SKIPPER` | `"{pct}% skip rate", "{total_skips} skips"` |
| `ARCHETYPE_DEEP_CUT_DIGGER` | `"{play_count} plays", "{hours}h total"` |
| `ARCHETYPE_LOYAL_FAN` | `"{pct}% of listening", "{play_count} plays"` |
| `ARCHETYPE_EXPLORER` | `"{count} new artists", "{new_songs} new songs"` |

Then add 4 new archetype rows:

| Type | Trigger | Title | Description | StatLines | Visual |
|------|---------|-------|-------------|-----------|--------|
| `ARCHETYPE_WEEKEND_WARRIOR` | Weekend listening > 60% of weekly avg over last 4 weeks | `"Weekend Warrior"` | `"Most of your listening happens on weekends"` | `"{pct}% on weekends", "{hours}h on weekends/week"` | Dark placeholder |
| `ARCHETYPE_WIDE_TASTE` | Top artist < 15% of last-30-day listening AND ≥ 15 unique artists | `"Wide Taste"` | `"No single artist dominates your listening"` | `"{unique_artists} artists this month", "top artist: {pct}%"` | Dark placeholder |
| `ARCHETYPE_REPEAT_OFFENDER` | Top 3 songs > 40% of plays in last 30 days | `"Repeat Offender"` | `"You found your songs. You're not letting go."` | `"top 3 songs: {pct}% of plays", "{top_song} × {count}"` | Album art of most-played song |
| `ARCHETYPE_ALBUM_LISTENER` | ≥ 3 sessions in last 30 days with 5+ consecutive songs from same artist | `"Album Listener"` | `"You don't shuffle. You commit."` | `"{count} album runs this month", "avg {avg} songs per run"` | Album art of most-album-run artist |

**Section 7 — Behavioral:** Update all 7 existing rows:

| Type | StatLines |
|------|-----------|
| `OBSESSION_DAILY` | `"{count} plays today", "{total_plays} all-time"` |
| `DAILY_RITUAL` | `"{total_plays} all-time plays", "{total_duration} total"` |
| `BREAKUP_CANDIDATE` | `"{count} skips this week", "{plays} plays this week"` |
| `FAST_OBSESSION` | `"{count} plays", "{days} days · #{rank} all-time"` |
| `LONGEST_SESSION` | `"{duration}", "new personal best"` |
| `QUICK_OBSESSION` | `"#{rank} all-time", "{days} days since discovered"` |
| `DISCOVERY_WEEK` | `"{count} new artists", "{new_songs} new songs"` |

Then add 6 new behavioral rows:

| Type | Trigger | Title | Description | StatLines | Entity Key | Visual |
|------|---------|-------|-------------|-----------|------------|--------|
| `RESURRECTION` | Song unplayed for 30+ days gets 5+ plays in one calendar day | `"It's back"` | `"{title} went quiet for {days} days. Today it's all you're playing."` | `"{days} days away", "{count} plays today"` | `{songId}:{yyyy-MM-dd}` | Album art |
| `NIGHT_BINGE` | 2+ hours of listening midnight–4am in one night | `"Night binge"` | `"You listened for {duration} after midnight"` | `"{duration} after midnight", "{song_count} songs"` | `{yyyy-MM-dd}` | Dark placeholder |
| `COMFORT_ZONE` | Top 5 songs this week ≥ 80% of weekly plays | `"Comfort zone"` | `"Your top 5 songs made up {pct}% of your listening this week"` | `"{pct}% from 5 songs", "{total_plays} plays this week"` | `W{yyyy-ww}` | Album art of most-played song |
| `REDISCOVERY` | Artist unplayed for 60+ days becomes top artist this week with ≥ 5 plays | `"You're back"` | `"You hadn't played {artist} in {days} days. Welcome back."` | `"{days} days away", "{plays} plays this week"` | `{artist}:W{yyyy-ww}` | Artist image |
| `SLOW_BURN` | Song first heard 60+ days ago with < 5 lifetime plays before this week, now gets 5+ plays this week | `"Slow burn"` | `"{title} has been in your library for {days} days. It just clicked."` | `"{days} days to click", "{total_plays} plays now"` | `{songId}:W{yyyy-ww}` | Album art |
| `MARATHON_WEEK` | Total listening this week beats all-time personal weekly record | `"Marathon week"` | `"New record: {duration} this week"` | `"{duration} this week", "{song_count} songs · {artist_count} artists"` | `W{yyyy-ww}` | Dark placeholder |

**Step 3: Update Visual Treatment Summary**

Add new types to the visual treatment table under the appropriate rows:
- Album art row: add `Resurrection, Comfort Zone (most-played song), Slow Burn, Repeat Offender (most-played song), Album Listener (most-run artist)`
- Artist image row: add `Rediscovery`
- Dark placeholder row: add `Night Binge, Marathon Week, Weekend Warrior, Wide Taste`

**Step 4: Update total count**

Change `Total types: **38**` → `Total types: **48**` in the header. Update the 7 categories line to note Archetypes now has 12 and Behavioral has 13.

**Step 5: Commit**

```bash
git add docs/moments/card-types.md
git commit -m "docs: update card-types.md — 48 types, multi-chip statlines, 10 new types"
```

---

### Task 2: Schema migration — `statLine` → `statLines`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/model/Moment.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt`
- Create: `app/src/main/java/com/musicstats/app/data/Converters.kt`

**Step 1: Create `Converters.kt`**

```kotlin
package com.musicstats.app.data

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromStringList(list: List<String>): String = Json.encodeToString(list)

    @TypeConverter
    fun toStringList(json: String): List<String> =
        runCatching { Json.decodeFromString<List<String>>(json) }.getOrDefault(emptyList())
}
```

**Step 2: Update `Moment.kt`**

Replace `val statLine: String? = null,` with `val statLines: List<String> = emptyList(),`

Full updated file:

```kotlin
package com.musicstats.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "moments",
    indices = [Index(value = ["type", "entityKey"], unique = true)]
)
data class Moment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val entityKey: String,
    val triggeredAt: Long,
    val seenAt: Long? = null,
    val sharedAt: Long? = null,
    val title: String,
    val description: String,
    val songId: Long? = null,
    val artistId: Long? = null,
    val statLines: List<String> = emptyList(),
    val imageUrl: String? = null,
    val entityName: String? = null
)
```

**Step 3: Update `MusicStatsDatabase.kt`**

- Add `@TypeConverters(Converters::class)` annotation to the class
- Bump version from `11` to `12`
- Add `MIGRATION_11_12`

Changes to apply:

```kotlin
// Change version:
version = 12,

// Add annotation above @Database:
@TypeConverters(Converters::class)

// Add import:
import androidx.room.TypeConverters
import com.musicstats.app.data.Converters

// Add migration in companion object:
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE moments ADD COLUMN statLines TEXT NOT NULL DEFAULT '[]'")
        // Clear all moments — they will be re-detected with proper statLines on next run
        db.execSQL("DELETE FROM moments")
    }
}
```

Also add `MIGRATION_11_12` to the `.addMigrations(...)` call in the DatabaseModule (see `data/di/DatabaseModule.kt`).

**Step 4: Find and update DatabaseModule**

Open `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt`. Find the `.addMigrations(...)` call and add `MusicStatsDatabase.MIGRATION_11_12`.

**Step 5: Build to verify schema compiles**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If Room complains about missing TypeConverter or unresolved column, check that `@TypeConverters(Converters::class)` is on the `@Database` class and the column name in the migration matches exactly (`statLines`).

**Step 6: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/model/Moment.kt
git add app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt
git add app/src/main/java/com/musicstats/app/data/Converters.kt
git add app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt
git commit -m "feat: migrate Moment.statLine → statLines List<String>, DB v12"
```

---

### Task 3: Update `MomentCard.kt` to render multiple pills

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt`

**Step 1: Replace the single-pill block**

Find this block (lines 133–148):

```kotlin
if (moment.statLine != null) {
    Spacer(Modifier.height(6.dp))
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = moment.statLine,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}
```

Replace with:

```kotlin
if (moment.statLines.isNotEmpty()) {
    Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        moment.statLines.forEach { stat ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stat,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
```

Also add `import androidx.compose.foundation.layout.Arrangement` at the top.

**Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Any reference to `moment.statLine` elsewhere (e.g. MomentDetailBottomSheet, MomentShareCard) will now fail to compile — fix those in the same task by replacing `.statLine` with `.statLines.firstOrNull()` for display purposes.

**Step 3: Fix any remaining `statLine` references**

Search for remaining `.statLine` usages:

```bash
grep -r "statLine" app/src/main/java --include="*.kt"
```

For any `moment.statLine` in detail/share screens, replace with:
- `moment.statLines.joinToString(" · ")` for a single-line display, or
- `moment.statLines.firstOrNull()` if only showing one chip

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt
git add app/src/main/java/com/musicstats/app/ui/moments/MomentDetailBottomSheet.kt  # if changed
git add app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt  # if changed
git commit -m "feat: render multiple stat chips in MomentCard"
```

---

### Task 4: Add new DAO queries to `ListeningEventDao.kt`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt`

**Step 1: Add new data classes** (before the `@Dao` interface)

```kotlin
data class SongArtistEvent(
    val songId: Long,
    val artist: String,
    val startedAt: Long,
    val durationMs: Long
)

data class DayNightListening(
    val day: String,
    val nightMs: Long
)

data class WeeklyListening(
    val weekKey: String,
    val totalMs: Long
)
```

**Step 2: Add new suspend query methods** at the end of the `@Dao` interface

```kotlin
// Weekend listening total (Sat=6, Sun=0 in SQLite strftime %w)
@Query("""
    SELECT COALESCE(SUM(durationMs), 0) FROM listening_events
    WHERE completed = 1 AND startedAt >= :since
    AND CAST(strftime('%w', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) IN (0, 6)
""")
suspend fun getWeekendListeningMsSuspend(since: Long): Long

// Unique artist count in a period (for Wide Taste)
@Query("""
    SELECT COUNT(DISTINCT s.artist) FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1 AND e.startedAt >= :since
""")
suspend fun getUniqueArtistCountSinceSuspend(since: Long): Int

// Top N songs by play count in a time window (for Repeat Offender, Comfort Zone)
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
           COUNT(CASE WHEN e.completed = 1 THEN 1 END) AS playCount
    FROM listening_events e JOIN songs s ON e.songId = s.id
    WHERE e.startedAt >= :since AND e.completed = 1
    GROUP BY e.songId
    ORDER BY playCount DESC
    LIMIT :limit
""")
suspend fun getTopSongsInPeriodByPlayCountSuspend(since: Long, limit: Int): List<SongPlayStats>

// Total completed play count in a period (for Repeat Offender, Comfort Zone)
@Query("""
    SELECT COUNT(*) FROM listening_events
    WHERE completed = 1 AND startedAt >= :since
""")
suspend fun getTotalPlayCountInPeriodSuspend(since: Long): Long

// Ordered events for consecutive-run detection (for Album Listener)
@Query("""
    SELECT e.songId, s.artist, e.startedAt, e.durationMs
    FROM listening_events e JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1 AND e.startedAt >= :since
    ORDER BY e.startedAt ASC
""")
suspend fun getOrderedSongArtistEventsSuspend(since: Long): List<SongArtistEvent>

// Last completed play timestamp for a song before a cutoff (for Resurrection)
@Query("""
    SELECT MAX(startedAt) FROM listening_events
    WHERE songId = :songId AND completed = 1 AND startedAt < :before
""")
suspend fun getSongLastPlayedBeforeSuspend(songId: Long, before: Long): Long?

// Night listening totals grouped by calendar day, hours 0–3 (for Night Binge)
@Query("""
    SELECT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') AS day,
           COALESCE(SUM(durationMs), 0) AS nightMs
    FROM listening_events
    WHERE completed = 1 AND startedAt >= :since
    AND CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) < 4
    GROUP BY day
""")
suspend fun getNightListeningByDaySuspend(since: Long): List<DayNightListening>

// Last completed play timestamp for an artist before a cutoff (for Rediscovery)
@Query("""
    SELECT MAX(e.startedAt) FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE s.artist = :artist AND e.completed = 1 AND e.startedAt < :before
""")
suspend fun getArtistLastPlayedBeforeSuspend(artist: String, before: Long): Long?

// Artist play count in a time period (for Rediscovery, Breakup plays chip)
@Query("""
    SELECT COUNT(*) FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE s.artist = :artist AND e.completed = 1 AND e.startedAt >= :since
""")
suspend fun getArtistPlayCountSinceSuspend(artist: String, since: Long): Int

// Song play count before a cutoff (for Slow Burn — plays before this week)
@Query("""
    SELECT COUNT(*) FROM listening_events
    WHERE songId = :songId AND completed = 1 AND startedAt < :before
""")
suspend fun getSongPlayCountBeforeSuspend(songId: Long, before: Long): Int

// Song play count since a timestamp (for Slow Burn — plays this week)
@Query("""
    SELECT COUNT(*) FROM listening_events
    WHERE songId = :songId AND completed = 1 AND startedAt >= :since
""")
suspend fun getSongPlayCountSinceSuspend(songId: Long, since: Long): Int

// Weekly listening totals (ISO week, for Marathon Week)
@Query("""
    SELECT strftime('%Y-W%W', startedAt / 1000, 'unixepoch', 'localtime') AS weekKey,
           COALESCE(SUM(durationMs), 0) AS totalMs
    FROM listening_events
    WHERE completed = 1 AND startedAt >= :since
    GROUP BY weekKey
    ORDER BY weekKey ASC
""")
suspend fun getWeeklyListeningTotalsSuspend(since: Long): List<WeeklyListening>

// Song rank by all-time play count (1 = most played)
@Query("""
    SELECT COUNT(*) + 1 FROM (
        SELECT songId, COUNT(*) AS playCount
        FROM listening_events WHERE completed = 1
        GROUP BY songId
    ) WHERE playCount > (
        SELECT COUNT(*) FROM listening_events
        WHERE songId = :songId AND completed = 1
    )
""")
suspend fun getSongRankByPlayCountSuspend(songId: Long): Int

// Unique song count for an artist (for Artist Hour Milestones chip)
@Query("""
    SELECT COUNT(DISTINCT e.songId) FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE s.artist = :artist AND e.completed = 1
""")
suspend fun getUniqueSongCountForArtistSuspend(artist: String): Int

// Average daily listening in ms over a period (for Streak chips)
@Query("""
    SELECT COALESCE(
        SUM(durationMs) / NULLIF(COUNT(DISTINCT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime')), 0),
        0
    )
    FROM listening_events
    WHERE completed = 1 AND startedAt >= :since
""")
suspend fun getAvgDailyListeningMsSuspend(since: Long): Long

// Unique song count in a time period (for Streak and Total Hours chips)
@Query("""
    SELECT COUNT(DISTINCT songId) FROM listening_events
    WHERE completed = 1 AND startedAt >= :since
""")
suspend fun getUniqueSongCountInPeriodSuspend(since: Long): Int

// New songs discovered since a timestamp (for Explorer and Discovery Week chips)
@Query("""
    SELECT COUNT(*) FROM songs WHERE firstHeardAt >= :since
""")
suspend fun getNewSongsSinceSuspend(since: Long): Int

// Count days in last N ms with commute-hour listening (for Commute Listener chip)
@Query("""
    SELECT COUNT(DISTINCT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime'))
    FROM listening_events
    WHERE completed = 1 AND startedAt >= :since
    AND CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) IN (7, 8, 17, 18)
""")
suspend fun getCommuteDaysCountSuspend(since: Long): Int
```

**Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Room will validate all SQL at compile time — any syntax error will show here.

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt
git commit -m "feat: add DAO queries for new moment detectors and enriched statlines"
```

---

### Task 5: Update existing detectors in `MomentDetector.kt`

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

This task migrates all `statLine = "..."` usages to `statLines = listOf(...)` and adds the second chip for each type.

**Step 1: Update `detectSongPlayMilestones`**

Replace the `persistIfNew(Moment(...)` call so it uses `statLines` and adds rank:

```kotlin
private suspend fun detectSongPlayMilestones(): List<Moment> {
    val result = mutableListOf<Moment>()
    val songs = eventDao.getSongsWithMinPlays(SONG_PLAY_THRESHOLDS.first())
    for (song in songs) {
        for (threshold in SONG_PLAY_THRESHOLDS) {
            if (song.playCount >= threshold) {
                val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
                persistIfNew(Moment(
                    type = "SONG_PLAYS_$threshold",
                    entityKey = "${song.songId}:$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = "$threshold plays",
                    description = "You've played ${song.title} $threshold times",
                    songId = song.songId,
                    statLines = listOf("${formatDuration(song.totalDurationMs)} total", "#$rank all-time"),
                    imageUrl = song.albumArtUrl
                ))?.let { result += it }
            }
        }
    }
    return result
}
```

**Step 2: Update `detectArtistHourMilestones`**

```kotlin
private suspend fun detectArtistHourMilestones(): List<Moment> {
    val result = mutableListOf<Moment>()
    val artists = eventDao.getAllArtistsWithDurationSuspend()
    for (artist in artists) {
        val artistEntity = artistDao.findByName(artist.artist)
        val uniqueSongs = eventDao.getUniqueSongCountForArtistSuspend(artist.artist)
        for (thresholdMs in ARTIST_HOUR_THRESHOLDS_MS) {
            if (artist.totalDurationMs >= thresholdMs) {
                val hours = thresholdMs / 3_600_000L
                val humanHours = if (hours == 1L) "1 hour" else "$hours hours"
                persistIfNew(Moment(
                    type = "ARTIST_HOURS_$hours",
                    entityKey = "${artist.artist}:$hours",
                    triggeredAt = System.currentTimeMillis(),
                    title = "$humanHours of ${artist.artist}",
                    description = "You've spent $humanHours listening to ${artist.artist}",
                    artistId = artistEntity?.id,
                    statLines = listOf("${artist.playCount} plays", "$uniqueSongs songs heard"),
                    imageUrl = artistEntity?.imageUrl,
                    entityName = artist.artist
                ))?.let { result += it }
            }
        }
    }
    return result
}
```

**Step 3: Update `detectStreakMilestones`**

```kotlin
private suspend fun detectStreakMilestones(): List<Moment> {
    val result = mutableListOf<Moment>()
    val streak = computeStreak()
    for (threshold in STREAK_THRESHOLDS) {
        if (streak >= threshold) {
            val since = System.currentTimeMillis() - threshold.toLong() * 86_400_000L
            val avgMs = eventDao.getAvgDailyListeningMsSuspend(since)
            val avgMins = avgMs / 60_000L
            val uniqueSongs = eventDao.getUniqueSongCountInPeriodSuspend(since)
            persistIfNew(Moment(
                type = "STREAK_$threshold",
                entityKey = "$threshold",
                triggeredAt = System.currentTimeMillis(),
                title = "$threshold-day streak",
                description = "$threshold days in a row — you're on fire",
                statLines = listOf("avg ${avgMins}min/day", "$uniqueSongs songs this streak")
            ))?.let { result += it }
        }
    }
    return result
}
```

**Step 4: Update `detectTotalHourMilestones`**

```kotlin
private suspend fun detectTotalHourMilestones(now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val totalMs = eventDao.getTotalListeningTimeMsSuspend()
    val uniqueSongs = eventDao.getUniqueSongCountSuspend()
    val uniqueArtists = eventDao.getUniqueArtistCountSuspend()
    for (thresholdMs in TOTAL_HOUR_THRESHOLDS_MS) {
        if (totalMs >= thresholdMs) {
            val hours = thresholdMs / 3_600_000L
            persistIfNew(Moment(
                type = "TOTAL_HOURS_$hours",
                entityKey = "$hours",
                triggeredAt = now,
                title = "$hours hours",
                description = "You've listened to ${hours}h of music in total",
                statLines = listOf("$uniqueSongs unique songs", "$uniqueArtists artists")
            ))?.let { result += it }
        }
    }
    return result
}
```

**Step 5: Update `detectDiscoveryMilestones`**

```kotlin
private suspend fun detectDiscoveryMilestones(): List<Moment> {
    val result = mutableListOf<Moment>()
    val uniqueSongs = eventDao.getUniqueSongCountSuspend()
    val uniqueArtists = eventDao.getUniqueArtistCountSuspend()
    val totalMs = eventDao.getTotalListeningTimeMsSuspend()
    val totalHours = totalMs / 3_600_000L
    for (threshold in DISCOVERY_THRESHOLDS) {
        if (uniqueSongs >= threshold) {
            persistIfNew(Moment(
                type = "SONGS_DISCOVERED_$threshold",
                entityKey = "$threshold",
                triggeredAt = System.currentTimeMillis(),
                title = "$threshold songs",
                description = "You've discovered $threshold unique songs",
                statLines = listOf("from $uniqueArtists artists", "${totalHours}h of music")
            ))?.let { result += it }
        }
    }
    return result
}
```

**Step 6: Update `detectArchetypes` — time-of-day archetypes**

Inside `detectArchetypes`, replace each `statLine = "..."` with `statLines = listOf(...)` as follows:

For `ARCHETYPE_NIGHT_OWL`:
```kotlin
val peakNightHour = hourly.filter { it.hour in listOf(22, 23, 0, 1, 2, 3) }
    .maxByOrNull { it.totalDurationMs }?.hour ?: 23
val peakLabel = when (peakNightHour) {
    0 -> "midnight"; 1 -> "1am"; 2 -> "2am"; 3 -> "3am"
    22 -> "10pm"; 23 -> "11pm"; else -> "${peakNightHour}pm"
}
statLines = listOf("$nightPct% of your listening", "peak: $peakLabel")
```

For `ARCHETYPE_MORNING_LISTENER`:
```kotlin
val peakMorningHour = hourly.filter { it.hour in 5..8 }
    .maxByOrNull { it.totalDurationMs }?.hour ?: 7
statLines = listOf("$morningPct% of your listening", "peak: ${peakMorningHour}am")
```

For `ARCHETYPE_COMMUTE_LISTENER`:
```kotlin
val commuteDays = eventDao.getCommuteDaysCountSuspend(since)
statLines = listOf("$commutePct% during commute hours", "$commuteDays days this month")
```

For `ARCHETYPE_COMPLETIONIST`:
```kotlin
statLines = listOf("${skipPct}% skip rate", "$totalPlays plays")
```

For `ARCHETYPE_CERTIFIED_SKIPPER`:
```kotlin
statLines = listOf("${skipPct}% skip rate", "$totalSkips skips")
```
(Note: `totalSkips` is already computed as `eventDao.getTotalSkipCountSuspend()`)

For `ARCHETYPE_DEEP_CUT_DIGGER`:
```kotlin
val deepCutHours = deepCuts[0].totalDurationMs / 3_600_000L
val deepCutMins = (deepCuts[0].totalDurationMs % 3_600_000L) / 60_000L
val deepCutDuration = if (deepCutHours > 0) "${deepCutHours}h ${deepCutMins}m" else "${deepCutMins}m"
statLines = listOf("${deepCuts[0].playCount} plays", "$deepCutDuration total")
```

For `ARCHETYPE_LOYAL_FAN`:
```kotlin
statLines = listOf("$topPct% of listening", "${topArtists[0].playCount} plays")
```

For `ARCHETYPE_EXPLORER`:
```kotlin
val newSongsThisWeek = eventDao.getNewSongsSinceSuspend(now - 7L * 24 * 3600 * 1000)
statLines = listOf("$newArtistsThisWeek new artists", "$newSongsThisWeek new songs")
```

**Step 7: Update `detectObsessionDaily`**

```kotlin
// Inside the song loop, after the playCount check:
val songAllTimePlays = eventDao.getSongPlayCountSinceSuspend(song.songId, 0L) // 0L = all time via since=0
persistIfNew(Moment(
    ...
    statLines = listOf("${song.playCount} plays today", "$songAllTimePlays all-time")
    ...
))
```

Wait — `getSongPlayCountSinceSuspend` with `since=0` gives all-time. But `getSongsPlayedOnDay` returns `SongPlayStats` (no `firstHeardAt`). We need the all-time play count separately. Use:
```kotlin
val allTimePlays = eventDao.getSongPlayCountSinceSuspend(song.songId, 0L)
statLines = listOf("${song.playCount} plays today", "$allTimePlays all-time")
```

**Step 8: Update `detectDailyRitual`**

```kotlin
// song is SongWithStats from getSongsWithMinPlays(7)
statLines = listOf("${song.playCount} all-time plays", formatDuration(song.totalDurationMs) + " total")
```

**Step 9: Update `detectBreakupCandidate`**

```kotlin
val playsThisWeek = eventDao.getArtistPlayCountSinceSuspend(artistStats.artist, sevenDaysAgo)
statLines = listOf("$skipCount skips this week", "$playsThisWeek plays this week")
```

**Step 10: Update `detectFastObsession`**

```kotlin
val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
statLines = listOf("${song.playCount} plays", "$ageDays days · #$rank all-time")
```

**Step 11: Update `detectLongestSession`**

```kotlin
statLines = listOf(label, "new personal best")
```

**Step 12: Update `detectQuickObsession`**

```kotlin
val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
statLines = listOf("#$rank all-time", "$ageDays days since discovered")
```

**Step 13: Update `detectDiscoveryWeek`**

```kotlin
val newSongsCount = eventDao.getNewSongsSinceSuspend(sevenDaysAgo)
statLines = listOf("$newArtistsCount new artists", "$newSongsCount new songs")
```

**Step 14: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The compiler will catch any leftover `statLine =` usages — fix them as you find them.

**Step 15: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: enrich all existing moment cards with multi-chip statlines"
```

---

### Task 6: Add new archetype detectors

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

Add these four private methods and call them from `detectArchetypes`.

**Step 1: Add `detectWeekendWarrior` inside `detectArchetypes`**

Add after the Explorer detection (still inside `detectArchetypes`):

```kotlin
// Weekend Warrior
val fourWeeksAgo = now - 28L * 24 * 3600 * 1000
val weekendMs = eventDao.getWeekendListeningMsSuspend(fourWeeksAgo)
val totalFourWeekMs = eventDao.getTotalListeningTimeMsInPeriodSuspend(fourWeeksAgo, now)
// Note: reuse getTotalPlayCountInPeriodSuspend is count, not ms. Use a new helper or calculate
// Actually: we need total ms in last 4 weeks. Use getListeningTimeSince as suspend:
// Add a suspend variant or use getTotalListeningTimeMsSuspend filtered.
// Simplest: compute from hourly. Or add getListeningTimeSinceSuspend.
// Let's add it as: eventDao.getListeningTimeSinceSuspend(fourWeeksAgo)
```

Wait — `getListeningTimeSince` exists as a `Flow<Long>` but not `suspend`. Add to DAO:

```kotlin
@Query("SELECT COALESCE(SUM(durationMs), 0) FROM listening_events WHERE startedAt >= :since AND completed = 1")
suspend fun getListeningTimeSinceSuspend(since: Long): Long
```

Then in the detector:

```kotlin
val fourWeeksAgo = now - 28L * 24 * 3600 * 1000
val weekendMs = eventDao.getWeekendListeningMsSuspend(fourWeeksAgo)
val totalFourWeekMs = eventDao.getListeningTimeSinceSuspend(fourWeeksAgo).coerceAtLeast(1L)
if (weekendMs.toDouble() / totalFourWeekMs > 0.6) {
    val weekendPct = (weekendMs * 100 / totalFourWeekMs).toInt()
    val weekendHoursPerWeek = weekendMs / 4 / 3_600_000L
    persistIfNew(Moment(
        type = "ARCHETYPE_WEEKEND_WARRIOR", entityKey = yearMonth, triggeredAt = now,
        title = "Weekend Warrior",
        description = "Most of your listening happens on weekends",
        statLines = listOf("$weekendPct% on weekends", "${weekendHoursPerWeek}h on weekends/week")
    ))?.let { result += it }
}
```

**Step 2: Add Wide Taste detection**

```kotlin
// Wide Taste
val uniqueArtistsThisMonth = eventDao.getUniqueArtistCountSinceSuspend(since) // since = thirtyDaysAgo
val topArtistThisMonth = eventDao.getTopArtistsByDurationSuspend(since, 1)
val totalThisMonthMs = eventDao.getListeningTimeSinceSuspend(since).coerceAtLeast(1L)
if (topArtistThisMonth.isNotEmpty() && uniqueArtistsThisMonth >= 15) {
    val topArtistPct = (topArtistThisMonth[0].totalDurationMs * 100 / totalThisMonthMs).toInt()
    if (topArtistPct < 15) {
        persistIfNew(Moment(
            type = "ARCHETYPE_WIDE_TASTE", entityKey = yearMonth, triggeredAt = now,
            title = "Wide Taste",
            description = "No single artist dominates your listening",
            statLines = listOf("$uniqueArtistsThisMonth artists this month", "top artist: $topArtistPct%")
        ))?.let { result += it }
    }
}
```

**Step 3: Add Repeat Offender detection**

```kotlin
// Repeat Offender
val totalPlaysThisMonth = eventDao.getTotalPlayCountInPeriodSuspend(since).coerceAtLeast(1L)
val top3ThisMonth = eventDao.getTopSongsInPeriodByPlayCountSuspend(since, 3)
if (top3ThisMonth.isNotEmpty()) {
    val top3Plays = top3ThisMonth.sumOf { it.playCount }
    val top3Pct = (top3Plays * 100 / totalPlaysThisMonth).toInt()
    if (top3Pct > 40) {
        val topSong = top3ThisMonth[0]
        persistIfNew(Moment(
            type = "ARCHETYPE_REPEAT_OFFENDER", entityKey = yearMonth, triggeredAt = now,
            title = "Repeat Offender",
            description = "You found your songs. You're not letting go.",
            songId = topSong.songId,
            statLines = listOf("top 3 songs: $top3Pct% of plays", "${topSong.title} × ${topSong.playCount}"),
            imageUrl = topSong.albumArtUrl
        ))?.let { result += it }
    }
}
```

**Step 4: Add Album Listener detection**

```kotlin
// Album Listener — detect consecutive same-artist runs ≥ 5 in sessions
val orderedEvents = eventDao.getOrderedSongArtistEventsSuspend(since)
val sessionGapMs = 30 * 60 * 1000L // 30-minute gap = new session
data class AlbumRun(val artist: String, val length: Int)
val albumRuns = mutableListOf<AlbumRun>()
var currentArtist: String? = null
var currentRunLength = 0
var lastEndMs = 0L

for (event in orderedEvents) {
    val isNewSession = event.startedAt - lastEndMs > sessionGapMs
    if (isNewSession || event.artist != currentArtist) {
        if (currentRunLength >= 5 && currentArtist != null) {
            albumRuns += AlbumRun(currentArtist!!, currentRunLength)
        }
        currentArtist = event.artist
        currentRunLength = 1
    } else {
        currentRunLength++
    }
    lastEndMs = event.startedAt + event.durationMs
}
// Check last run
if (currentRunLength >= 5 && currentArtist != null) {
    albumRuns += AlbumRun(currentArtist!!, currentRunLength)
}

if (albumRuns.size >= 3) {
    val avgRun = albumRuns.map { it.length }.average().toInt()
    val topRunArtist = albumRuns.groupBy { it.artist }
        .maxByOrNull { it.value.size }?.key
    val topArtistEntity = if (topRunArtist != null) artistDao.findByName(topRunArtist) else null
    val topSong = if (topRunArtist != null)
        eventDao.getTopSongsInPeriodByPlayCountSuspend(since, 100)
            .firstOrNull { it.artist == topRunArtist }
    else null
    persistIfNew(Moment(
        type = "ARCHETYPE_ALBUM_LISTENER", entityKey = yearMonth, triggeredAt = now,
        title = "Album Listener",
        description = "You don't shuffle. You commit.",
        songId = topSong?.songId,
        statLines = listOf("${albumRuns.size} album runs this month", "avg $avgRun songs per run"),
        imageUrl = topSong?.albumArtUrl
    ))?.let { result += it }
}
```

**Step 5: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If `getListeningTimeSinceSuspend` is missing, add it to the DAO first (the instruction is in Step 1 above).

**Step 6: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git add app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt  # for getListeningTimeSinceSuspend
git commit -m "feat: add 4 new archetype moment detectors (Weekend Warrior, Wide Taste, Repeat Offender, Album Listener)"
```

---

### Task 7: Add new behavioral detectors

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

Add six new private methods and wire them into `detectAndPersistNewMoments`.

**Step 1: Add `detectResurrection`**

```kotlin
private suspend fun detectResurrection(todayStart: Long, now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val todayEnd = todayStart + 86_400_000L
    val todaySongs = eventDao.getSongsPlayedOnDay(todayStart, todayEnd)
    val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()
    val thirtyDaysAgoMs = todayStart - 30L * 24 * 3600 * 1000

    for (song in todaySongs) {
        if (song.playCount < 5) continue
        val lastPlayedBefore = eventDao.getSongLastPlayedBeforeSuspend(song.songId, todayStart) ?: continue
        val gapDays = (todayStart - lastPlayedBefore) / 86_400_000L
        if (gapDays >= 30) {
            persistIfNew(Moment(
                type = "RESURRECTION",
                entityKey = "${song.songId}:$todayDate",
                triggeredAt = now,
                title = "It's back",
                description = "${song.title} went quiet for $gapDays days. Today it's all you're playing.",
                songId = song.songId,
                statLines = listOf("$gapDays days away", "${song.playCount} plays today"),
                imageUrl = song.albumArtUrl
            ))?.let { result += it }
        }
    }
    return result
}
```

**Step 2: Add `detectNightBinge`**

```kotlin
private suspend fun detectNightBinge(now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val thirtyDaysAgo = now - 30L * 24 * 3600 * 1000
    val nightDays = eventDao.getNightListeningByDaySuspend(thirtyDaysAgo)

    for (dayNight in nightDays) {
        if (dayNight.nightMs >= 2 * 3_600_000L) {
            val duration = formatDuration(dayNight.nightMs)
            // Rough song count: assume avg 3.5 min per song
            val songCount = (dayNight.nightMs / 210_000L).toInt().coerceAtLeast(1)
            persistIfNew(Moment(
                type = "NIGHT_BINGE",
                entityKey = dayNight.day,
                triggeredAt = now,
                title = "Night binge",
                description = "You listened for $duration after midnight",
                statLines = listOf("$duration after midnight", "~$songCount songs")
            ))?.let { result += it }
        }
    }
    return result
}
```

**Step 3: Add `detectComfortZone`**

```kotlin
private suspend fun detectComfortZone(sevenDaysAgo: Long, now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
    val totalPlaysThisWeek = eventDao.getTotalPlayCountInPeriodSuspend(sevenDaysAgo).coerceAtLeast(1L)
    val top5 = eventDao.getTopSongsInPeriodByPlayCountSuspend(sevenDaysAgo, 5)
    if (top5.isEmpty()) return result
    val top5Plays = top5.sumOf { it.playCount }
    val top5Pct = (top5Plays * 100 / totalPlaysThisWeek).toInt()
    if (top5Pct >= 80) {
        val topSong = top5.first()
        persistIfNew(Moment(
            type = "COMFORT_ZONE",
            entityKey = weekKey,
            triggeredAt = now,
            title = "Comfort zone",
            description = "Your top 5 songs made up $top5Pct% of your listening this week",
            songId = topSong.songId,
            statLines = listOf("$top5Pct% from 5 songs", "$totalPlaysThisWeek plays this week"),
            imageUrl = topSong.albumArtUrl
        ))?.let { result += it }
    }
    return result
}
```

**Step 4: Add `detectRediscovery`**

```kotlin
private suspend fun detectRediscovery(sevenDaysAgo: Long, now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
    val sixtyDaysAgo = now - 60L * 24 * 3600 * 1000
    val topArtistsThisWeek = eventDao.getTopArtistsByDurationSuspend(sevenDaysAgo, 5)

    for (artistStats in topArtistsThisWeek) {
        val playsThisWeek = eventDao.getArtistPlayCountSinceSuspend(artistStats.artist, sevenDaysAgo)
        if (playsThisWeek < 5) continue
        val lastPlayedBefore = eventDao.getArtistLastPlayedBeforeSuspend(artistStats.artist, sevenDaysAgo) ?: continue
        val gapDays = (sevenDaysAgo - lastPlayedBefore) / 86_400_000L
        if (gapDays >= 60) {
            val artistEntity = artistDao.findByName(artistStats.artist)
            persistIfNew(Moment(
                type = "REDISCOVERY",
                entityKey = "${artistStats.artist}:$weekKey",
                triggeredAt = now,
                title = "You're back",
                description = "You hadn't played ${artistStats.artist} in $gapDays days. Welcome back.",
                artistId = artistEntity?.id,
                statLines = listOf("$gapDays days away", "$playsThisWeek plays this week"),
                imageUrl = artistEntity?.imageUrl,
                entityName = artistStats.artist
            ))?.let { result += it }
        }
    }
    return result
}
```

**Step 5: Add `detectSlowBurn`**

```kotlin
private suspend fun detectSlowBurn(sevenDaysAgo: Long, now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
    val sixtyDaysAgo = now - 60L * 24 * 3600 * 1000
    val topThisWeek = eventDao.getTopSongsInPeriodByPlayCountSuspend(sevenDaysAgo, 20)

    for (song in topThisWeek) {
        val playsThisWeek = eventDao.getSongPlayCountSinceSuspend(song.songId, sevenDaysAgo)
        if (playsThisWeek < 5) continue
        val playsBeforeThisWeek = eventDao.getSongPlayCountBeforeSuspend(song.songId, sevenDaysAgo)
        if (playsBeforeThisWeek >= 5) continue // Not a slow burn if already well-played
        // Check song age
        val songDetails = eventDao.getSongsWithMinPlays(0).firstOrNull { it.songId == song.songId } ?: continue
        val ageDays = (now - songDetails.firstHeardAt) / 86_400_000L
        if (ageDays < 60) continue
        val totalPlays = playsBeforeThisWeek + playsThisWeek
        persistIfNew(Moment(
            type = "SLOW_BURN",
            entityKey = "${song.songId}:$weekKey",
            triggeredAt = now,
            title = "Slow burn",
            description = "${song.title} has been in your library for $ageDays days. It just clicked.",
            songId = song.songId,
            statLines = listOf("$ageDays days to click", "$totalPlays plays now"),
            imageUrl = song.albumArtUrl
        ))?.let { result += it }
    }
    return result
}
```

**Note on `getSongsWithMinPlays(0)`:** This will return all songs. If the list is large, consider adding a dedicated `getSongByIdSuspend(songId: Long): SongWithStats?` DAO query instead. Either approach works — pick what compiles cleanly.

**Step 6: Add `detectMarathonWeek`**

```kotlin
private suspend fun detectMarathonWeek(now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val weekKey = "W${LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-ww"))}"
    // Load all weekly totals (since ~2 years ago for enough history)
    val twoYearsAgo = now - 730L * 24 * 3600 * 1000
    val weeks = eventDao.getWeeklyListeningTotalsSuspend(twoYearsAgo)
    if (weeks.size < 2) return result

    val currentWeekMs = weeks.lastOrNull()?.totalMs ?: return result
    val previousMax = weeks.dropLast(1).maxOfOrNull { it.totalMs } ?: return result

    if (currentWeekMs > previousMax) {
        val sevenDaysAgo = now - 7L * 24 * 3600 * 1000
        val songCount = eventDao.getTopSongsInPeriodByPlayCountSuspend(sevenDaysAgo, 999).size
        val artistCount = eventDao.getUniqueArtistCountSinceSuspend(sevenDaysAgo)
        val duration = formatDuration(currentWeekMs)
        persistIfNew(Moment(
            type = "MARATHON_WEEK",
            entityKey = weekKey,
            triggeredAt = now,
            title = "Marathon week",
            description = "New record: $duration this week",
            statLines = listOf("$duration this week", "$songCount songs · $artistCount artists")
        ))?.let { result += it }
    }
    return result
}
```

**Step 7: Wire all new detectors into `detectAndPersistNewMoments`**

In `detectAndPersistNewMoments()`, add after the existing `newMoments += detectDiscoveryWeek(...)` call:

```kotlin
newMoments += detectResurrection(todayStart, now)
newMoments += detectNightBinge(now)
newMoments += detectComfortZone(sevenDaysAgo, now)
newMoments += detectRediscovery(sevenDaysAgo, now)
newMoments += detectSlowBurn(sevenDaysAgo, now)
newMoments += detectMarathonWeek(now)
```

Also update `detectArchetypes(...)` call to include calls to the new archetype detectors — all four new archetypes were added inline in Task 6 Step 1–4, so no change needed here as long as they are inside `detectArchetypes`.

**Step 8: Build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Fix any unresolved references.

**Step 9: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add 6 new behavioral moment detectors (Resurrection, Night Binge, Comfort Zone, Rediscovery, Slow Burn, Marathon Week)"
```

---

### Task 8: Final build and install

**Step 1: Full build**

```bash
./gradlew assembleDebug
```

Expected: `BUILD SUCCESSFUL` with no warnings about unresolved Room entities.

**Step 2: Run unit tests**

```bash
./gradlew test
```

Expected: all existing tests pass. There are no new unit tests required — detector logic is tested by installing and triggering detection.

**Step 3: Install**

```bash
./gradlew installDebug
```

**Step 4: Verify on device**

1. Open the app — it will run the DB migration (v11→v12), wipe all moments, re-detect
2. Navigate to the Moments feed — cards should now show 2 chips each
3. Cards that previously showed one chip (e.g. Song Play Milestones) should now show two pills side-by-side
4. Cards that previously showed no chips (Streaks, Total Hours) should now show two pills

**Step 5: Commit**

No new code in this task — just verification. If any fixes were made, commit them here:

```bash
git add -p
git commit -m "fix: address final build issues from card types expansion"
```
