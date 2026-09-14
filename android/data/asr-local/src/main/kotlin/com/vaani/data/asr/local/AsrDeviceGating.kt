package com.vaani.data.asr.local

/**
 * Whether this device should run the on-device ASR engine (ADR-001 §6).
 * A 1–2B-class transformer on a weak/old device thermal-throttles and drains
 * battery, so gate it. Pure inputs → JVM-testable.
 */
data class DeviceProfile(
    val supportedAbis: List<String>,
    val totalRamBytes: Long,
    val isCharging: Boolean,
)

data class GatingDecision(val allowed: Boolean, val reason: String)

object AsrDeviceGating {

    const val MIN_RAM_BYTES = 4L * 1024 * 1024 * 1024 // 4 GB for Whisper-small

    /**
     * @param requireCharging demand a charger (used for large backlogs, not a
     *   single fresh recording which is cheap enough to run immediately).
     */
    fun evaluate(profile: DeviceProfile, requireCharging: Boolean = false): GatingDecision {
        val hasArm64 = profile.supportedAbis.any { it == "arm64-v8a" }
        return when {
            !hasArm64 -> GatingDecision(false, "on-device ASR needs an arm64-v8a device")
            profile.totalRamBytes < MIN_RAM_BYTES ->
                GatingDecision(false, "on-device ASR needs ≥ 4 GB RAM")
            requireCharging && !profile.isCharging ->
                GatingDecision(false, "connect a charger to process the backlog on-device")
            else -> GatingDecision(true, "ok")
        }
    }
}
