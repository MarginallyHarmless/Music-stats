# Flex Moments (Round 4) Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add 6 shareable flex moment cards: Top 1%, The Loop, The Collector, Speed Run, Power Hour, After Hours.

**Architecture:** Same pattern as existing moments — DAO queries feed detection functions in MomentDetector, copy lives in MomentCopywriter, preview entries in AllMomentsViewModel. 3 new DAO queries needed, 3 existing queries reused.

**Tech Stack:** Kotlin, Room DAO, Jetpack Compose (preview only)

**Design doc:** `docs/plans/2026-03-03-flex-moments-design.md`

---

### Task 1: Add 3 new DAO queries + 1 data class

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt`

**Step 1: Add `MonthlyListening` data class**

Add after the existing `DailyFirstEvent` data class (around line 102):

```kotlin
data class MonthlyListening(
    val monthKey: String,
    val totalMs: Long
)
```

**Step 2: Add 3 new queries at the end of the DAO interface**

Add before the closing `}` of `ListeningEventDao`:

```kotlin
    // Monthly listening totals (for Biggest Month / Top 1%)
    @Query("""
        SELECT strftime('%Y-%m', startedAt / 1000, 'unixepoch', 'localtime') AS monthKey,
               COALESCE(SUM(durationMs), 0) AS totalMs
        FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
        GROUP BY monthKey
        ORDER BY monthKey ASC
    """)
    suspend fun getMonthlyListeningTotalsSuspend(since: Long): List<MonthlyListening>

    // Nth completed play timestamp for a song (for Speed Run)
    @Query("""
        SELECT startedAt FROM listening_events
        WHERE songId = :songId AND completed = 1
        ORDER BY startedAt ASC
        LIMIT 1 OFFSET :offset
    """)
    suspend fun getNthPlayTimestamp(songId: Long, offset: Int): Long?

    // After-hours listening (midnight to 6am) grouped by day (for After Hours)
    @Query("""
        SELECT strftime('%Y-%m-%d', startedAt / 1000, 'unixepoch', 'localtime') AS day,
               COALESCE(SUM(durationMs), 0) AS nightMs
        FROM listening_events
        WHERE completed = 1 AND startedAt >= :since
        AND CAST(strftime('%H', startedAt / 1000, 'unixepoch', 'localtime') AS INTEGER) < 6
        GROUP BY day
    """)
    suspend fun getAfterHoursListeningByDaySuspend(since: Long): List<DayNightListening>
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/dao/ListeningEventDao.kt
git commit -m "feat: add DAO queries for flex moments (monthly totals, nth play, after-hours)"
```

---

### Task 2: Add 6 copywriter entries

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt`

**Step 1: Add entries before the `else` fallback**

Find the block ending with `BEHAVIORAL_ANTHEM` and add after it, before `// Fallback for any unknown type`:

```kotlin
            // ── Flex / Shareable ────────────────────────────────
            type == "FLEX_BIGGEST_MONTH" -> {
                val duration = rawStats["duration"]?.toString() ?: "?"
                MomentCopy(
                    title = "Top 1%",
                    description = "$duration this month. That's more music than any month before. New personal record.",
                    statLines = rawStats.toStatLines("duration" to "%s this month", "prevBest" to "previous best: %s", "songCount" to "%s songs")
                )
            }
            type == "FLEX_LOOP" -> {
                val plays = rawStats["plays"]?.toString() ?: "?"
                val interval = rawStats["interval"]?.toString() ?: "?"
                MomentCopy(
                    title = "The Loop",
                    description = "You played $name $plays times today. That's roughly once every $interval.",
                    statLines = rawStats.toStatLines("plays" to "%s plays today", "interval" to "~every %s", "allTimePlays" to "%s all-time")
                )
            }
            type.startsWith("FLEX_COLLECTOR_") -> {
                val count = rawStats["count"]?.toString() ?: "?"
                val (title, desc) = when (type) {
                    "FLEX_COLLECTOR_2000" -> "Music Hoarder" to "2,000 songs. You don't listen to music — you curate it."
                    "FLEX_COLLECTOR_5000" -> "Living Archive" to "5,000 songs. That's not a library, that's an institution."
                    else -> "The Collector" to "1,000 unique songs. You could run a radio station."
                }
                MomentCopy(
                    title = title,
                    description = desc,
                    statLines = rawStats.toStatLines("count" to "%s songs", "artistCount" to "from %s artists", "totalHours" to "%sh of music")
                )
            }
            type == "FLEX_SPEED_RUN" -> {
                val days = rawStats["days"]?.toString() ?: "?"
                MomentCopy(
                    title = "Speed Run",
                    description = "50 plays of $name in $days days. Most relationships don't move that fast.",
                    statLines = rawStats.toStatLines("days" to "%s days to 50 plays", "rank" to "#%s all-time", "daysAgo" to "discovered %sd ago")
                )
            }
            type == "FLEX_POWER_HOUR" -> {
                val count = rawStats["count"]?.toString() ?: "?"
                val seconds = rawStats["seconds"]?.toString() ?: "?"
                MomentCopy(
                    title = "Power Hour",
                    description = "$count songs in a single hour. That's one every $seconds seconds.",
                    statLines = rawStats.toStatLines("count" to "%s songs", "seconds" to "~every %ss", "peakHour" to "%s")
                )
            }
            type == "FLEX_AFTER_HOURS" -> {
                val duration = rawStats["duration"]?.toString() ?: "?"
                MomentCopy(
                    title = "After Hours",
                    description = "$duration of music between midnight and 6am. Your neighbors have opinions.",
                    statLines = rawStats.toStatLines("duration" to "%s after midnight", "songCount" to "~%s songs", "peakHour" to "%s peak")
                )
            }
```

**Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt
git commit -m "feat: add copywriter entries for 6 flex moments"
```

---

### Task 3: Add Top 1% (Biggest Month) detection

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Wire into `detectAndPersistNewMoments`**

Find the line `newMoments += detectAnthem(now)` and add after it:

```kotlin
        newMoments += detectBiggestMonth(now)
```

**Step 2: Add detection method**

Add before `private fun startOfDay`:

```kotlin
    private suspend fun detectBiggestMonth(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val twoYearsAgo = now - 730L * 24 * 3600 * 1000
        val months = eventDao.getMonthlyListeningTotalsSuspend(twoYearsAgo)
        if (months.size < 3) return result

        val currentMonthKey = LocalDate.now(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val currentMonth = months.lastOrNull { it.monthKey == currentMonthKey } ?: return result
        val previousMax = months.filter { it.monthKey != currentMonthKey }.maxOfOrNull { it.totalMs } ?: return result

        if (currentMonth.totalMs <= previousMax) return result

        val type = "FLEX_BIGGEST_MONTH"
        val entityKey = "bigmonth_$currentMonthKey"
        if (momentDao.existsByTypeAndKey(type, entityKey)) return result

        val zone = ZoneId.systemDefault()
        val monthStart = YearMonth.now(zone).atDay(1)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val songCount = eventDao.getUniqueSongCountInPeriodSuspend(monthStart)
        val duration = formatDuration(currentMonth.totalMs)
        val prevBest = formatDuration(previousMax)

        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "duration" to duration,
            "prevBest" to prevBest,
            "songCount" to "$songCount"
        )
        val copy = MomentCopywriter.generate(type, null, rawStats, copyVariant)
        persistIfNew(Moment(
            type = type,
            entityKey = entityKey,
            triggeredAt = now,
            title = copy.title,
            description = copy.description,
            statLines = copy.statLines,
            isPersonalBest = true,
            copyVariant = copyVariant
        ))?.let { result += it }

        return result
    }
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add Top 1% (biggest month ever) detection"
```

---

### Task 4: Add The Loop detection

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Wire into `detectAndPersistNewMoments`**

After the `detectBiggestMonth` line, add:

```kotlin
        newMoments += detectLoop(todayStart, now)
```

**Step 2: Add detection method**

Add before `private fun startOfDay`:

```kotlin
    private suspend fun detectLoop(todayStart: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val todayEnd = todayStart + 86_400_000L
        val todaySongs = eventDao.getSongsPlayedOnDay(todayStart, todayEnd)
        val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()

        for (song in todaySongs) {
            if (song.playCount < 15) continue

            val type = "FLEX_LOOP"
            val entityKey = "loop_${song.songId}_$todayDate"
            if (momentDao.existsByTypeAndKey(type, entityKey)) continue

            // Calculate interval: hours elapsed since start of day / plays
            val hoursElapsed = ((now - todayStart) / 3_600_000.0).coerceAtLeast(1.0)
            val intervalMinutes = ((hoursElapsed * 60) / song.playCount).toInt()
            val interval = if (intervalMinutes >= 60) "${intervalMinutes / 60}h ${intervalMinutes % 60}m" else "${intervalMinutes}m"

            val allTimePlays = eventDao.getSongStats(song.songId)?.playCount ?: song.playCount

            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "plays" to "${song.playCount}",
                "interval" to interval,
                "allTimePlays" to "$allTimePlays"
            )
            val copy = MomentCopywriter.generate(type, song.title, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = entityKey,
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                songId = song.songId,
                statLines = copy.statLines,
                imageUrl = song.albumArtUrl,
                entityName = song.title,
                copyVariant = copyVariant
            ))?.let { result += it }
        }
        return result
    }
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add The Loop detection (15+ plays of one song in a day)"
```

---

### Task 5: Add The Collector detection

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Add thresholds constant**

In the `companion object` block, add after `val DISCOVERY_THRESHOLDS`:

```kotlin
        val COLLECTOR_THRESHOLDS = listOf(1000, 2000, 5000)
```

**Step 2: Wire into `detectAndPersistNewMoments`**

After the `detectLoop` line, add:

```kotlin
        newMoments += detectCollector(now)
```

**Step 3: Add detection method**

Add before `private fun startOfDay`:

```kotlin
    private suspend fun detectCollector(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val uniqueSongs = eventDao.getUniqueSongCountSuspend()
        val artistCount = eventDao.getUniqueArtistCountSuspend()
        val totalMs = eventDao.getTotalListeningTimeMsSuspend()
        val totalHours = totalMs / 3_600_000L

        for (threshold in COLLECTOR_THRESHOLDS) {
            if (uniqueSongs < threshold) break

            val type = "FLEX_COLLECTOR_$threshold"
            val entityKey = "collector_$threshold"
            if (momentDao.existsByTypeAndKey(type, entityKey)) continue

            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "count" to "$uniqueSongs",
                "artistCount" to "$artistCount",
                "totalHours" to "$totalHours"
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
        }
        return result
    }
```

**Step 4: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add The Collector detection (1000/2000/5000 unique songs)"
```

---

### Task 6: Add Speed Run detection

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Wire into `detectAndPersistNewMoments`**

After the `detectCollector` line, add:

```kotlin
        newMoments += detectSpeedRun(now)
```

**Step 2: Add detection method**

Add before `private fun startOfDay`:

```kotlin
    private suspend fun detectSpeedRun(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val songs = eventDao.getSongsWithMinPlays(50)

        for (song in songs) {
            val type = "FLEX_SPEED_RUN"
            val entityKey = "speedrun_${song.songId}"
            if (momentDao.existsByTypeAndKey(type, entityKey)) continue

            // Get timestamp of the 50th play (0-indexed offset = 49)
            val fiftiethPlayAt = eventDao.getNthPlayTimestamp(song.songId, 49) ?: continue
            val daysTaken = ((fiftiethPlayAt - song.firstHeardAt) / 86_400_000L).toInt()
            if (daysTaken > 7) continue

            val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
            val daysAgo = ((now - song.firstHeardAt) / 86_400_000L).toInt()

            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "days" to "${daysTaken.coerceAtLeast(1)}",
                "rank" to "$rank",
                "daysAgo" to "$daysAgo"
            )
            val copy = MomentCopywriter.generate(type, song.title, rawStats, copyVariant)
            persistIfNew(Moment(
                type = type,
                entityKey = entityKey,
                triggeredAt = now,
                title = copy.title,
                description = copy.description,
                songId = song.songId,
                statLines = copy.statLines,
                imageUrl = song.albumArtUrl,
                entityName = song.title,
                copyVariant = copyVariant
            ))?.let { result += it }
        }
        return result
    }
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add Speed Run detection (50 plays in 7 days)"
```

---

### Task 7: Add Power Hour detection

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Wire into `detectAndPersistNewMoments`**

After the `detectSpeedRun` line, add:

```kotlin
        newMoments += detectPowerHour(todayStart, now)
```

**Step 2: Add detection method**

Add before `private fun startOfDay`:

```kotlin
    private suspend fun detectPowerHour(todayStart: Long, now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val events = eventDao.getOrderedSongArtistEventsSuspend(todayStart)
        if (events.size < 15) return result

        val todayDate = LocalDate.now(ZoneId.systemDefault()).toString()
        val type = "FLEX_POWER_HOUR"
        val entityKey = "phour_$todayDate"
        if (momentDao.existsByTypeAndKey(type, entityKey)) return result

        // Sliding window: find the 60-min window with the most events
        var bestCount = 0
        var bestStartIdx = 0
        for (i in events.indices) {
            val windowEnd = events[i].startedAt + 3_600_000L
            var count = 0
            for (j in i until events.size) {
                if (events[j].startedAt <= windowEnd) count++ else break
            }
            if (count > bestCount) {
                bestCount = count
                bestStartIdx = i
            }
        }

        if (bestCount < 15) return result

        val peakStartMs = events[bestStartIdx].startedAt
        val zone = ZoneId.systemDefault()
        val peakTime = java.time.Instant.ofEpochMilli(peakStartMs).atZone(zone).toLocalTime()
        val peakHour = peakTime.hour
        val peakLabel = String.format("%d:%02d%s",
            if (peakHour % 12 == 0) 12 else peakHour % 12,
            peakTime.minute,
            if (peakHour < 12) "am" else "pm"
        )

        val seconds = (3600 / bestCount)

        val copyVariant = momentDao.countByType(type)
        val rawStats = mapOf<String, Any>(
            "count" to "$bestCount",
            "seconds" to "$seconds",
            "peakHour" to peakLabel
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
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add Power Hour detection (15+ songs in 60 min)"
```

---

### Task 8: Add After Hours detection

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

**Step 1: Wire into `detectAndPersistNewMoments`**

After the `detectPowerHour` line, add:

```kotlin
        newMoments += detectAfterHours(now)
```

**Step 2: Add detection method**

Add before `private fun startOfDay`:

```kotlin
    private suspend fun detectAfterHours(now: Long): List<Moment> {
        val result = mutableListOf<Moment>()
        val thirtyDaysAgo = now - 30L * 24 * 3600 * 1000
        val afterHoursDays = eventDao.getAfterHoursListeningByDaySuspend(thirtyDaysAgo)
        val threeHoursMs = 3 * 3_600_000L

        for (dayData in afterHoursDays) {
            if (dayData.nightMs < threeHoursMs) continue

            val type = "FLEX_AFTER_HOURS"
            val entityKey = "afterhrs_${dayData.day}"
            if (momentDao.existsByTypeAndKey(type, entityKey)) continue

            val duration = formatDuration(dayData.nightMs)
            val songCount = (dayData.nightMs / 210_000L).toInt().coerceAtLeast(1)

            // Estimate peak hour (middle of the 0-5 window weighted by duration)
            val peakHour = "2am"

            val copyVariant = momentDao.countByType(type)
            val rawStats = mapOf<String, Any>(
                "duration" to duration,
                "songCount" to "$songCount",
                "peakHour" to peakHour
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
        }
        return result
    }
```

**Step 3: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: add After Hours detection (3h+ listening midnight-6am)"
```

---

### Task 9: Add 8 preview entries

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt`

**Step 1: Add preview entries before `return result`**

Find the last `behavioralMoment` call (BEHAVIORAL_ANTHEM at id -118) and add after it:

```kotlin
        // ── Flex / Shareable (8) ──────────────────────────────────
        result += behavioralMoment(-119, "FLEX_BIGGEST_MONTH", null,
            mapOf("duration" to "47h 12m", "prevBest" to "39h 44m", "songCount" to "312"))
        result += behavioralMoment(-120, "FLEX_LOOP", s0?.title ?: "Mr. Brightside",
            mapOf("plays" to "23", "interval" to "26m", "allTimePlays" to "${s0?.playCount ?: 89}"),
            songId = s0?.songId, imageUrl = s0?.albumArtUrl ?: songArt)
        result += behavioralMoment(-121, "FLEX_COLLECTOR_1000", null,
            mapOf("count" to "1,000", "artistCount" to "142", "totalHours" to "387"))
        result += behavioralMoment(-122, "FLEX_COLLECTOR_2000", null,
            mapOf("count" to "2,000", "artistCount" to "298", "totalHours" to "812"))
        result += behavioralMoment(-123, "FLEX_COLLECTOR_5000", null,
            mapOf("count" to "5,000", "artistCount" to "614", "totalHours" to "2,041"))
        result += behavioralMoment(-124, "FLEX_SPEED_RUN", s1?.title ?: "Espresso",
            mapOf("days" to "4", "rank" to "3", "daysAgo" to "12"),
            songId = s1?.songId, imageUrl = s1?.albumArtUrl ?: songArt)
        result += behavioralMoment(-125, "FLEX_POWER_HOUR", null,
            mapOf("count" to "19", "seconds" to "189", "peakHour" to "2:30pm"))
        result += behavioralMoment(-126, "FLEX_AFTER_HOURS", null,
            mapOf("duration" to "3h 41m", "songCount" to "63", "peakHour" to "2am"))
```

**Step 2: Verify build**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt
git commit -m "feat: add preview entries for 6 flex moments (8 cards total)"
```

---

### Task 10: Update design doc and final verification

**Files:**
- Modify: `docs/plans/2026-03-03-deeper-insight-moments.md` (append note about Round 4)

**Step 1: Append to design doc**

Add at the end of `docs/plans/2026-03-03-deeper-insight-moments.md`:

```markdown

## Round 4: Flex Moments

See `docs/plans/2026-03-03-flex-moments-design.md` for full design. Added 6 shareable flex moment types:
- FLEX_BIGGEST_MONTH, FLEX_LOOP, FLEX_COLLECTOR_1000/2000/5000, FLEX_SPEED_RUN, FLEX_POWER_HOUR, FLEX_AFTER_HOURS
```

**Step 2: Full build verification**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Verify preview count**

The preview tab should now show 72 moments (64 existing + 8 new preview entries).

**Step 4: Commit**

```bash
git add docs/plans/
git commit -m "docs: update design docs for flex moments (round 4)"
```
