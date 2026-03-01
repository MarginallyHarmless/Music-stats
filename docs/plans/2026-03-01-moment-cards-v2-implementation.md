# Moment Cards v2 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prune 5 low-value moment types, add 12 narrative moment types with story-driven copy, and visually refresh the share card layout.

**Architecture:** New DAO queries feed 12 new detection methods in MomentDetector. MomentCopywriter gets narrative-specific copy templates. Share card gets typography/color/spacing overhaul. Pruned types removed from thresholds, copywriter, and preview data.

**Tech Stack:** Kotlin, Room DAOs, Jetpack Compose, Coil 3, Material 3

---

### Task 1: Prune Low-Value Moment Thresholds

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt:22-27`

**Step 1: Update threshold lists**

Change the companion object thresholds to remove pruned values:

```kotlin
companion object {
    val SONG_PLAY_THRESHOLDS = listOf(50, 100, 250, 500)
    val ARTIST_HOUR_THRESHOLDS_MS = listOf(5L, 10L, 24L).map { it * 3_600_000L }
    val STREAK_THRESHOLDS = listOf(7, 14, 30, 100)
    val TOTAL_HOUR_THRESHOLDS_MS = listOf(24L, 100L, 500L, 1000L).map { it * 3_600_000L }
    val DISCOVERY_THRESHOLDS = listOf(100, 250, 500)
}
```

Removed: `10, 25` from song plays, `1` from artist hours, `3` from streaks, `50` from discovery.
Kept: `TOTAL_HOURS_24` per design.

**Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: prune low-value moment thresholds (SONG_PLAYS_10/25, ARTIST_HOURS_1, STREAK_3, SONGS_DISCOVERED_50)"
```

---

### Task 2: Remove Pruned Copy from MomentCopywriter

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt:40-49,72-76,94-98,143-147`

**Step 1: Remove pruned copy blocks**

Delete these `when` branches from `generate()`:

```kotlin
// DELETE: SONG_PLAYS_10 block (lines 40-44)
type == "SONG_PLAYS_10" -> MomentCopy(...)

// DELETE: SONG_PLAYS_25 block (lines 45-49)
type == "SONG_PLAYS_25" -> MomentCopy(...)

// DELETE: ARTIST_HOURS_1 block (lines 72-76)
type == "ARTIST_HOURS_1" -> MomentCopy(...)

// DELETE: STREAK_3 block (lines 94-98)
type == "STREAK_3" -> MomentCopy(...)

// DELETE: SONGS_DISCOVERED_50 block (lines 143-147)
type == "SONGS_DISCOVERED_50" -> MomentCopy(...)
```

**Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt
git commit -m "feat: remove copywriter templates for pruned moment types"
```

---

### Task 3: Remove Pruned Preview Moments from AllMomentsViewModel

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt`

**Step 1: Update preview moment lists**

In `buildPreviewMoments()`, update the threshold lists to match the pruned values:

```kotlin
// Song Play Milestones: was listOf(10, 25, 50, 100, 250, 500), now:
listOf(50, 100, 250, 500).forEachIndexed { idx, threshold ->

// Artist Hour Milestones: was listOf(1, 5, 10, 24), now:
listOf(5, 10, 24).forEachIndexed { idx, hours ->

// Streak Milestones: was listOf(3, 7, 14, 30, 100), now:
listOf(7, 14, 30, 100).forEachIndexed { idx, days ->

// Discovery Milestones: was listOf(50, 100, 250, 500), now:
listOf(100, 250, 500).forEachIndexed { idx, count ->
```

**Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt
git commit -m "feat: update preview moments to match pruned thresholds"
```

---

### Task 4: Add New DAO Queries for Narrative Moments

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt`

**Step 1: Add data classes for new query result types**

Add these at the top of the file alongside existing data classes:

```kotlin
data class WeeklyPlays(
    val weekKey: String,
    val playCount: Int
)

data class MonthlyPlays(
    val monthKey: String,
    val playCount: Int
)
```

**Step 2: Add the 8 new suspend queries**

Add inside the `ListeningEventDao` interface, after the existing suspend queries:

```kotlin
// --- Narrative Moment queries ---

// First song heard by an artist (for Origin Story, Gateway)
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
           COUNT(CASE WHEN e.completed = 1 THEN 1 END) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE s.artist = :artist
    GROUP BY e.songId
    ORDER BY MIN(e.startedAt) ASC
    LIMIT 1
""")
suspend fun getFirstSongByArtist(artist: String): SongPlayStats?

// Most-played song by an artist (for Gateway)
@Query("""
    SELECT e.songId, s.title, s.artist, s.albumArtUrl,
           COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
           COUNT(CASE WHEN e.completed = 1 THEN 1 END) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE s.artist = :artist AND e.completed = 1
    GROUP BY e.songId
    ORDER BY playCount DESC
    LIMIT 1
""")
suspend fun getMostPlayedSongByArtist(artist: String): SongPlayStats?

// Artist rank by total listening duration (for Origin Story)
@Query("""
    SELECT COUNT(*) + 1 FROM (
        SELECT s.artist, SUM(e.durationMs) AS totalMs
        FROM listening_events e
        JOIN songs s ON e.songId = s.id
        WHERE e.completed = 1
        GROUP BY s.artist
    ) WHERE totalMs > (
        SELECT COALESCE(SUM(e2.durationMs), 0)
        FROM listening_events e2
        JOIN songs s2 ON e2.songId = s2.id
        WHERE s2.artist = :artist AND e2.completed = 1
    )
""")
suspend fun getArtistRankByDuration(artist: String): Int

// Weekly play counts for a specific song (for Slow Build)
@Query("""
    SELECT strftime('%Y-W%W', startedAt / 1000, 'unixepoch', 'localtime') AS weekKey,
           COUNT(*) AS playCount
    FROM listening_events
    WHERE songId = :songId AND completed = 1 AND startedAt >= :since
    GROUP BY weekKey
    ORDER BY weekKey ASC
""")
suspend fun getWeeklyPlayCountsForSong(songId: Long, since: Long): List<WeeklyPlays>

// Song play count between two timestamps (for Binge and Fade)
@Query("""
    SELECT COUNT(*) FROM listening_events
    WHERE songId = :songId AND completed = 1
    AND startedAt >= :from AND startedAt < :until
""")
suspend fun getSongPlayCountBetween(songId: Long, from: Long, until: Long): Int

// Artist monthly play counts (for One That Got Away)
@Query("""
    SELECT strftime('%Y-%m', e.startedAt / 1000, 'unixepoch', 'localtime') AS monthKey,
           COUNT(*) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE s.artist = :artist AND e.completed = 1 AND e.startedAt >= :since
    GROUP BY monthKey
    ORDER BY monthKey ASC
""")
suspend fun getArtistMonthlyPlayCounts(artist: String, since: Long): List<MonthlyPlays>

// Top artists by play count in a time window (for One That Got Away)
@Query("""
    SELECT s.artist, SUM(e.durationMs) AS totalDurationMs, COUNT(*) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1 AND e.startedAt >= :from AND e.startedAt < :until
    GROUP BY s.artist
    ORDER BY playCount DESC
    LIMIT :limit
""")
suspend fun getTopArtistsByPlayCountInPeriod(from: Long, until: Long, limit: Int): List<ArtistPlayStats>

// Top artist filtered by time-of-day window (for Night and Day)
// hourStart/hourEnd are 0-23. Handles wrap-around (e.g., 22-4) with OR logic.
@Query("""
    SELECT s.artist,
           COALESCE(SUM(e.durationMs), 0) AS totalDurationMs,
           COUNT(*) AS playCount
    FROM listening_events e
    JOIN songs s ON e.songId = s.id
    WHERE e.completed = 1 AND e.startedAt >= :since
    AND (
        CASE WHEN :hourStart <= :hourEnd
            THEN CAST(strftime('%H', e.startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) >= :hourStart
                 AND CAST(strftime('%H', e.startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) < :hourEnd
            ELSE CAST(strftime('%H', e.startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) >= :hourStart
                 OR CAST(strftime('%H', e.startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) < :hourEnd
        END
    )
    GROUP BY s.artist
    ORDER BY totalDurationMs DESC
    LIMIT :limit
""")
suspend fun getTopArtistInTimeWindow(since: Long, hourStart: Int, hourEnd: Int, limit: Int): List<ArtistPlayStats>
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (Room will generate implementations at compile time)

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt
git commit -m "feat: add 8 new DAO queries for narrative moment detection"
```

---

### Task 5: Add Narrative Moment Copy to MomentCopywriter

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt`

**Step 1: Add narrative copy blocks**

Add these `when` branches inside `generate()`, before the `else` fallback block. Each narrative type uses `rawStats` keys populated by the detector (Task 6+).

```kotlin
// ── Narrative: Discovery Arcs ─────────────────────
type == "NARRATIVE_ORIGIN_STORY" -> {
    val firstSong = rawStats["firstSong"]?.toString() ?: "a song"
    val rank = rawStats["rank"]?.toString() ?: "?"
    val daysAgo = rawStats["daysAgo"]?.toString() ?: "?"
    MomentCopy(
        title = "Origin Story",
        description = "You discovered $name through $firstSong, $daysAgo days ago. They just became your #$rank most-listened artist.",
        statLines = rawStats.toStatLines("uniqueSongs" to "%s songs", "totalHours" to "%sh total", "daysAgo" to "discovered %sd ago")
    )
}
type == "NARRATIVE_GATEWAY" -> {
    val firstSong = rawStats["firstSong"]?.toString() ?: "a song"
    val topSong = rawStats["topSong"]?.toString() ?: "another song"
    val ratio = rawStats["ratio"]?.toString() ?: "?"
    MomentCopy(
        title = "The Gateway",
        description = "$firstSong was your gateway to $name. But $topSong became your real favorite — ${ratio}× more plays.",
        statLines = rawStats.toStatLines("firstSong" to "first: %s", "topSongLine" to "favorite: %s")
    )
}
type == "NARRATIVE_COLLECTION" -> {
    val songCount = rawStats["songCount"]?.toString() ?: "?"
    val firstSong = rawStats["firstSong"]?.toString() ?: "the beginning"
    MomentCopy(
        title = "The Collection",
        description = "You've heard $songCount songs by $name — from $firstSong to the deep cuts.",
        statLines = rawStats.toStatLines("songCount" to "%s songs", "totalHours" to "%sh total", "daysAgo" to "first heard %sd ago")
    )
}

// ── Narrative: Obsession Arcs ─────────────────────
type == "NARRATIVE_TAKEOVER" -> {
    val daysAgo = rawStats["daysAgo"]?.toString() ?: "?"
    val rank = rawStats["rank"]?.toString() ?: "?"
    MomentCopy(
        title = "The Takeover",
        description = "$daysAgo days ago, this didn't exist in your library. Now it's #$rank all-time.",
        statLines = rawStats.toStatLines("rank" to "#%s all-time", "playCount" to "%s plays", "daysAgo" to "%s days")
    )
}
type == "NARRATIVE_SLOW_BUILD" -> {
    val w1 = rawStats["w1"]?.toString() ?: "?"
    val w2 = rawStats["w2"]?.toString() ?: "?"
    val w3 = rawStats["w3"]?.toString() ?: "?"
    val w4 = rawStats["w4"]?.toString() ?: "?"
    MomentCopy(
        title = "The Slow Build",
        description = "Week 1: $w1 plays. Week 2: $w2. Week 3: $w3. Week 4: $w4. You didn't binge it — you fell for it gradually.",
        statLines = rawStats.toStatLines("trajectory" to "%s")
    )
}
type == "NARRATIVE_BINGE_AND_FADE" -> {
    val bingePlays = rawStats["bingePlays"]?.toString() ?: "?"
    val fadePlays = rawStats["fadePlays"]?.toString() ?: "?"
    MomentCopy(
        title = "Burned Bright",
        description = "$bingePlays plays in its first 2 weeks. In the weeks since? $fadePlays. Burned bright, burned fast.",
        statLines = rawStats.toStatLines("bingePlays" to "%s plays first 2 weeks", "fadePlays" to "%s plays since")
    )
}

// ── Narrative: Comeback Arcs ──────────────────────
type == "NARRATIVE_FULL_CIRCLE" -> {
    val gapDays = rawStats["gapDays"]?.toString() ?: "?"
    val playsThisWeek = rawStats["playsThisWeek"]?.toString() ?: "?"
    MomentCopy(
        title = "Full Circle",
        description = "You hadn't touched $name in $gapDays days. This week: $playsThisWeek plays. Some things come back around.",
        statLines = rawStats.toStatLines("gapDays" to "%s days away", "playsThisWeek" to "%s plays this week")
    )
}
type == "NARRATIVE_ONE_THAT_GOT_AWAY" -> {
    val peakPlays = rawStats["peakPlays"]?.toString() ?: "?"
    val peakMonth = rawStats["peakMonth"]?.toString() ?: "?"
    val currentPlays = rawStats["currentPlays"]?.toString() ?: "?"
    MomentCopy(
        title = "The One That Got Away",
        description = "$name was your most-played in $peakMonth — $peakPlays plays. This month? Just $currentPlays.",
        statLines = rawStats.toStatLines("peakPlays" to "peak: %s plays", "currentPlays" to "now: %s plays")
    )
}

// ── Narrative: Consistency Arcs ───────────────────
type == "NARRATIVE_SOUNDTRACK" -> {
    val distinctDays = rawStats["distinctDays"]?.toString() ?: "?"
    val monthSpan = rawStats["monthSpan"]?.toString() ?: "?"
    MomentCopy(
        title = "The Soundtrack",
        description = "This song has been with you for $monthSpan months. Played on $distinctDays different days.",
        statLines = rawStats.toStatLines("distinctDays" to "%s days", "monthSpan" to "%s months", "playCount" to "%s plays")
    )
}

// ── Narrative: Session Arcs ───────────────────────
type == "NARRATIVE_RABBIT_HOLE" -> {
    val songCount = rawStats["songCount"]?.toString() ?: "?"
    val duration = rawStats["duration"]?.toString() ?: "?"
    MomentCopy(
        title = "The Rabbit Hole",
        description = "You went deep: $songCount $name songs back-to-back, $duration without coming up for air.",
        statLines = rawStats.toStatLines("songCount" to "%s songs", "duration" to "%s", "noInterrupt" to "%s")
    )
}

// ── Narrative: Contrast Arcs ──────────────────────
type == "NARRATIVE_NIGHT_AND_DAY" -> {
    val dayArtist = rawStats["dayArtist"]?.toString() ?: "?"
    val nightArtist = rawStats["nightArtist"]?.toString() ?: "?"
    MomentCopy(
        title = "Night & Day",
        description = "By day: $dayArtist. After midnight: $nightArtist. Your 2am self has different taste.",
        statLines = rawStats.toStatLines("dayLine" to "day: %s", "nightLine" to "night: %s")
    )
}
type == "NARRATIVE_PARALLEL_LIVES" -> {
    val artist1 = rawStats["artist1"]?.toString() ?: "?"
    val artist2 = rawStats["artist2"]?.toString() ?: "?"
    MomentCopy(
        title = "Parallel Lives",
        description = "$artist1 and $artist2 — you listen to both, but never in the same session. Zero overlap.",
        statLines = rawStats.toStatLines("artist1Line" to "%s", "artist2Line" to "%s", "overlap" to "%s")
    )
}
```

**Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt
git commit -m "feat: add copywriter templates for 12 narrative moment types"
```

---

### Task 6: Add Narrative Detection Methods — Discovery Arcs (Origin Story, Gateway, Collection)

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Add detection methods**

Add these private methods to MomentDetector. They follow the existing pattern: query data → check threshold → generate copy → persistIfNew.

```kotlin
private suspend fun detectNarrativeOriginStory(): List<Moment> {
    val result = mutableListOf<Moment>()
    // Get all artists sorted by total listening duration
    val allArtists = eventDao.getAllArtistsWithDurationSuspend()
    // Only consider top 5
    val top5 = allArtists.take(5)
    for (artistStats in top5) {
        val type = "NARRATIVE_ORIGIN_STORY"
        val entityKey = artistStats.artist
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        val artist = artistDao.findByName(artistStats.artist) ?: continue
        val firstSong = eventDao.getFirstSongByArtist(artistStats.artist) ?: continue
        val rank = eventDao.getArtistRankByDuration(artistStats.artist)
        val daysAgo = ((System.currentTimeMillis() - artist.firstHeardAt) / 86_400_000L).toInt()
        val uniqueSongs = eventDao.getUniqueSongCountForArtistSuspend(artistStats.artist)
        val totalHours = artistStats.totalDurationMs / 3_600_000L

        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "firstSong" to firstSong.title,
            "rank" to rank,
            "daysAgo" to daysAgo,
            "uniqueSongs" to uniqueSongs,
            "totalHours" to totalHours
        )
        val copy = MomentCopywriter.generate(type, artistStats.artist, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = System.currentTimeMillis(),
            title = copy.title,
            description = copy.description,
            entityName = artistStats.artist,
            statLines = copy.statLines,
            imageUrl = artist.imageUrl,
            artistId = artist.id,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}

private suspend fun detectNarrativeGateway(): List<Moment> {
    val result = mutableListOf<Moment>()
    val allArtists = eventDao.getAllArtistsWithDurationSuspend()
    // Only consider artists with 10+ hours
    val qualifying = allArtists.filter { it.totalDurationMs >= 10 * 3_600_000L }
    for (artistStats in qualifying) {
        val type = "NARRATIVE_GATEWAY"
        val entityKey = artistStats.artist
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        val firstSong = eventDao.getFirstSongByArtist(artistStats.artist) ?: continue
        val topSong = eventDao.getMostPlayedSongByArtist(artistStats.artist) ?: continue

        // Only fire if gateway song != favorite song
        if (firstSong.songId == topSong.songId) continue
        // Avoid division by zero
        if (firstSong.playCount <= 0) continue

        val ratio = topSong.playCount / firstSong.playCount
        if (ratio < 2) continue // Not interesting enough if ratio is small

        val artist = artistDao.findByName(artistStats.artist) ?: continue
        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "firstSong" to firstSong.title,
            "topSong" to topSong.title,
            "ratio" to ratio,
            "topSongLine" to "${topSong.title} (${topSong.playCount} plays)"
        )
        val copy = MomentCopywriter.generate(type, artistStats.artist, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = System.currentTimeMillis(),
            title = copy.title,
            description = copy.description,
            entityName = artistStats.artist,
            statLines = copy.statLines,
            imageUrl = artist.imageUrl,
            artistId = artist.id,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}

private suspend fun detectNarrativeCollection(): List<Moment> {
    val result = mutableListOf<Moment>()
    val allArtists = eventDao.getAllArtistsWithDurationSuspend()
    val now = System.currentTimeMillis()
    for (artistStats in allArtists) {
        val type = "NARRATIVE_COLLECTION"
        val entityKey = artistStats.artist
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        val uniqueSongs = eventDao.getUniqueSongCountForArtistSuspend(artistStats.artist)
        if (uniqueSongs < 15) continue

        val artist = artistDao.findByName(artistStats.artist) ?: continue
        val daysSinceFirst = ((now - artist.firstHeardAt) / 86_400_000L).toInt()
        if (daysSinceFirst < 30) continue

        val firstSong = eventDao.getFirstSongByArtist(artistStats.artist)
        val totalHours = artistStats.totalDurationMs / 3_600_000L
        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "songCount" to uniqueSongs,
            "firstSong" to (firstSong?.title ?: "the beginning"),
            "totalHours" to totalHours,
            "daysAgo" to daysSinceFirst
        )
        val copy = MomentCopywriter.generate(type, artistStats.artist, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            entityName = artistStats.artist,
            statLines = copy.statLines,
            imageUrl = artist.imageUrl,
            artistId = artist.id,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}
```

**Step 2: Wire into detectAndPersistNewMoments()**

Add these calls in `detectAndPersistNewMoments()`, after the existing detector calls:

```kotlin
// Narrative moments
newMoments += detectNarrativeOriginStory()
newMoments += detectNarrativeGateway()
newMoments += detectNarrativeCollection()
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add narrative detection — Origin Story, Gateway, Collection"
```

---

### Task 7: Add Narrative Detection Methods — Obsession Arcs (Takeover, Slow Build, Binge and Fade)

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Add detection methods**

```kotlin
private suspend fun detectNarrativeTakeover(): List<Moment> {
    val result = mutableListOf<Moment>()
    val now = System.currentTimeMillis()
    val thirtyDaysAgo = now - 30L * 86_400_000L
    // Get top 10 songs all-time
    val topSongs = eventDao.getTopSongsByPlayCountSuspend(10)
    for (song in topSongs) {
        val type = "NARRATIVE_TAKEOVER"
        val entityKey = song.songId.toString()
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        // Song must have been discovered in last 30 days
        // Need firstHeardAt — query from songs table
        val songWithStats = eventDao.getSongsWithMinPlays(1)
            .find { it.songId == song.songId } ?: continue
        if (songWithStats.firstHeardAt < thirtyDaysAgo) continue

        val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
        if (rank > 10) continue

        val daysAgo = ((now - songWithStats.firstHeardAt) / 86_400_000L).toInt()
        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "rank" to rank,
            "playCount" to song.playCount,
            "daysAgo" to daysAgo
        )
        val copy = MomentCopywriter.generate(type, song.title, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            entityName = song.artist,
            songId = song.songId,
            statLines = copy.statLines,
            imageUrl = song.albumArtUrl,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}

private suspend fun detectNarrativeSlowBuild(): List<Moment> {
    val result = mutableListOf<Moment>()
    val now = System.currentTimeMillis()
    val fourWeeksAgo = now - 28L * 86_400_000L
    // Check songs with decent play counts
    val songs = eventDao.getSongsWithMinPlays(15)
    for (song in songs) {
        val weeklyPlays = eventDao.getWeeklyPlayCountsForSong(song.songId, fourWeeksAgo)
        if (weeklyPlays.size < 4) continue

        // Take the last 4 weeks
        val last4 = weeklyPlays.takeLast(4)
        // Check strictly increasing
        val increasing = (0 until 3).all { last4[it].playCount < last4[it + 1].playCount }
        if (!increasing) continue
        // Final week must be 15+
        if (last4.last().playCount < 15) continue

        val type = "NARRATIVE_SLOW_BUILD"
        val entityKey = "${song.songId}:${last4.last().weekKey}"
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "w1" to last4[0].playCount,
            "w2" to last4[1].playCount,
            "w3" to last4[2].playCount,
            "w4" to last4[3].playCount,
            "trajectory" to "${last4[0].playCount} → ${last4[1].playCount} → ${last4[2].playCount} → ${last4[3].playCount} plays"
        )
        val copy = MomentCopywriter.generate(type, song.title, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            entityName = song.artist,
            songId = song.songId,
            statLines = copy.statLines,
            imageUrl = song.albumArtUrl,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}

private suspend fun detectNarrativeBingeAndFade(): List<Moment> {
    val result = mutableListOf<Moment>()
    val now = System.currentTimeMillis()
    val thirtyFiveDaysMs = 35L * 86_400_000L
    val fourteenDaysMs = 14L * 86_400_000L
    // Songs with 30+ plays that are at least 35 days old
    val songs = eventDao.getSongsWithMinPlays(30)
    for (song in songs) {
        val type = "NARRATIVE_BINGE_AND_FADE"
        val entityKey = song.songId.toString()
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        val age = now - song.firstHeardAt
        if (age < thirtyFiveDaysMs) continue

        val bingeEnd = song.firstHeardAt + fourteenDaysMs
        val bingePlays = eventDao.getSongPlayCountBetween(song.songId, song.firstHeardAt, bingeEnd)
        if (bingePlays < 30) continue

        val fadePlays = eventDao.getSongPlayCountBetween(song.songId, bingeEnd, now)
        if (fadePlays >= 5) continue

        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "bingePlays" to bingePlays,
            "fadePlays" to fadePlays
        )
        val copy = MomentCopywriter.generate(type, song.title, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            entityName = song.artist,
            songId = song.songId,
            statLines = copy.statLines,
            imageUrl = song.albumArtUrl,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}
```

**Step 2: Wire into detectAndPersistNewMoments()**

```kotlin
newMoments += detectNarrativeTakeover()
newMoments += detectNarrativeSlowBuild()
newMoments += detectNarrativeBingeAndFade()
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add narrative detection — Takeover, Slow Build, Binge and Fade"
```

---

### Task 8: Add Narrative Detection Methods — Comeback Arcs (Full Circle, One That Got Away)

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Add detection methods**

```kotlin
private suspend fun detectNarrativeFullCircle(sevenDaysAgo: Long, now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val yearMonth = LocalDate.now(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-ww"))
    val allArtists = eventDao.getAllArtistsWithDurationSuspend()
    for (artistStats in allArtists) {
        val playsThisWeek = eventDao.getArtistPlayCountSinceSuspend(artistStats.artist, sevenDaysAgo)
        if (playsThisWeek < 10) continue

        val lastPlayBefore = eventDao.getArtistLastPlayedBeforeSuspend(artistStats.artist, sevenDaysAgo)
            ?: continue
        val gapDays = ((sevenDaysAgo - lastPlayBefore) / 86_400_000L).toInt()
        if (gapDays < 60) continue

        val type = "NARRATIVE_FULL_CIRCLE"
        val entityKey = "${artistStats.artist}:W$yearMonth"
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        val artist = artistDao.findByName(artistStats.artist) ?: continue
        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "gapDays" to gapDays,
            "playsThisWeek" to playsThisWeek
        )
        val copy = MomentCopywriter.generate(type, artistStats.artist, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            entityName = artistStats.artist,
            statLines = copy.statLines,
            imageUrl = artist.imageUrl,
            artistId = artist.id,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}

private suspend fun detectNarrativeOneGotAway(now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val thirtyDaysAgo = now - 30L * 86_400_000L
    val sixtyDaysAgo = now - 60L * 86_400_000L
    val oneYearAgo = now - 365L * 86_400_000L
    val currentMonth = LocalDate.now(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM"))

    // Get all artists and check their history
    val allArtists = eventDao.getAllArtistsWithDurationSuspend()
    for (artistStats in allArtists) {
        val type = "NARRATIVE_ONE_THAT_GOT_AWAY"
        val entityKey = "${artistStats.artist}:$currentMonth"
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        // Current plays must be <5
        val currentPlays = eventDao.getArtistPlayCountSinceSuspend(artistStats.artist, thirtyDaysAgo)
        if (currentPlays >= 5) continue

        // Check monthly history for a peak where artist was top 3
        val monthlyPlays = eventDao.getArtistMonthlyPlayCounts(artistStats.artist, oneYearAgo)
        if (monthlyPlays.isEmpty()) continue

        val peakMonth = monthlyPlays.maxByOrNull { it.playCount } ?: continue
        if (peakMonth.playCount < 15) continue // Needs to have been meaningfully listened to

        // Check the peak month was 60+ days ago (avoid natural cooldowns)
        // Parse peakMonth.monthKey "yyyy-MM" to approximate epoch
        val peakParts = peakMonth.monthKey.split("-")
        if (peakParts.size != 2) continue
        val peakEpoch = try {
            LocalDate.of(peakParts[0].toInt(), peakParts[1].toInt(), 15)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (_: Exception) { continue }
        if (peakEpoch > sixtyDaysAgo) continue

        // Verify artist was top 3 in that peak month
        val peakMonthStart = LocalDate.of(peakParts[0].toInt(), peakParts[1].toInt(), 1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val peakMonthEnd = LocalDate.of(peakParts[0].toInt(), peakParts[1].toInt(), 1)
            .plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val topInPeak = eventDao.getTopArtistsByPlayCountInPeriod(peakMonthStart, peakMonthEnd, 3)
        if (topInPeak.none { it.artist == artistStats.artist }) continue

        val artist = artistDao.findByName(artistStats.artist) ?: continue
        // Format peak month name nicely
        val peakMonthName = try {
            java.time.YearMonth.of(peakParts[0].toInt(), peakParts[1].toInt())
                .format(DateTimeFormatter.ofPattern("MMMM"))
        } catch (_: Exception) { peakMonth.monthKey }

        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "peakPlays" to peakMonth.playCount,
            "peakMonth" to peakMonthName,
            "currentPlays" to currentPlays
        )
        val copy = MomentCopywriter.generate(type, artistStats.artist, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            entityName = artistStats.artist,
            statLines = copy.statLines,
            imageUrl = artist.imageUrl,
            artistId = artist.id,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}
```

**Step 2: Wire into detectAndPersistNewMoments()**

```kotlin
newMoments += detectNarrativeFullCircle(sevenDaysAgo, now)
newMoments += detectNarrativeOneGotAway(now)
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add narrative detection — Full Circle, One That Got Away"
```

---

### Task 9: Add Narrative Detection Methods — Soundtrack, Rabbit Hole

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Add detection methods**

```kotlin
private suspend fun detectNarrativeSoundtrack(): List<Moment> {
    val result = mutableListOf<Moment>()
    val now = System.currentTimeMillis()
    val ninetyDaysMs = 90L * 86_400_000L
    // Get all songs — check for consistency
    val songs = eventDao.getSongsWithMinPlays(10)
    for (song in songs) {
        val type = "NARRATIVE_SOUNDTRACK"
        val entityKey = song.songId.toString()
        if (momentDao.existsByTypeAndKey(type, entityKey)) continue

        val age = now - song.firstHeardAt
        if (age < ninetyDaysMs) continue

        val distinctDays = eventDao.getDistinctDaysForSong(song.songId, song.firstHeardAt)
        if (distinctDays.size < 30) continue

        val monthSpan = (age / (30L * 86_400_000L)).toInt().coerceAtLeast(3)
        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "distinctDays" to distinctDays.size,
            "monthSpan" to monthSpan,
            "playCount" to song.playCount
        )
        val copy = MomentCopywriter.generate(type, song.title, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            entityName = song.artist,
            songId = song.songId,
            statLines = copy.statLines,
            imageUrl = song.albumArtUrl,
            copyVariant = copyVariant
        ))?.let { result += it }
    }
    return result
}

private suspend fun detectNarrativeRabbitHole(now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val twoDaysAgo = now - 2L * 86_400_000L
    val events = eventDao.getOrderedSongArtistEventsSuspend(twoDaysAgo)
    if (events.size < 8) return result

    // Group into sessions by 30-min gap, then find consecutive same-artist runs
    var i = 0
    while (i < events.size) {
        val runArtist = events[i].artist
        var runEnd = i
        var runDurationMs = events[i].durationMs

        for (j in i + 1 until events.size) {
            val gap = events[j].startedAt - (events[j - 1].startedAt + events[j - 1].durationMs)
            if (gap > 30 * 60 * 1000) break // session break
            if (events[j].artist != runArtist) break
            runEnd = j
            runDurationMs += events[j].durationMs
        }

        val runLength = runEnd - i + 1
        if (runLength >= 8) {
            // Determine the day for entity key
            val runDate = java.time.Instant.ofEpochMilli(events[i].startedAt)
                .atZone(ZoneId.systemDefault()).toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            val type = "NARRATIVE_RABBIT_HOLE"
            val entityKey = "$runArtist:$runDate"
            if (!momentDao.existsByTypeAndKey(type, entityKey)) {
                val artist = artistDao.findByName(runArtist)
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "songCount" to runLength,
                    "duration" to formatDuration(runDurationMs),
                    "noInterrupt" to "no interruptions"
                )
                val copy = MomentCopywriter.generate(type, runArtist, rawStats, copyVariant)
                persistIfNew(Moment(
                    type = type,
                    entityKey = entityKey,
                    triggeredAt = now,
                    title = copy.title,
                    description = copy.description,
                    entityName = runArtist,
                    statLines = copy.statLines,
                    imageUrl = artist?.imageUrl,
                    artistId = artist?.id,
                    copyVariant = copyVariant
                ))?.let { result += it }
            }
        }
        i = runEnd + 1
    }
    return result
}
```

**Step 2: Wire into detectAndPersistNewMoments()**

```kotlin
newMoments += detectNarrativeSoundtrack()
newMoments += detectNarrativeRabbitHole(now)
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add narrative detection — Soundtrack, Rabbit Hole"
```

---

### Task 10: Add Narrative Detection Methods — Contrast Arcs (Night and Day, Parallel Lives)

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Add detection methods**

```kotlin
private suspend fun detectNarrativeNightAndDay(thirtyDaysAgo: Long, now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val yearMonth = LocalDate.now(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val type = "NARRATIVE_NIGHT_AND_DAY"
    val entityKey = yearMonth
    if (momentDao.existsByTypeAndKey(type, entityKey)) return result

    // Day: 8am-8pm (hours 8-20), Night: 10pm-4am (hours 22-4, wraps)
    val dayArtists = eventDao.getTopArtistInTimeWindow(thirtyDaysAgo, 8, 20, 1)
    val nightArtists = eventDao.getTopArtistInTimeWindow(thirtyDaysAgo, 22, 4, 1)

    val dayTop = dayArtists.firstOrNull() ?: return result
    val nightTop = nightArtists.firstOrNull() ?: return result

    // Must be different artists, each with 5+ hours in their window
    if (dayTop.artist == nightTop.artist) return result
    if (dayTop.totalDurationMs < 5 * 3_600_000L) return result
    if (nightTop.totalDurationMs < 5 * 3_600_000L) return result

    val dayHours = dayTop.totalDurationMs / 3_600_000L
    val nightHours = nightTop.totalDurationMs / 3_600_000L
    val copyVariant = momentDao.countByType(type)
    val rawStats = mapOf<String, Any>(
        "dayArtist" to dayTop.artist,
        "nightArtist" to nightTop.artist,
        "dayLine" to "${dayTop.artist} (${dayHours}h)",
        "nightLine" to "${nightTop.artist} (${nightHours}h)"
    )
    val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
    persistIfNew(Moment(
        type = type,
        entityKey = entityKey,
        triggeredAt = now,
        title = copy.title,
        description = copy.description,
        statLines = copy.statLines,
        copyVariant = copyVariant
    ))?.let { result += it }
    return result
}

private suspend fun detectNarrativeParallelLives(thirtyDaysAgo: Long, now: Long): List<Moment> {
    val result = mutableListOf<Moment>()
    val yearMonth = LocalDate.now(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM"))

    // Get artists with 3+ hours in last 30 days
    val qualifying = eventDao.getTopArtistsByDurationSuspend(thirtyDaysAgo, 20)
        .filter { it.totalDurationMs >= 3 * 3_600_000L }
    if (qualifying.size < 2) return result

    // Build sessions from ordered events
    val events = eventDao.getOrderedSongArtistEventsSuspend(thirtyDaysAgo)
    if (events.isEmpty()) return result

    // Group events into sessions (30-min gap)
    val sessions = mutableListOf<Set<String>>() // each session = set of artist names
    var sessionArtists = mutableSetOf(events[0].artist)
    for (i in 1 until events.size) {
        val gap = events[i].startedAt - (events[i - 1].startedAt + events[i - 1].durationMs)
        if (gap > 30 * 60 * 1000) {
            sessions += sessionArtists.toSet()
            sessionArtists = mutableSetOf()
        }
        sessionArtists += events[i].artist
    }
    sessions += sessionArtists.toSet()

    // Check pairs of qualifying artists for zero co-occurrence
    for (i in qualifying.indices) {
        for (j in i + 1 until qualifying.size) {
            val a1 = qualifying[i]
            val a2 = qualifying[j]
            val sorted = listOf(a1.artist, a2.artist).sorted()
            val type = "NARRATIVE_PARALLEL_LIVES"
            val entityKey = "${sorted[0]}:${sorted[1]}:$yearMonth"
            if (momentDao.existsByTypeAndKey(type, entityKey)) continue

            val overlap = sessions.any { it.contains(a1.artist) && it.contains(a2.artist) }
            if (overlap) continue

            val hours1 = a1.totalDurationMs / 3_600_000L
            val hours2 = a2.totalDurationMs / 3_600_000L
            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "artist1" to sorted[0],
                "artist2" to sorted[1],
                "artist1Line" to "${sorted[0]}: ${hours1}h",
                "artist2Line" to "${sorted[1]}: ${hours2}h",
                "overlap" to "0 shared sessions"
            )
            val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = entityKey,
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                statLines = copy.statLines,
                copyVariant = copyVariant
            ))?.let { result += it }

            // Only fire one Parallel Lives per detection cycle
            if (result.isNotEmpty()) return result
        }
    }
    return result
}
```

**Step 2: Wire into detectAndPersistNewMoments()**

```kotlin
newMoments += detectNarrativeNightAndDay(thirtyDaysAgo, now)
newMoments += detectNarrativeParallelLives(thirtyDaysAgo, now)
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add narrative detection — Night and Day, Parallel Lives"
```

---

### Task 11: Add Narrative Preview Moments to AllMomentsViewModel

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt`

**Step 1: Add narrative preview moments**

Add at the end of `buildPreviewMoments()`, before `return result`:

```kotlin
// ── Narrative Moments (12) ─────────────────────────────

fun narrativeMoment(id: Long, type: String, entityName: String?, rawStats: Map<String, Any>,
                    songId: Long? = null, imageUrl: String? = null): Moment {
    val copy = MomentCopywriter.generate(type, entityName, rawStats, 0)
    return Moment(
        id = id, type = type, entityKey = "preview",
        triggeredAt = now,
        title = copy.title,
        description = copy.description,
        entityName = entityName,
        songId = songId,
        statLines = copy.statLines,
        imageUrl = imageUrl,
        copyVariant = 0
    )
}

result += narrativeMoment(-90, "NARRATIVE_ORIGIN_STORY", a0?.name ?: "Radiohead",
    mapOf("firstSong" to (s0?.title ?: "Everything In Its Right Place"), "rank" to 3, "daysAgo" to 47, "uniqueSongs" to 9, "totalHours" to 18L),
    imageUrl = a0?.imageUrl ?: artistArt)
result += narrativeMoment(-91, "NARRATIVE_GATEWAY", a0?.name ?: "Arctic Monkeys",
    mapOf("firstSong" to (s0?.title ?: "Do I Wanna Know?"), "topSong" to (s1?.title ?: "Fluorescent Adolescent"), "ratio" to 3, "topSongLine" to "${s1?.title ?: "Fluorescent Adolescent"} (${s1?.playCount ?: 84} plays)"),
    imageUrl = a0?.imageUrl ?: artistArt)
result += narrativeMoment(-92, "NARRATIVE_COLLECTION", a0?.name ?: "Arctic Monkeys",
    mapOf("songCount" to 23, "firstSong" to (s0?.title ?: "Do I Wanna Know?"), "totalHours" to 31L, "daysAgo" to 120),
    imageUrl = a0?.imageUrl ?: artistArt)
result += narrativeMoment(-93, "NARRATIVE_TAKEOVER", s1?.title ?: "Espresso",
    mapOf("rank" to 4, "playCount" to (s1?.playCount ?: 67), "daysAgo" to 21),
    songId = s1?.songId, imageUrl = s1?.albumArtUrl ?: songArt)
result += narrativeMoment(-94, "NARRATIVE_SLOW_BUILD", s0?.title ?: "a song",
    mapOf("w1" to 2, "w2" to 5, "w3" to 11, "w4" to 19, "trajectory" to "2 → 5 → 11 → 19 plays"),
    songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
result += narrativeMoment(-95, "NARRATIVE_BINGE_AND_FADE", s2?.title ?: "Heat Waves",
    mapOf("bingePlays" to 47, "fadePlays" to 2),
    songId = s2?.songId, imageUrl = s2?.albumArtUrl ?: songArt)
result += narrativeMoment(-96, "NARRATIVE_FULL_CIRCLE", a1?.name ?: "Daft Punk",
    mapOf("gapDays" to 73, "playsThisWeek" to 12),
    imageUrl = a1?.imageUrl ?: artistArt)
result += narrativeMoment(-97, "NARRATIVE_ONE_THAT_GOT_AWAY", a1?.name ?: "Tyler, The Creator",
    mapOf("peakPlays" to 34, "peakMonth" to "February", "currentPlays" to 2),
    imageUrl = a1?.imageUrl ?: artistArt)
result += narrativeMoment(-98, "NARRATIVE_SOUNDTRACK", s0?.title ?: "Blinding Lights",
    mapOf("distinctDays" to 47, "monthSpan" to 6, "playCount" to (s0?.playCount ?: 89)),
    songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
result += narrativeMoment(-99, "NARRATIVE_RABBIT_HOLE", a0?.name ?: "Radiohead",
    mapOf("songCount" to 14, "duration" to "58m", "noInterrupt" to "no interruptions"),
    imageUrl = a0?.imageUrl ?: artistArt)
result += narrativeMoment(-100, "NARRATIVE_NIGHT_AND_DAY", null,
    mapOf("dayArtist" to (a0?.name ?: "Taylor Swift"), "nightArtist" to (a1?.name ?: "Bon Iver"),
        "dayLine" to "${a0?.name ?: "Taylor Swift"} (12h)", "nightLine" to "${a1?.name ?: "Bon Iver"} (7h)"))
result += narrativeMoment(-101, "NARRATIVE_PARALLEL_LIVES", null,
    mapOf("artist1" to (a0?.name ?: "Radiohead"), "artist2" to (a1?.name ?: "Coldplay"),
        "artist1Line" to "${a0?.name ?: "Radiohead"}: 8h", "artist2Line" to "${a1?.name ?: "Coldplay"}: 5h",
        "overlap" to "0 shared sessions"))
```

**Step 2: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt
git commit -m "feat: add preview moments for 12 narrative types"
```

---

### Task 12: Share Card Visual Refresh — Typography & Composition

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt`

**Step 1: Update MomentShareCard typography and spacing**

Apply the design doc requirements: larger bolder title, better line height for description, more padding, subtler branding, description maxLines bumped to 4 for narrative moments.

In `MomentShareCard.kt`, update the text section:

```kotlin
// Layer 3: text anchored to bottom
Column(
    modifier = Modifier
        .fillMaxWidth()
        .align(Alignment.BottomStart)
        .padding(horizontal = 32.dp, vertical = 36.dp)  // was 28/32
) {
    if (moment.entityName != null) {
        Text(
            text = moment.entityName,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.60f),
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))  // was 4
    }
    Text(
        text = moment.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,  // was Bold
        color = Color.White,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(Modifier.height(10.dp))  // was 8
    Text(
        text = moment.description,
        style = MaterialTheme.typography.titleMedium.copy(
            lineHeight = 24.sp  // explicit line height for readability
        ),
        color = Color.White.copy(alpha = 0.75f),  // was 0.70
        maxLines = 4,  // was 3 — narrative moments need more room
        overflow = TextOverflow.Ellipsis
    )
```

Update pill spacing:

```kotlin
    if (moment.statLines.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))  // was 16
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
```

Update branding row — make date and brand more subtle:

```kotlin
    Spacer(Modifier.height(18.dp))  // was 16
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateStr,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.30f)  // was 0.40
        )
        Text(
            text = "vibes",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),  // was 2
            color = Color.White.copy(alpha = 0.25f)  // was 0.35
        )
    }
```

Also reduce blur slightly for more image texture:

```kotlin
.blur(16.dp)  // was 20.dp
```

And increase scrim slightly to compensate:

```kotlin
Color.Black.copy(alpha = 0.80f)  // was 0.75
```

**Step 2: Apply similar polish to MomentCard.kt**

Update the in-app card to match: bump description maxLines to 4, same fontWeight tweaks.

In MomentCard.kt, update title:

```kotlin
Text(
    text = moment.title,
    style = MaterialTheme.typography.titleLarge,
    fontWeight = FontWeight.ExtraBold,  // was Bold
    color = Color.White,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis
)
```

And description:

```kotlin
Text(
    text = moment.description,
    style = MaterialTheme.typography.bodyMedium,
    color = Color.White.copy(alpha = 0.75f),  // was 0.70
    maxLines = 4,  // was 3
    overflow = TextOverflow.Ellipsis
)
```

**Step 3: Verify build compiles**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt \
    app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt
git commit -m "feat: share card visual refresh — typography, spacing, composition"
```

---

### Task 13: Share Card Visual Refresh — Palette-Driven Colors

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt`
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt`

**Step 1: Use palette colors for pill backgrounds and scrim tint**

The `LocalAlbumPalette` already provides an accent color. For cards with images, tint the scrim and pills with a hint of the palette accent instead of pure white/black.

In both `MomentShareCard.kt` and `MomentCard.kt`, update the scrim gradient to include a subtle palette tint:

```kotlin
// Layer 2: scrim — tinted with palette accent
Box(
    modifier = Modifier
        .matchParentSize() // or .fillMaxSize() for share card
        .background(
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    palette.accent.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.80f)  // share card: 0.80, in-app: 0.70
                )
            )
        )
)
```

Update pill backgrounds to use a subtle palette tint:

```kotlin
.background(palette.accent.copy(alpha = 0.18f))  // was Color.White.copy(alpha = 0.15f)
```

**Step 2: Verify build compiles and install**

Run: `./gradlew installDebug`
Expected: BUILD SUCCESSFUL, installs on device

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt \
    app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt
git commit -m "feat: palette-driven colors for share card scrim and pills"
```

---

### Task 14: Final Build Verification and Install

**Step 1: Run full build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 2: Install and test on device**

Run: `./gradlew installDebug`
Expected: App installs, moments screen loads, preview tab shows all moment types including narratives, share cards render with updated styling

**Step 3: Verify key scenarios manually**

- Open All Moments → Preview tab. Confirm: pruned types (SONG_PLAYS_10, etc.) are gone, 12 narrative cards appear with story-driven copy.
- Tap a narrative card → bottom sheet shows title + long description.
- Share a narrative card → share card renders with updated typography, palette colors, and 4-line description.
- Share a regular milestone card → same visual refresh applies.

**Step 4: Final commit if any fixups needed**

```bash
git add -A && git commit -m "fix: address any post-integration issues"
```
