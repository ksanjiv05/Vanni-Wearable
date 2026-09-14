package com.vaani.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun formatClock_underOneHour_isMmSs() {
        assertEquals("00:00", formatClock(0))
        assertEquals("04:20", formatClock(260_000))
        assertEquals("14:20", formatClock(860_000))
        assertEquals("00:09", formatClock(9_000))
    }

    @Test
    fun formatClock_overOneHour_isHMmSs() {
        assertEquals("1:00:00", formatClock(3_600_000))
        assertEquals("2:03:05", formatClock(7_385_000))
    }

    @Test
    fun formatClock_truncatesSubSecond() {
        assertEquals("00:01", formatClock(1_999))
    }

    @Test
    fun formatDuration_isAlwaysHhMmSs() {
        assertEquals("00:00:00", formatDuration(0))
        assertEquals("00:14:20", formatDuration(860_000))
        assertEquals("01:00:00", formatDuration(3_600_000))
    }

    @Test
    fun formatBytes_switchesUnitAtOneMb() {
        assertEquals("47 MB", formatBytes(47L * 1024 * 1024))
        assertEquals("512 KB", formatBytes(512L * 1024))
        assertEquals("1 MB", formatBytes(1024L * 1024))
    }

    @Test
    fun formatters_areLocaleRootAscii() {
        // No grouping/decimal localisation — technical values stay ASCII.
        assertEquals("2:03:05", formatClock(7_385_000))
        assertEquals("100 MB", formatBytes(100L * 1024 * 1024))
    }
}
