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
