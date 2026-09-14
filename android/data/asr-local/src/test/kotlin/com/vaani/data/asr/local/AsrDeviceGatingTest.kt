package com.vaani.data.asr.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsrDeviceGatingTest {

    private val gb = 1024L * 1024 * 1024

    @Test
    fun allowsModernArm64WithEnoughRam() {
        val d = AsrDeviceGating.evaluate(
            DeviceProfile(listOf("arm64-v8a"), 6 * gb, isCharging = false),
        )
        assertTrue(d.allowed)
    }

    @Test
    fun blocksNonArm64() {
        val d = AsrDeviceGating.evaluate(
            DeviceProfile(listOf("armeabi-v7a", "x86"), 8 * gb, isCharging = true),
        )
        assertFalse(d.allowed)
        assertTrue(d.reason.contains("arm64"))
    }

    @Test
    fun blocksLowRam() {
        val d = AsrDeviceGating.evaluate(
            DeviceProfile(listOf("arm64-v8a"), 3 * gb, isCharging = true),
        )
        assertFalse(d.allowed)
        assertTrue(d.reason.contains("RAM"))
    }

    @Test
    fun requiresChargerForBacklogOnly() {
        val profile = DeviceProfile(listOf("arm64-v8a"), 6 * gb, isCharging = false)
        assertTrue(AsrDeviceGating.evaluate(profile, requireCharging = false).allowed)
        assertFalse(AsrDeviceGating.evaluate(profile, requireCharging = true).allowed)
    }
}
