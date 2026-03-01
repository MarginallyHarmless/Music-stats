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
     *                      - "skipRate"/"skipPct", "skipCount", "totalPlays"
     *                      - "avgMinPerDay", "uniqueSongs"
     *                      - "nightPct", "peakLabel", "gapDays", "ageDays"
     *                      - "sessionLabel", "weekDuration"
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
