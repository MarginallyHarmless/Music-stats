package com.musicstats.app.service

object MomentPriority {

    private val tiers: Map<String, Int> = mapOf(
        // Tier 1 — Legendary
        "SONG_PLAYS_500" to 1,
        "STREAK_100" to 1,
        "TOTAL_HOURS_1000" to 1,
        "FLEX_COLLECTOR_5000" to 1,

        // Tier 2 — Epic
        "SONG_PLAYS_250" to 2,
        "STREAK_30" to 2,
        "TOTAL_HOURS_500" to 2,
        "FLEX_COLLECTOR_2000" to 2,
        "NARRATIVE_SOUNDTRACK" to 2,
        "NARRATIVE_PARALLEL_LIVES" to 2,
        "NARRATIVE_NIGHT_AND_DAY" to 2,

        // Tier 3 — Milestone
        "SONG_PLAYS_100" to 3,
        "ARTIST_HOURS_24" to 3,
        "STREAK_14" to 3,
        "TOTAL_HOURS_100" to 3,
        "SONGS_DISCOVERED_500" to 3,
        "FLEX_COLLECTOR_1000" to 3,
        "BEHAVIORAL_GROWER" to 3,
        "BEHAVIORAL_TASTE_DRIFT" to 3,
        "BEHAVIORAL_LOCKED_IN" to 3,
        "BEHAVIORAL_REPLACEMENT" to 3,
        "NARRATIVE_ORIGIN_STORY" to 3,
        "NARRATIVE_GATEWAY" to 3,
        "NARRATIVE_COLLECTION" to 3,
        "NARRATIVE_TAKEOVER" to 3,
        "NARRATIVE_SLOW_BUILD" to 3,
        "NARRATIVE_BINGE_AND_FADE" to 3,
        "NARRATIVE_FULL_CIRCLE" to 3,
        "NARRATIVE_ONE_THAT_GOT_AWAY" to 3,
        "NARRATIVE_RABBIT_HOLE" to 3,
        "FLEX_BIGGEST_MONTH" to 3,
        "FLEX_SPEED_RUN" to 3,

        // Tier 4 — Notable
        "SONG_PLAYS_50" to 4,
        "ARTIST_HOURS_10" to 4,
        "ARTIST_HOURS_5" to 4,
        "STREAK_7" to 4,
        "TOTAL_HOURS_24" to 4,
        "SONGS_DISCOVERED_250" to 4,
        "SONGS_DISCOVERED_100" to 4,
        "ARCHETYPE_NIGHT_OWL" to 4,
        "ARCHETYPE_MORNING_LISTENER" to 4,
        "ARCHETYPE_COMMUTE_LISTENER" to 4,
        "ARCHETYPE_COMPLETIONIST" to 4,
        "ARCHETYPE_CERTIFIED_SKIPPER" to 4,
        "ARCHETYPE_DEEP_CUT_DIGGER" to 4,
        "ARCHETYPE_LOYAL_FAN" to 4,
        "ARCHETYPE_EXPLORER" to 4,
        "ARCHETYPE_WEEKEND_WARRIOR" to 4,
        "ARCHETYPE_WIDE_TASTE" to 4,
        "ARCHETYPE_REPEAT_OFFENDER" to 4,
        "ARCHETYPE_ALBUM_LISTENER" to 4,
        "DAILY_RITUAL" to 4,
        "FAST_OBSESSION" to 4,
        "QUICK_OBSESSION" to 4,
        "DISCOVERY_WEEK" to 4,
        "REDISCOVERY" to 4,
        "SLOW_BURN" to 4,
        "MARATHON_WEEK" to 4,
        "BEHAVIORAL_MAIN_CHARACTER" to 4,
        "BEHAVIORAL_ARTIST_MARATHON" to 4,
        "BEHAVIORAL_ONE_HIT_WONDER" to 4,
        "BEHAVIORAL_CLOCK_WORK" to 4,
        "BEHAVIORAL_ANTHEM" to 4,
        "FLEX_LOOP" to 4,
        "FLEX_POWER_HOUR" to 4,
        "FLEX_AFTER_HOURS" to 4,

        // Tier 5 — Common
        "OBSESSION_DAILY" to 5,
        "BREAKUP_CANDIDATE" to 5,
        "LONGEST_SESSION" to 5,
        "RESURRECTION" to 5,
        "NIGHT_BINGE" to 5,
        "COMFORT_ZONE" to 5,
    )

    private const val DEFAULT_TIER = 4

    fun tierOf(type: String): Int = tiers[type] ?: DEFAULT_TIER

    fun canExpire(tier: Int): Boolean = tier >= 3

    fun shouldNotify(tier: Int): Boolean = tier <= 3

    companion object {
        const val GATE_HOURS = 5f
        const val GATE_MS = (GATE_HOURS * 3_600_000).toLong()
        const val EXPIRY_DAYS = 14
        const val EXPIRY_MS = EXPIRY_DAYS * 24L * 3_600_000L
    }
}
