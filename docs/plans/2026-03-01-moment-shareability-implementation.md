# Moment Cards Shareability Redesign — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make moment cards share-worthy with playful copy, bronze/silver/gold visual tiers, and personal-record context.

**Architecture:** Add `MomentTier` enum and `MomentCopywriter` as pure Kotlin objects. Extend `Moment` entity with 3 new fields. Update `MomentDetector` to use the copywriter and tier system. Update `MomentCard`/`MomentShareCard` composables with tier-aware visuals.

**Tech Stack:** Kotlin, Room (migration 14→15), Jetpack Compose, Material 3

---

### Task 1: Add MomentTier enum

**Files:**
- Create: `app/src/main/java/com/musicstats/app/data/model/MomentTier.kt`

**Step 1: Create the MomentTier enum**

```kotlin
package com.musicstats.app.data.model

enum class MomentTier {
    BRONZE, SILVER, GOLD;

    companion object {
        fun tierFor(type: String): MomentTier = when (type) {
            // Bronze: entry-level milestones, common behaviors
            "SONG_PLAYS_10", "SONG_PLAYS_25",
            "ARTIST_HOURS_1",
            "STREAK_3", "STREAK_7",
            "TOTAL_HOURS_24",
            "SONGS_DISCOVERED_50",
            "OBSESSION_DAILY", "COMFORT_ZONE", "DAILY_RITUAL" -> BRONZE

            // Gold: genuinely impressive, flex-worthy
            "SONG_PLAYS_250", "SONG_PLAYS_500",
            "ARTIST_HOURS_24",
            "STREAK_100",
            "TOTAL_HOURS_500", "TOTAL_HOURS_1000",
            "SONGS_DISCOVERED_500",
            "MARATHON_WEEK", "LONGEST_SESSION", "SLOW_BURN", "RESURRECTION" -> GOLD

            // Silver: everything else (meaningful achievements, all archetypes, notable behaviors)
            else -> SILVER
        }

        /** Personal best bumps tier up one level. */
        fun bumped(tier: MomentTier): MomentTier = when (tier) {
            BRONZE -> SILVER
            SILVER -> GOLD
            GOLD -> GOLD
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/model/MomentTier.kt
git commit -m "feat: add MomentTier enum with type-to-tier mapping"
```

---

### Task 2: Add MomentCopywriter

**Files:**
- Create: `app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt`

This is the largest single file — it contains all the cheeky copy for every moment type. The copywriter is a pure object with no dependencies.

**Step 1: Create the MomentCopywriter**

```kotlin
package com.musicstats.app.service

import com.musicstats.app.util.formatDuration

data class MomentCopy(
    val title: String,
    val description: String,
    val statLines: List<String>
)

/**
 * Generates playful, share-worthy copy for moments.
 * All copy lives here in one place for easy iteration.
 */
object MomentCopywriter {

    /**
     * @param type          moment type string e.g. "SONG_PLAYS_100"
     * @param entityName    artist or song name (nullable)
     * @param rawStats      map of raw stat values from the detector, keys vary by type:
     *                      - "playCount", "totalDurationMs", "rank", "threshold"
     *                      - "skipRate", "skipCount", "totalPlays"
     *                      - "streakDays", "avgMinPerDay", "uniqueSongs"
     *                      - "nightPct", "peakLabel", "gapDays", "ageDays"
     *                      - "sessionDuration", "weekDuration"
     *                      - "newArtists", "newSongs", "top5Pct"
     *                      etc.
     * @param copyVariant   how many previous moments of this type exist (0 = first time)
     */
    fun generate(
        type: String,
        entityName: String?,
        rawStats: Map<String, Any>,
        copyVariant: Int = 0
    ): MomentCopy {
        val name = entityName ?: "this"

        return when {
            // ── Song Play Milestones ────────────────────────────
            type == "SONG_PLAYS_10" -> MomentCopy(
                title = "On Repeat",
                description = "10 plays and counting — this one's got its hooks in you.",
                statLines = buildSongMilestoneStats(rawStats)
            )
            type == "SONG_PLAYS_25" -> MomentCopy(
                title = "Can't Stop",
                description = "25 plays. You and $name have something going on.",
                statLines = buildSongMilestoneStats(rawStats)
            )
            type == "SONG_PLAYS_50" -> MomentCopy(
                title = "Ride or Die",
                description = "50 plays. This is a committed relationship at this point.",
                statLines = buildSongMilestoneStats(rawStats)
            )
            type == "SONG_PLAYS_100" -> MomentCopy(
                title = "Certified Obsession",
                description = "100 plays. At this point, $name owes you royalties.",
                statLines = buildSongMilestoneStats(rawStats)
            )
            type == "SONG_PLAYS_250" -> MomentCopy(
                title = "Down Bad",
                description = "250 plays. This isn't casual anymore.",
                statLines = buildSongMilestoneStats(rawStats)
            )
            type == "SONG_PLAYS_500" -> MomentCopy(
                title = "Beyond Help",
                description = "500 plays. This isn't a song anymore, it's a lifestyle.",
                statLines = buildSongMilestoneStats(rawStats)
            )

            // ── Artist Hour Milestones ──────────────────────────
            type == "ARTIST_HOURS_1" -> MomentCopy(
                title = "First Hour",
                description = "A whole hour of $name. The beginning of something.",
                statLines = buildArtistHourStats(rawStats)
            )
            type == "ARTIST_HOURS_5" -> MomentCopy(
                title = "Getting Serious",
                description = "5 hours of $name. That's a long first date.",
                statLines = buildArtistHourStats(rawStats)
            )
            type == "ARTIST_HOURS_10" -> MomentCopy(
                title = "Dedicated Fan",
                description = "10 hours of $name. You could teach a masterclass.",
                statLines = buildArtistHourStats(rawStats)
            )
            type == "ARTIST_HOURS_24" -> MomentCopy(
                title = "A Full Day",
                description = "24 hours of $name. A literal day of your life. No regrets.",
                statLines = buildArtistHourStats(rawStats)
            )

            // ── Streak Milestones ───────────────────────────────
            type == "STREAK_3" -> MomentCopy(
                title = "Hat Trick",
                description = "3 days in a row. The streak begins.",
                statLines = buildStreakStats(rawStats)
            )
            type == "STREAK_7" -> MomentCopy(
                title = "Full Week",
                description = "7 days straight. Music every single day.",
                statLines = buildStreakStats(rawStats)
            )
            type == "STREAK_14" -> MomentCopy(
                title = "Two Weeks Strong",
                description = "14 days. Most habits don't last this long.",
                statLines = buildStreakStats(rawStats)
            )
            type == "STREAK_30" -> MomentCopy(
                title = "Unstoppable",
                description = "30 days straight. That's a whole month without silence.",
                statLines = buildStreakStats(rawStats)
            )
            type == "STREAK_100" -> MomentCopy(
                title = "Legendary",
                description = "100-day streak. At this point, silence is the anomaly.",
                statLines = buildStreakStats(rawStats)
            )

            // ── Total Hour Milestones ───────────────────────────
            type == "TOTAL_HOURS_24" -> MomentCopy(
                title = "First Full Day",
                description = "24 hours of music total. That's a whole day of your life — well spent.",
                statLines = buildTotalHourStats(rawStats)
            )
            type == "TOTAL_HOURS_100" -> MomentCopy(
                title = "Triple Digits",
                description = "100 hours. That's longer than binging every season of a show.",
                statLines = buildTotalHourStats(rawStats)
            )
            type == "TOTAL_HOURS_500" -> MomentCopy(
                title = "Half a Thousand",
                description = "500 hours of music. That's almost 21 full days of nonstop listening.",
                statLines = buildTotalHourStats(rawStats)
            )
            type == "TOTAL_HOURS_1000" -> MomentCopy(
                title = "The Thousand Hour Club",
                description = "1,000 hours. You've spent over 41 days of your life listening to music.",
                statLines = buildTotalHourStats(rawStats)
            )

            // ── Discovery Milestones ────────────────────────────
            type == "SONGS_DISCOVERED_50" -> MomentCopy(
                title = "50 Songs Deep",
                description = "Your library is growing. 50 unique songs and counting.",
                statLines = buildDiscoveryStats(rawStats)
            )
            type == "SONGS_DISCOVERED_100" -> MomentCopy(
                title = "Century Club",
                description = "100 unique songs. Your taste is wider than you think.",
                statLines = buildDiscoveryStats(rawStats)
            )
            type == "SONGS_DISCOVERED_250" -> MomentCopy(
                title = "Music Collector",
                description = "250 songs. That's enough to fill a road trip playlist 10 times over.",
                statLines = buildDiscoveryStats(rawStats)
            )
            type == "SONGS_DISCOVERED_500" -> MomentCopy(
                title = "Walking Jukebox",
                description = "500 songs. You could soundtrack an entire year without repeating.",
                statLines = buildDiscoveryStats(rawStats)
            )

            // ── Archetypes ──────────────────────────────────────
            type == "ARCHETYPE_NIGHT_OWL" -> MomentCopy(
                title = "Night Owl",
                description = if (copyVariant == 0) "Most of your listening happens when normal people are asleep. No judgment."
                    else "Night Owl — again. At this point it's just who you are.",
                statLines = rawStats.toStatLines("nightPct" to "%s of your listening", "peakLabel" to "peak: %s")
            )
            type == "ARCHETYPE_MORNING_LISTENER" -> MomentCopy(
                title = "Early Bird",
                description = if (copyVariant == 0) "While everyone's hitting snooze, you're already vibing."
                    else "Early Bird — still at it. Your alarm clock is just a suggestion.",
                statLines = rawStats.toStatLines("morningPct" to "%s of your listening", "peakLabel" to "peak: %s")
            )
            type == "ARCHETYPE_COMMUTE_LISTENER" -> MomentCopy(
                title = "Road Warrior",
                description = if (copyVariant == 0) "Rush hour is your concert hour. 7–9am and 5–7pm belong to you."
                    else "Road Warrior returns. The commute is nothing without a soundtrack.",
                statLines = rawStats.toStatLines("commutePct" to "%s during commute hours", "commuteDays" to "%s days this month")
            )
            type == "ARCHETYPE_COMPLETIONIST" -> MomentCopy(
                title = "Completionist",
                description = if (copyVariant == 0) "You skip less than 5% of songs. Truly dedicated — or just too lazy to skip."
                    else "Still a Completionist. Every song gets its full moment.",
                statLines = rawStats.toStatLines("skipPct" to "%s skip rate", "totalPlays" to "%s plays")
            )
            type == "ARCHETYPE_CERTIFIED_SKIPPER" -> MomentCopy(
                title = "Certified Skipper",
                description = if (copyVariant == 0) "You skip more songs than you finish. Bold strategy."
                    else "Still skipping. Nothing meets your impossibly high standards.",
                statLines = rawStats.toStatLines("skipPct" to "%s skip rate", "skipCount" to "%s skips")
            )
            type == "ARCHETYPE_DEEP_CUT_DIGGER" -> MomentCopy(
                title = "Deep Cut Digger",
                description = "You found a song and you played it into the ground. Respect.",
                statLines = rawStats.toStatLines("playCount" to "%s plays", "duration" to "%s total")
            )
            type == "ARCHETYPE_LOYAL_FAN" -> MomentCopy(
                title = "Loyal Fan",
                description = if (copyVariant == 0) "Over half your listening is one artist. That's not a preference, it's devotion."
                    else "Still loyal. $name has you on lock.",
                statLines = rawStats.toStatLines("topPct" to "%s of listening", "playCount" to "%s plays")
            )
            type == "ARCHETYPE_EXPLORER" -> MomentCopy(
                title = "Explorer",
                description = if (copyVariant == 0) "5+ new artists this week. Your ears are adventurous."
                    else "Exploring again. You refuse to stay in one lane.",
                statLines = rawStats.toStatLines("newArtists" to "%s new artists", "newSongs" to "%s new songs")
            )
            type == "ARCHETYPE_WEEKEND_WARRIOR" -> MomentCopy(
                title = "Weekend Warrior",
                description = if (copyVariant == 0) "Weekdays are for work. Weekends are for music."
                    else "Weekend Warrior — your Saturdays and Sundays are still loud.",
                statLines = rawStats.toStatLines("weekendPct" to "%s on weekends", "weekendHours" to "%s on weekends/week")
            )
            type == "ARCHETYPE_WIDE_TASTE" -> MomentCopy(
                title = "Wide Taste",
                description = if (copyVariant == 0) "No single artist dominates. You contain multitudes."
                    else "Wide Taste — still eclectic, still impossible to pin down.",
                statLines = rawStats.toStatLines("uniqueArtists" to "%s artists this month", "topArtistPct" to "top artist: %s")
            )
            type == "ARCHETYPE_REPEAT_OFFENDER" -> MomentCopy(
                title = "Repeat Offender",
                description = "You found your songs. You're not letting go.",
                statLines = rawStats.toStatLines("top3Pct" to "top 3 songs: %s of plays", "topSongLine" to "%s")
            )
            type == "ARCHETYPE_ALBUM_LISTENER" -> MomentCopy(
                title = "Album Listener",
                description = if (copyVariant == 0) "You don't shuffle. You commit. An album is a journey."
                    else "Album Listener — still pressing play and trusting the tracklist.",
                statLines = rawStats.toStatLines("albumRuns" to "%s album runs this month", "avgPerRun" to "avg %s songs per run")
            )

            // ── Behavioral ──────────────────────────────────────
            type == "OBSESSION_DAILY" -> {
                val count = rawStats["playCountToday"]?.toString() ?: "5+"
                MomentCopy(
                    title = "Today's Fixation",
                    description = "You played this $count times today. The neighbors definitely know the lyrics.",
                    statLines = rawStats.toStatLines("playCountToday" to "%s plays today", "allTimePlays" to "%s all-time")
                )
            }
            type == "DAILY_RITUAL" -> MomentCopy(
                title = "Daily Ritual",
                description = "7 days straight. This song is part of your routine now.",
                statLines = rawStats.toStatLines("playCount" to "%s all-time plays", "duration" to "%s total")
            )
            type == "BREAKUP_CANDIDATE" -> {
                val skips = rawStats["skipCount"]?.toString() ?: "10+"
                MomentCopy(
                    title = "It's Complicated",
                    description = "$skips skips this week. Maybe it's time to take a break from $name.",
                    statLines = rawStats.toStatLines("skipCount" to "%s skips this week", "playsThisWeek" to "%s plays this week")
                )
            }
            type == "FAST_OBSESSION" -> {
                val plays = rawStats["playCount"]?.toString() ?: "20+"
                val days = rawStats["ageDays"]?.toString() ?: "?"
                MomentCopy(
                    title = "Instant Classic",
                    description = "$plays plays in $days days. This one didn't need time to grow on you.",
                    statLines = rawStats.toStatLines("playCount" to "%s plays", "ageLine" to "%s")
                )
            }
            type == "LONGEST_SESSION" -> {
                val label = rawStats["sessionLabel"]?.toString() ?: "?"
                MomentCopy(
                    title = "Marathon Mode",
                    description = "$label in one sitting — that's longer than most movies.",
                    statLines = rawStats.toStatLines("sessionLabel" to "%s", "pbLabel" to "%s")
                )
            }
            type == "QUICK_OBSESSION" -> {
                val days = rawStats["ageDays"]?.toString() ?: "?"
                MomentCopy(
                    title = "Love at First Listen",
                    description = "Discovered $days days ago, already in your top 5. That was fast.",
                    statLines = rawStats.toStatLines("rank" to "#%s all-time", "ageLine" to "%s since discovered")
                )
            }
            type == "DISCOVERY_WEEK" -> {
                val count = rawStats["newArtists"]?.toString() ?: "8+"
                MomentCopy(
                    title = "New Ears Week",
                    description = "$count new artists in one week. Your algorithm could never.",
                    statLines = rawStats.toStatLines("newArtists" to "%s new artists", "newSongs" to "%s new songs")
                )
            }
            type == "RESURRECTION" -> {
                val days = rawStats["gapDays"]?.toString() ?: "30+"
                MomentCopy(
                    title = "Back from the Dead",
                    description = "$days days of silence. Then today happened. Welcome back.",
                    statLines = rawStats.toStatLines("gapDays" to "%s days away", "playsToday" to "%s plays today")
                )
            }
            type == "NIGHT_BINGE" -> {
                val duration = rawStats["duration"]?.toString() ?: "2h+"
                MomentCopy(
                    title = "3am Vibes",
                    description = "$duration of music after midnight. Tomorrow-you is going to have opinions about this.",
                    statLines = rawStats.toStatLines("duration" to "%s after midnight", "songCount" to "~%s songs")
                )
            }
            type == "COMFORT_ZONE" -> {
                val pct = rawStats["top5Pct"]?.toString() ?: "80+"
                MomentCopy(
                    title = "Comfort Zone",
                    description = "Your top 5 songs made up $pct% of your week. You know what you like.",
                    statLines = rawStats.toStatLines("top5Pct" to "%s%% from 5 songs", "totalPlays" to "%s plays this week")
                )
            }
            type == "REDISCOVERY" -> {
                val days = rawStats["gapDays"]?.toString() ?: "60+"
                MomentCopy(
                    title = "The Comeback",
                    description = "$days days without $name. Then you couldn't stop. Welcome back.",
                    statLines = rawStats.toStatLines("gapDays" to "%s days away", "playsThisWeek" to "%s plays this week")
                )
            }
            type == "SLOW_BURN" -> {
                val days = rawStats["ageDays"]?.toString() ?: "60+"
                MomentCopy(
                    title = "Slow Burn",
                    description = "$days days in your library before it clicked. Some things take time.",
                    statLines = rawStats.toStatLines("ageDays" to "%s days to click", "totalPlays" to "%s plays now")
                )
            }
            type == "MARATHON_WEEK" -> {
                val duration = rawStats["weekDuration"]?.toString() ?: "?"
                MomentCopy(
                    title = "Marathon Week",
                    description = "$duration this week. Your most listened week ever. New personal record.",
                    statLines = rawStats.toStatLines("weekDuration" to "%s this week", "songArtistLine" to "%s")
                )
            }

            // Fallback for any unknown type
            else -> MomentCopy(
                title = type.replace("_", " ").lowercase()
                    .replaceFirstChar { it.uppercase() },
                description = "Something happened worth noting.",
                statLines = emptyList()
            )
        }
    }

    // ── Stat line helpers ───────────────────────────────────

    private fun buildSongMilestoneStats(raw: Map<String, Any>): List<String> {
        val stats = mutableListOf<String>()
        raw["totalDurationMs"]?.let { stats += "${formatDuration(it.toString().toLong())} total" }
        raw["rank"]?.let { stats += "#$it all-time" }
        return stats
    }

    private fun buildArtistHourStats(raw: Map<String, Any>): List<String> {
        val stats = mutableListOf<String>()
        raw["playCount"]?.let { stats += "$it plays" }
        raw["uniqueSongs"]?.let { stats += "$it songs heard" }
        return stats
    }

    private fun buildStreakStats(raw: Map<String, Any>): List<String> {
        val stats = mutableListOf<String>()
        raw["avgMinPerDay"]?.let { stats += "avg ${it}min/day" }
        raw["uniqueSongs"]?.let { stats += "$it songs this streak" }
        return stats
    }

    private fun buildTotalHourStats(raw: Map<String, Any>): List<String> {
        val stats = mutableListOf<String>()
        raw["uniqueSongs"]?.let { stats += "$it unique songs" }
        raw["uniqueArtists"]?.let { stats += "$it artists" }
        return stats
    }

    private fun buildDiscoveryStats(raw: Map<String, Any>): List<String> {
        val stats = mutableListOf<String>()
        raw["uniqueArtists"]?.let { stats += "from $it artists" }
        raw["totalHours"]?.let { stats += "${it}h of music" }
        return stats
    }

    /**
     * Convenience: builds stat lines from a raw stats map using format templates.
     * Each pair is (rawKey -> formatTemplate). Skips entries where rawKey is missing.
     */
    private fun Map<String, Any>.toStatLines(vararg pairs: Pair<String, String>): List<String> {
        return pairs.mapNotNull { (key, template) ->
            this[key]?.let { template.format(it) }
        }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentCopywriter.kt
git commit -m "feat: add MomentCopywriter with playful copy for all 48 moment types"
```

---

### Task 3: Update Moment entity and Room migration

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/data/model/Moment.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/dao/MomentDao.kt`

**Step 1: Add tier, isPersonalBest, copyVariant fields to Moment entity**

In `Moment.kt`, add these three fields after `entityName`:

```kotlin
val tier: String = "BRONZE",           // "BRONZE", "SILVER", or "GOLD"
val isPersonalBest: Boolean = false,
val copyVariant: Int = 0               // 0 = first time this type was triggered
```

**Step 2: Add countByType query to MomentDao**

In `MomentDao.kt`, add:

```kotlin
@Query("SELECT COUNT(*) FROM moments WHERE type = :type")
suspend fun countByType(type: String): Int
```

**Step 3: Add Room migration 14→15 to MusicStatsDatabase**

Add a new migration in the companion object:

```kotlin
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE moments ADD COLUMN tier TEXT NOT NULL DEFAULT 'BRONZE'")
        db.execSQL("ALTER TABLE moments ADD COLUMN isPersonalBest INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE moments ADD COLUMN copyVariant INTEGER NOT NULL DEFAULT 0")
        // Wipe moments so they re-detect with new copy, tiers, and personal bests
        db.execSQL("DELETE FROM moments")
    }
}
```

Update the `@Database` annotation version from 14 to 15.

**Step 4: Register the migration in DatabaseModule**

Find the Hilt database module (likely `app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt`) and add `MIGRATION_14_15` to the builder's `.addMigrations(...)` call.

**Step 5: Commit**

```bash
git add app/src/main/java/com/musicstats/app/data/model/Moment.kt \
       app/src/main/java/com/musicstats/app/data/MusicStatsDatabase.kt \
       app/src/main/java/com/musicstats/app/data/dao/MomentDao.kt \
       app/src/main/java/com/musicstats/app/data/di/DatabaseModule.kt
git commit -m "feat: add tier, isPersonalBest, copyVariant to Moment entity (migration 14→15)"
```

---

### Task 4: Integrate MomentCopywriter and MomentTier into MomentDetector

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/service/MomentDetector.kt`

This is the largest change. Each of the 20+ `detect*` methods currently constructs `Moment(...)` inline with hardcoded title/description/statLines. We need to:

1. Build a `rawStats: Map<String, Any>` from the computed values
2. Query `momentDao.countByType(type)` for `copyVariant`
3. Call `MomentCopywriter.generate(type, entityName, rawStats, copyVariant)` to get the copy
4. Call `MomentTier.tierFor(type)` to get the base tier
5. For applicable types, detect personal best and bump tier
6. Pass `tier`, `isPersonalBest`, `copyVariant` to the `Moment` constructor

**Step 1: Add imports at top of MomentDetector.kt**

```kotlin
import com.musicstats.app.data.model.MomentTier
import com.musicstats.app.service.MomentCopywriter
```

**Step 2: Refactor detectSongPlayMilestones**

Replace the current `detectSongPlayMilestones` method (lines 70–91) with:

```kotlin
private suspend fun detectSongPlayMilestones(): List<Moment> {
    val result = mutableListOf<Moment>()
    val songs = eventDao.getSongsWithMinPlays(SONG_PLAY_THRESHOLDS.first())
    for (song in songs) {
        val rank = eventDao.getSongRankByPlayCountSuspend(song.songId)
        for (threshold in SONG_PLAY_THRESHOLDS) {
            if (song.playCount >= threshold) {
                val type = "SONG_PLAYS_$threshold"
                val copyVariant = momentDao.countByType(type)
                val rawStats = mapOf<String, Any>(
                    "totalDurationMs" to song.totalDurationMs,
                    "rank" to rank,
                    "threshold" to threshold
                )
                val copy = MomentCopywriter.generate(type, song.artist, rawStats, copyVariant)
                val tier = MomentTier.tierFor(type)
                persistIfNew(Moment(
                    type = type,
                    entityKey = "${song.songId}:$threshold",
                    triggeredAt = System.currentTimeMillis(),
                    title = copy.title,
                    description = copy.description,
                    songId = song.songId,
                    statLines = copy.statLines,
                    imageUrl = song.albumArtUrl,
                    tier = tier.name,
                    isPersonalBest = copyVariant == 0,
                    copyVariant = copyVariant
                ))?.let { result += it }
            }
        }
    }
    return result
}
```

**Step 3: Refactor all remaining detect* methods**

Apply the same pattern to all other detect methods. For each one:
- Build `rawStats` map from the values computed in that method
- Get `copyVariant` from `momentDao.countByType(type)`
- Get copy from `MomentCopywriter.generate(...)`
- Get tier from `MomentTier.tierFor(type)`
- Set `isPersonalBest = true` when it makes sense for that type (first occurrence, or surpasses a record)
- Pass new fields to `Moment(...)`

Key personal best logic by type:
- **LONGEST_SESSION**: always `isPersonalBest = true` (by definition it's a new record)
- **MARATHON_WEEK**: always `isPersonalBest = true` (by definition)
- **STREAK_***: `isPersonalBest = true` if no higher streak threshold has been reached
- **SONG_PLAYS_***: `isPersonalBest` if this is the first song to hit this threshold (copyVariant == 0)
- **ARTIST_HOURS_***: `isPersonalBest` if copyVariant == 0
- **All archetypes**: `isPersonalBest = false` (not applicable)
- **Behavioral moments**: `isPersonalBest = copyVariant == 0` for first occurrence

For each detect method, the `rawStats` keys must match what `MomentCopywriter.generate` expects. The key mapping for each method:

**detectArtistHourMilestones**: `"playCount"`, `"uniqueSongs"`
**detectStreakMilestones**: `"avgMinPerDay"`, `"uniqueSongs"`
**detectTotalHourMilestones**: `"uniqueSongs"`, `"uniqueArtists"`
**detectDiscoveryMilestones**: `"uniqueArtists"`, `"totalHours"`
**detectArchetypes** (Night Owl): `"nightPct"`, `"peakLabel"`
**detectArchetypes** (Morning): `"morningPct"`, `"peakLabel"`
**detectArchetypes** (Commute): `"commutePct"`, `"commuteDays"`
**detectArchetypes** (Completionist): `"skipPct"`, `"totalPlays"`
**detectArchetypes** (Skipper): `"skipPct"`, `"skipCount"`
**detectArchetypes** (Deep Cut): `"playCount"`, `"duration"`
**detectArchetypes** (Loyal Fan): `"topPct"`, `"playCount"`
**detectArchetypes** (Explorer): `"newArtists"`, `"newSongs"`
**detectArchetypes** (Weekend Warrior): `"weekendPct"`, `"weekendHours"`
**detectArchetypes** (Wide Taste): `"uniqueArtists"`, `"topArtistPct"`
**detectArchetypes** (Repeat Offender): `"top3Pct"`, `"topSongLine"`
**detectArchetypes** (Album Listener): `"albumRuns"`, `"avgPerRun"`
**detectObsessionDaily**: `"playCountToday"`, `"allTimePlays"`
**detectDailyRitual**: `"playCount"`, `"duration"`
**detectBreakupCandidate**: `"skipCount"`, `"playsThisWeek"`
**detectFastObsession**: `"playCount"`, `"ageDays"`, `"ageLine"`
**detectLongestSession**: `"sessionLabel"`, `"pbLabel"`
**detectQuickObsession**: `"rank"`, `"ageLine"`, `"ageDays"`
**detectDiscoveryWeek**: `"newArtists"`, `"newSongs"`
**detectResurrection**: `"gapDays"`, `"playsToday"`
**detectNightBinge**: `"duration"`, `"songCount"`
**detectComfortZone**: `"top5Pct"`, `"totalPlays"`
**detectRediscovery**: `"gapDays"`, `"playsThisWeek"`
**detectSlowBurn**: `"ageDays"`, `"totalPlays"`
**detectMarathonWeek**: `"weekDuration"`, `"songArtistLine"`

**Step 4: Commit**

```bash
git add app/src/main/java/com/musicstats/app/service/MomentDetector.kt
git commit -m "feat: integrate MomentCopywriter and MomentTier into all detection methods"
```

---

### Task 5: Add tier-aware visuals to MomentCard

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt`
- Modify: `app/src/main/java/com/musicstats/app/data/model/MomentTier.kt` (add visual properties)

**Step 1: Add visual properties to MomentTier**

Add these properties/functions to `MomentTier.kt`:

```kotlin
import androidx.compose.ui.graphics.Color

enum class MomentTier {
    BRONZE, SILVER, GOLD;

    val glowAlpha: Float get() = when (this) {
        BRONZE -> 0.25f
        SILVER -> 0.40f
        GOLD -> 0.55f
    }

    val borderColors: List<Color>? get() = when (this) {
        BRONZE -> null  // no border
        SILVER -> listOf(Color(0xFF8E8E93), Color(0xFFD1D1D6), Color(0xFF8E8E93))
        GOLD -> listOf(Color(0xFFFFD700), Color(0xFFFFF4B8), Color(0xFFDAA520), Color(0xFFFFD700))
    }

    val statPillColor: Color? get() = when (this) {
        BRONZE -> null  // default white alpha
        SILVER -> null  // accent color — handled by palette
        GOLD -> Color(0xFFFFD700)
    }

    companion object { /* existing tierFor and bumped */ }
}
```

**Step 2: Update MomentCard composable**

Key changes to `MomentCard.kt`:
- Parse `moment.tier` to `MomentTier` enum
- If tier has `borderColors`, wrap the card in a `Box` with a gradient border (1.5dp for silver, 2dp for gold)
- Adjust radial glow alpha based on tier
- For gold cards: apply a `Brush.linearGradient` to the title text (white → gold)
- For gold cards: add a "RARE" pill in the top-right corner
- For silver cards: use palette accent on stat pills instead of `Color.White.copy(alpha = 0.15f)`

The border implementation uses `Modifier.border(width, brush, shape)`:

```kotlin
val tier = remember(moment.tier) {
    try { MomentTier.valueOf(moment.tier) } catch (_: Exception) { MomentTier.BRONZE }
}

// Outer Box with gradient border for Silver/Gold
val borderModifier = tier.borderColors?.let { colors ->
    val borderWidth = if (tier == MomentTier.GOLD) 2.dp else 1.5.dp
    Modifier.border(borderWidth, Brush.linearGradient(colors), CardShape)
} ?: Modifier

Box(
    modifier = modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .then(borderModifier)
        .clip(CardShape)
        .clickable(onClick = onTap)
) { /* existing layers */ }
```

For the gold title gradient:
```kotlin
val titleStyle = if (tier == MomentTier.GOLD) {
    MaterialTheme.typography.titleLarge.copy(
        brush = Brush.linearGradient(listOf(Color.White, Color(0xFFFFD700)))
    )
} else {
    MaterialTheme.typography.titleLarge
}
```

For the RARE badge on gold cards:
```kotlin
if (tier == MomentTier.GOLD) {
    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFFFD700).copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("RARE", style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold, color = Color.Black)
    }
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/MomentCard.kt \
       app/src/main/java/com/musicstats/app/data/model/MomentTier.kt
git commit -m "feat: add tier-aware visuals to MomentCard (border, glow, gold gradient, RARE badge)"
```

---

### Task 6: Add tier-aware visuals to MomentShareCard

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt`

**Step 1: Mirror the tier-aware changes from MomentCard**

Apply the same visual treatments to the share card:
- Parse `moment.tier` to `MomentTier`
- Gradient border for silver/gold (scaled up slightly for the larger card)
- Gold title gradient brush
- RARE badge for gold cards
- Adjusted glow alpha per tier
- Silver stat pills use accent color

The share card uses the same patterns as MomentCard but with larger sizes:
- Border: 2dp silver, 3dp gold
- RARE badge: slightly larger padding
- Title gradient: same approach with `headlineMedium` instead of `titleLarge`

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/components/MomentShareCard.kt
git commit -m "feat: add tier-aware visuals to MomentShareCard"
```

---

### Task 7: Update AllMomentsViewModel preview moments

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt`

**Step 1: Update buildPreviewMoments to use MomentCopywriter and tiers**

The preview moments need the new `tier`, `isPersonalBest`, and `copyVariant` fields, plus the updated copy from `MomentCopywriter`.

For each preview moment:
1. Build a fake `rawStats` map with the preview values
2. Call `MomentCopywriter.generate(type, entityName, rawStats, 0)` (copyVariant 0 for previews)
3. Call `MomentTier.tierFor(type).name` for the tier
4. Add the new fields to the `Moment(...)` constructor

Example for song play milestones:
```kotlin
listOf(10, 25, 50, 100, 250, 500).forEachIndexed { idx, threshold ->
    val type = "SONG_PLAYS_$threshold"
    val rawStats = mapOf<String, Any>(
        "totalDurationMs" to (threshold * 200_000L),
        "rank" to (idx + 1)
    )
    val copy = MomentCopywriter.generate(type, s0?.artist ?: "The Weeknd", rawStats, 0)
    result += Moment(
        id = (-1L - idx), type = type, entityKey = "preview",
        triggeredAt = now,
        title = copy.title,
        description = copy.description,
        songId = s0?.songId,
        statLines = copy.statLines,
        imageUrl = s0?.albumArtUrl ?: songArt,
        tier = MomentTier.tierFor(type).name,
        isPersonalBest = false,
        copyVariant = 0
    )
}
```

Apply same pattern to all 48 preview moments. Ensure gold-tier previews showcase the gold styling.

**Step 2: Add imports**

```kotlin
import com.musicstats.app.data.model.MomentTier
import com.musicstats.app.service.MomentCopywriter
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/AllMomentsViewModel.kt
git commit -m "feat: update preview moments with copywriter and tier system"
```

---

### Task 8: Update MomentDetailBottomSheet for tiers

**Files:**
- Modify: `app/src/main/java/com/musicstats/app/ui/moments/MomentDetailBottomSheet.kt`

**Step 1: Add tier-aware styling to bottom sheet**

- Parse moment tier
- For gold moments: show a gold-colored "RARE" chip next to the title
- For gold moments: title text color uses gold accent
- Stat pill colors match tier (gold uses gold container, silver uses accent, bronze uses default primaryContainer)

Minor changes — the bottom sheet is secondary to the cards. Keep it subtle.

**Step 2: Commit**

```bash
git add app/src/main/java/com/musicstats/app/ui/moments/MomentDetailBottomSheet.kt
git commit -m "feat: add tier-aware styling to MomentDetailBottomSheet"
```

---

### Task 9: Build, verify, and test

**Step 1: Build the project**

```bash
cd "D:\vibes\music stats" && ./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. Fix any compilation errors.

**Step 2: Verify preview moments render**

Open the app, navigate to Moments → Preview tab. Verify:
- All 48 preview moments render without crashes
- Bronze/Silver/Gold cards have visually distinct treatments
- Gold cards show RARE badge and gradient title
- Silver cards show shimmer border
- Copy is cheeky and reads well

**Step 3: Verify real moments re-detect**

Since migration 14→15 wipes moments, open the app home screen to trigger `detectAndPersistNewMoments()`. Check:
- Moments are detected with new copy, tiers, and personal best flags
- No crashes during detection

**Step 4: Verify share card**

Tap a moment → Share. Verify:
- Share card renders with correct tier visuals
- Gold share cards show border and gradient title
- Share intent works correctly

**Step 5: Commit any fixes**

```bash
git add -A && git commit -m "fix: resolve build/runtime issues from shareability redesign"
```

---

## Summary

| Task | What | Files |
|------|------|-------|
| 1 | MomentTier enum | Create `MomentTier.kt` |
| 2 | MomentCopywriter | Create `MomentCopywriter.kt` |
| 3 | Moment entity + migration | Modify `Moment.kt`, `MusicStatsDatabase.kt`, `MomentDao.kt`, `DatabaseModule.kt` |
| 4 | Detector integration | Modify `MomentDetector.kt` |
| 5 | MomentCard visuals | Modify `MomentCard.kt`, `MomentTier.kt` |
| 6 | MomentShareCard visuals | Modify `MomentShareCard.kt` |
| 7 | Preview moments | Modify `AllMomentsViewModel.kt` |
| 8 | Detail bottom sheet | Modify `MomentDetailBottomSheet.kt` |
| 9 | Build & verify | Full integration test |
