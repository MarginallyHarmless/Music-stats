package com.musicstats.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeFormattersTest {

    @Test
    fun `formatDuration returns less than 1m for short durations`() {
        assertEquals("<1m", formatDuration(0))
        assertEquals("<1m", formatDuration(999))
        assertEquals("<1m", formatDuration(59_999))
    }

    @Test
    fun `formatDuration returns minutes for durations under an hour`() {
        assertEquals("1m", formatDuration(60_000))
        assertEquals("5m", formatDuration(300_000))
        assertEquals("59m", formatDuration(3_599_999))
    }

    @Test
    fun `formatDuration returns hours and minutes`() {
        assertEquals("1h 0m", formatDuration(3_600_000))
        assertEquals("1h 30m", formatDuration(5_400_000))
        assertEquals("2h 15m", formatDuration(8_100_000))
    }

    @Test
    fun `formatDuration handles large values`() {
        assertEquals("100h 0m", formatDuration(360_000_000))
    }

    @Test
    fun `startOfToday returns a timestamp before now`() {
        val start = startOfToday()
        assertTrue(start <= System.currentTimeMillis())
        assertTrue(start > 0)
    }

    @Test
    fun `startOfWeek returns a timestamp before or equal to startOfToday`() {
        val weekStart = startOfWeek()
        val todayStart = startOfToday()
        assertTrue(weekStart <= todayStart)
    }

    @Test
    fun `startOfMonth returns a timestamp before or equal to startOfToday`() {
        val monthStart = startOfMonth()
        val todayStart = startOfToday()
        assertTrue(monthStart <= todayStart)
    }

    @Test
    fun `daysAgo returns timestamp in the past`() {
        val sevenDaysAgo = daysAgo(7)
        assertTrue(sevenDaysAgo < System.currentTimeMillis())
        assertTrue(sevenDaysAgo < startOfToday())
    }

    @Test
    fun `daysAgo with 0 returns startOfToday`() {
        assertEquals(startOfToday(), daysAgo(0))
    }
}
