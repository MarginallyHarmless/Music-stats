package com.musicstats.app.service

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class ChallengeType {
    DISCOVERY_STRETCH,
    GENRE_EXPLORER,
    ARTIST_DEEP_DIVE,
    REDISCOVERY,
    CONSISTENCY,
    TIME_STRETCH,
    NO_SKIP_RUN,
    NIGHT_OWL_EARLY_BIRD,
    MARATHON_SESSION,
    ARTIST_VARIETY,
    LOYALTY_TEST,
    SOURCE_SWAP,
    OLD_FAVORITE,
    WEEKEND_WARRIOR;
}

object WeekUtils {
    private val zone = ZoneId.systemDefault()

    fun currentWeekStart(): Long {
        val today = Instant.now().atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return monday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun previousWeekStart(): Long {
        val today = Instant.now().atZone(zone).toLocalDate()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val prevMonday = monday.minusWeeks(1)
        return prevMonday.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun weekEnd(weekStart: Long): Long {
        val monday = Instant.ofEpochMilli(weekStart).atZone(zone).toLocalDate()
        return monday.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun daysRemainingInWeek(): Int {
        val today = Instant.now().atZone(zone).toLocalDate()
        val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        return (sunday.toEpochDay() - today.toEpochDay()).toInt() + 1
    }
}
