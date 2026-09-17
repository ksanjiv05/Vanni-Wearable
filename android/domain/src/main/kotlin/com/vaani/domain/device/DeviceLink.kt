package com.vaani.domain.device

import com.vaani.domain.model.AppError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

/** Which physical transport reached the wearable. */
enum class LinkTransport { BLE, WIFI }

/** Connection lifecycle for the wearable link. */
enum class LinkState { IDLE, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

/** A wearable discovered while scanning (BLE advertisement or known Wi-Fi AP). */
data class DiscoveredDevice(
    val id: String,          // BLE MAC / stable address
    val name: String,        // advertised name, e.g. "Vaani-3F0A"
    val rssi: Int,           // signal strength (BLE); 0 for Wi-Fi
    val transport: LinkTransport,
)

/** Live status of the connected wearable, parsed from its INFO payload. */
data class DeviceInfo(
    val device: String,
    val chip: String,
    val freeHeap: Long,
    val psramFree: Long,
    val sdOk: Boolean,
    val sdType: String,
    val sdSizeMb: Long,
    val sdUsedMb: Long,
    val ssid: String,
    val ip: String,
    val transport: LinkTransport,
)

/** One entry in the wearable's SD-card directory. */
data class RemoteFile(
    val name: String,
    val size: Long,
    val isDir: Boolean,
)

/** Aggregated connection snapshot the UI observes. */
data class LinkStatus(
    val state: LinkState,
    val transport: LinkTransport? = null,
    val deviceName: String? = null,
    val info: DeviceInfo? = null,
    val message: String? = null,
)

/**
 * The wearable-link port (pure domain). Implemented in :data:device by a
 * BLE-GATT transport (primary) with a Wi-Fi HTTP transport (fallback), chosen
 * by [DeviceLinkManager]. Feature modules depend only on this interface.
 */
interface DeviceLink {
    /** Observe connection state + last-known device info. */
    fun status(): Flow<LinkStatus>

    /** Scan for nearby wearables. Emits the growing result set until cancelled. */
    fun scan(timeoutMs: Long = 8_000): Flow<List<DiscoveredDevice>>

    /** Connect to a discovered device (BLE primary, auto-fallback to Wi-Fi handled by the manager). */
    suspend fun connect(device: DiscoveredDevice): Outcome<DeviceInfo>

    /** Drop the current connection. */
    suspend fun disconnect()

    /** Fetch fresh device/SD info from the connected wearable. */
    suspend fun info(): Outcome<DeviceInfo>

    /** List a directory on the wearable's SD card. */
    suspend fun listFiles(path: String = "/"): Outcome<List<RemoteFile>>

    /** Read a file's bytes from the wearable's SD card. */
    suspend fun readFile(path: String): Outcome<ByteArray>

    /** Write bytes to a file on the wearable's SD card (truncates/creates). */
    suspend fun writeFile(path: String, bytes: ByteArray): Outcome<Int>

    /** List recordings in the wearable's /recordings dir. */
    suspend fun listRecordings(): Outcome<List<RemoteFile>>

    /** Pull one recording's raw bytes (prefers Wi-Fi for bulk audio; BLE fallback). */
    suspend fun pullRecording(path: String): Outcome<ByteArray>

    /** Delete a file on the wearable's SD card. */
    suspend fun deleteFile(path: String): Outcome<Unit>

    /**
     * Push a compact dashboard to the wearable's screen so it's informative without the phone:
     * whether the app is actively linked, how many notes are pending transfer, and today's top
     * todos (already prioritised + trimmed to the first 5). Fire-and-forget; best-effort.
     */
    suspend fun pushDisplay(appLinked: Boolean, pendingNotes: Int, todos: List<String>): Outcome<Unit>

    /**
     * Pull every recording off the wearable and feed it into the ingest pipeline
     * (transcribe → enrich → note). Emits progress per recording. Requires a
     * connected link. Idempotent: already-synced recordings dedupe by content hash.
     *
     * @param deleteAfterSync when true, each recording is deleted from the wearable's SD card
     *   AFTER its bytes are safely persisted to the phone (durable blob store). Freed space is
     *   reclaimed on the device. Skipped/failed recordings are never deleted.
     */
    fun syncRecordings(deleteAfterSync: Boolean = false): Flow<SyncProgress>
}

/** Progress events for a wearable → pipeline sync. */
sealed interface SyncProgress {
    data class Started(val total: Int) : SyncProgress
    data class Item(val index: Int, val total: Int, val name: String, val bytes: Long, val recordingId: String) : SyncProgress
    data class Skipped(val name: String, val reason: String) : SyncProgress
    data class Deleted(val name: String) : SyncProgress
    data class Done(val imported: Int, val total: Int, val deleted: Int = 0) : SyncProgress
    data class Failed(val message: String) : SyncProgress
}
