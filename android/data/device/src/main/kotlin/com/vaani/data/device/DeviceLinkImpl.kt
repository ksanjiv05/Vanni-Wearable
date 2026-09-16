package com.vaani.data.device

import com.vaani.data.device.ble.BleGattClient
import com.vaani.data.device.wifi.WifiHttpClient
import com.vaani.domain.device.DeviceInfo
import com.vaani.domain.device.DeviceLink
import com.vaani.domain.device.DiscoveredDevice
import com.vaani.domain.device.LinkState
import com.vaani.domain.device.LinkStatus
import com.vaani.domain.device.LinkTransport
import com.vaani.domain.device.RemoteFile
import com.vaani.domain.model.AppError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * DeviceLink implementation. BLE GATT is the PRIMARY transport; the Wi-Fi HTTP
 * server is the FALLBACK. connect() tries BLE first and only drops to Wi-Fi if
 * BLE is unavailable/fails. All file ops route through whichever transport is
 * currently active.
 */
@Singleton
class DeviceLinkImpl @Inject constructor(
    private val ble: BleGattClient,
    private val wifi: WifiHttpClient,
    private val wifiAp: com.vaani.data.device.wifi.WifiApConnector,
    private val syncManager: WearableSyncManager,
) : DeviceLink {

    private val _status = MutableStateFlow(LinkStatus(LinkState.IDLE))
    override fun status(): Flow<LinkStatus> = _status.asStateFlow()

    @Volatile private var active: LinkTransport? = null

    init {
        // P0-2: propagate real link drops (not just explicit disconnect) into status.
        wifiAp.onNetworkLost = {
            if (active == LinkTransport.WIFI) {
                active = null
                wifi.useNetwork(null)
                _status.value = LinkStatus(LinkState.DISCONNECTED, message = "Wi-Fi link lost")
            }
        }
        ble.onDisconnected = {
            if (active == LinkTransport.BLE) {
                active = null
                _status.value = LinkStatus(LinkState.DISCONNECTED, message = "Bluetooth link lost")
            }
        }
    }

    override fun scan(timeoutMs: Long): Flow<List<DiscoveredDevice>> {
        _status.value = _status.value.copy(state = LinkState.SCANNING)
        return ble.scan(timeoutMs)
    }

    override suspend fun connect(device: DiscoveredDevice): Outcome<DeviceInfo> = withContext(Dispatchers.IO) {
        _status.value = LinkStatus(LinkState.CONNECTING, deviceName = device.name)
        // BLE is the ENCRYPTED ROOT OF TRUST. It must come first: the wearable's WiFi PSK and REST
        // token are per-device secrets that are ONLY handed out over the bonded/encrypted BLE link
        // (CRED command). Android auto-bonds (Just Works) on first access to an encrypted
        // characteristic. Once we have the creds we try WiFi (far faster for bulk audio) using the
        // PROVISIONED PSK — no hardcoded passphrase anywhere.
        if (!(ble.isEnabled() && ble.hasPermissions())) {
            _status.value = LinkStatus(LinkState.ERROR, message = "Bluetooth is required to pair with the wearable")
            return@withContext Outcome.Err(AppError.Network("Bluetooth unavailable — cannot pair"))
        }
        if (!ble.connect(device.id)) {
            _status.value = LinkStatus(LinkState.ERROR, message = "Could not pair over Bluetooth")
            return@withContext Outcome.Err(AppError.Network("BLE pairing failed"))
        }
        // Provision secrets over the encrypted link.
        val creds = runCatching {
            ble.command("CRED")?.toString(Charsets.UTF_8)?.let { parseCreds(it) }
        }.getOrNull()
        val bleInfo = ble.command("INFO")?.toString(Charsets.UTF_8)?.let { parseInfo(it, LinkTransport.BLE) }
        if (bleInfo == null) {
            ble.disconnect()
            _status.value = LinkStatus(LinkState.ERROR, message = "No response from wearable")
            return@withContext Outcome.Err(AppError.Network("No device info after pairing"))
        }
        wifi.setToken(creds?.token)   // REST calls now carry the per-device bearer token

        // Try to upgrade to WiFi using the provisioned SSID + PSK (system approval dialog once).
        if (creds != null && creds.psk.isNotEmpty()) {
            try {
                val ssid = creds.ssid.ifEmpty { device.name }
                val net = wifiAp.connect(ssid = ssid, passphrase = creds.psk)
                if (net != null) {
                    wifi.useNetwork(net)
                    var wifiInfo: DeviceInfo? = null
                    repeat(12) {
                        if (wifiInfo == null) {
                            val v = runCatching { parseInfo(wifi.info(), LinkTransport.WIFI) }.getOrNull()
                            if (v != null) wifiInfo = v else kotlinx.coroutines.delay(800)
                        }
                    }
                    if (wifiInfo != null) {
                        active = LinkTransport.WIFI
                        _status.value = LinkStatus(LinkState.CONNECTED, LinkTransport.WIFI, device.name, wifiInfo)
                        return@withContext Outcome.Ok(wifiInfo!!)
                    }
                    // WiFi didn't come up — release and keep the working BLE link.
                    wifiAp.disconnect(); wifi.useNetwork(null)
                }
            } catch (c: kotlinx.coroutines.CancellationException) {
                wifiAp.disconnect(); wifi.useNetwork(null); ble.disconnect(); throw c
            } catch (_: Throwable) {
                wifiAp.disconnect(); wifi.useNetwork(null)
            }
        }
        // Stay on the already-connected (encrypted) BLE link.
        active = LinkTransport.BLE
        _status.value = LinkStatus(LinkState.CONNECTED, LinkTransport.BLE, device.name, bleInfo,
            message = if (creds == null) "Connected over Bluetooth (provisioning unavailable)"
                      else "Connected over Bluetooth")
        Outcome.Ok(bleInfo)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { ble.disconnect() }
        runCatching { wifiAp.disconnect() }
        runCatching { wifi.useNetwork(null) }
        runCatching { wifi.setToken(null) }
        active = null
        _status.value = LinkStatus(LinkState.DISCONNECTED)
    }

    override suspend fun info(): Outcome<DeviceInfo> = withContext(Dispatchers.IO) {
        when (active) {
            LinkTransport.BLE -> {
                val b = ble.command("INFO")?.toString(Charsets.UTF_8)
                b?.let { parseInfo(it, LinkTransport.BLE) }?.let { Outcome.Ok(it) }
                    ?: Outcome.Err(AppError.Network("No info from device (BLE)"))
            }
            LinkTransport.WIFI -> runCatching { parseInfo(wifi.info(), LinkTransport.WIFI) }
                .fold({ Outcome.Ok(it) }, { Outcome.Err(AppError.Network(it.message ?: "wifi info failed")) })
            null -> Outcome.Err(AppError.Network("Not connected"))
        }
    }

    override suspend fun listFiles(path: String): Outcome<List<RemoteFile>> = withContext(Dispatchers.IO) {
        when (active) {
            LinkTransport.BLE -> {
                val txt = ble.command("LIST $path")?.toString(Charsets.UTF_8)
                    ?: return@withContext Outcome.Err(AppError.Network("No response (BLE)"))
                val files = txt.split("\n").mapNotNull { line ->
                    val parts = line.split("\t")
                    if (parts.size >= 3) RemoteFile(parts[0], parts[1].toLongOrNull() ?: 0, parts[2] == "D") else null
                }
                Outcome.Ok(files)
            }
            LinkTransport.WIFI -> runCatching {
                val obj = JSONObject(wifi.listFiles(path))
                val arr = obj.getJSONArray("files")
                (0 until arr.length()).map {
                    val f = arr.getJSONObject(it)
                    RemoteFile(f.getString("name"), f.optLong("size"), f.optBoolean("dir"))
                }
            }.fold({ Outcome.Ok(it) }, { Outcome.Err(AppError.Network(it.message ?: "list failed")) })
            null -> Outcome.Err(AppError.Network("Not connected"))
        }
    }

    override suspend fun readFile(path: String): Outcome<ByteArray> = withContext(Dispatchers.IO) {
        when (active) {
            LinkTransport.BLE -> ble.command("READ $path", timeoutMs = 30_000)
                ?.let { Outcome.Ok(it) } ?: Outcome.Err(AppError.Network("Read failed (BLE)"))
            LinkTransport.WIFI -> runCatching { wifi.readFile(path) }
                .fold({ Outcome.Ok(it) }, { Outcome.Err(AppError.Network(it.message ?: "read failed")) })
            null -> Outcome.Err(AppError.Network("Not connected"))
        }
    }

    override suspend fun writeFile(path: String, bytes: ByteArray): Outcome<Int> = withContext(Dispatchers.IO) {
        when (active) {
            LinkTransport.BLE -> if (ble.writeSession(path, bytes)) Outcome.Ok(bytes.size)
                else Outcome.Err(AppError.Network("Write failed (BLE)"))
            LinkTransport.WIFI -> runCatching { wifi.writeFile(path, bytes); bytes.size }
                .fold({ Outcome.Ok(it) }, { Outcome.Err(AppError.Network(it.message ?: "write failed")) })
            null -> Outcome.Err(AppError.Network("Not connected"))
        }
    }

    override suspend fun listRecordings(): Outcome<List<RemoteFile>> = listFiles("/vaani")

    override suspend fun pullRecording(path: String): Outcome<ByteArray> = withContext(Dispatchers.IO) {
        when (active) {
            // WiFi is far faster for bulk audio → prefer it when the device is Wi-Fi-connected
            LinkTransport.WIFI -> runCatching { wifi.readFile(path) }
                .fold({ Outcome.Ok(it) }, { Outcome.Err(AppError.Network(it.message ?: "pull failed")) })
            // BLE: reliable block protocol (re-requests dropped blocks)
            LinkTransport.BLE -> {
                val b = ble.readBinary(path)
                b?.let { if (it.isNotEmpty()) Outcome.Ok(it) else Outcome.Err(AppError.Network("Empty file (BLE)")) }
                    ?: Outcome.Err(AppError.Network("Pull failed (BLE)"))
            }
            null -> Outcome.Err(AppError.Network("Not connected"))
        }
    }

    override suspend fun deleteFile(path: String): Outcome<Unit> = withContext(Dispatchers.IO) {
        when (active) {
            LinkTransport.BLE -> { ble.command("DEL $path"); Outcome.Ok(Unit) }
            LinkTransport.WIFI -> runCatching { wifi.deleteFile(path) }
                .fold({ Outcome.Ok(Unit) }, { Outcome.Err(AppError.Network(it.message ?: "delete failed")) })
            null -> Outcome.Err(AppError.Network("Not connected"))
        }
    }

    override fun syncRecordings(deleteAfterSync: Boolean): Flow<com.vaani.domain.device.SyncProgress> =
        syncManager.sync(this, deleteAfterSync)

    private data class Creds(val ssid: String, val psk: String, val token: String)

    private fun parseCreds(json: String): Creds? {
        val start = json.indexOf('{')
        if (start < 0) return null
        val o = JSONObject(json.substring(start))
        val psk = o.optString("psk", "")
        val token = o.optString("token", "")
        if (psk.isEmpty() && token.isEmpty()) return null
        return Creds(o.optString("ssid", ""), psk, token)
    }

    private fun parseInfo(json: String, transport: LinkTransport): DeviceInfo {
        val start = json.indexOf('{')
        val o = JSONObject(if (start >= 0) json.substring(start) else json)
        return DeviceInfo(
            device = o.optString("device", "Vaani"),
            chip = o.optString("chip", ""),
            freeHeap = o.optLong("freeHeap"),
            psramFree = o.optLong("psramFree"),
            sdOk = o.optBoolean("sdOk"),
            sdType = o.optString("sdType", "?"),
            sdSizeMb = o.optLong("sdSizeMB"),
            sdUsedMb = o.optLong("sdUsedMB"),
            ssid = o.optString("ssid", ""),
            ip = o.optString("ip", ""),
            transport = transport,
        )
    }
}
