package com.musicstats.app.util

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "<1m"
    }
}

fun startOfToday(): Long {
    val now = java.time.LocalDate.now()
    return now.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun startOfWeek(): Long {
    val now = java.time.LocalDate.now()
    val firstDayOfWeek = java.time.temporal.WeekFields.of(java.util.Locale.getDefault()).firstDayOfWeek
    val weekStart = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(firstDayOfWeek))
    return weekStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun startOfMonth(): Long {
    val now = java.time.LocalDate.now().withDayOfMonth(1)
    return now.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}

fun daysAgo(days: Int): Long {
    return java.time.LocalDate.now().minusDays(days.toLong())
        .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
}
