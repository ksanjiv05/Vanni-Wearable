package com.vaani.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.domain.device.DeviceInfo
import com.vaani.domain.device.DeviceLink
import com.vaani.domain.device.DiscoveredDevice
import com.vaani.domain.device.LinkState
import com.vaani.domain.device.LinkStatus
import com.vaani.domain.device.LinkTransport
import com.vaani.domain.device.RemoteFile
import com.vaani.domain.model.Outcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DeviceUiState(
    val state: LinkState = LinkState.IDLE,
    val transport: LinkTransport? = null,
    val scanning: Boolean = false,
    val found: List<DiscoveredDevice> = emptyList(),
    val info: DeviceInfo? = null,
    val deviceName: String? = null,
    val files: List<RemoteFile> = emptyList(),
    val busy: Boolean = false,
    val lastResult: String? = null,   // human-readable log line for the last read/write op
    val syncing: Boolean = false,
    val syncStatus: String? = null,   // live sync progress line
    val message: String? = null,
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val link: DeviceLink,
) : ViewModel() {

    private val _ui = MutableStateFlow(DeviceUiState())
    val ui: StateFlow<DeviceUiState> = _ui.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            link.status().collect { s: LinkStatus ->
                _ui.value = _ui.value.copy(
                    state = s.state,
                    transport = s.transport,
                    info = s.info ?: _ui.value.info,
                    deviceName = s.deviceName ?: _ui.value.deviceName,
                    message = s.message,
                )
            }
        }
    }

    fun startScan() {
        scanJob?.cancel()
        _ui.value = _ui.value.copy(scanning = true, found = emptyList(), message = null)
        scanJob = viewModelScope.launch {
            link.scan(8_000).collect { list ->
                _ui.value = _ui.value.copy(found = list)
            }
        }
        // auto-stop UI spinner after the scan window
        viewModelScope.launch {
            kotlinx.coroutines.delay(8_500)
            _ui.value = _ui.value.copy(scanning = false)
        }
    }

    fun connect(device: DiscoveredDevice) {
        scanJob?.cancel()
        _ui.value = _ui.value.copy(scanning = false, busy = true, message = "Connecting to ${device.name}…")
        viewModelScope.launch {
            when (val r = link.connect(device)) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(busy = false, info = r.value,
                    deviceName = device.name, message = "Connected")
                is Outcome.Err -> _ui.value = _ui.value.copy(busy = false, message = errText(r))
            }
            if (_ui.value.state == LinkState.CONNECTED) refreshFiles()
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            link.disconnect()
            _ui.value = _ui.value.copy(files = emptyList(), info = null, lastResult = null)
        }
    }

    fun refreshInfo() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            when (val r = link.info()) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(busy = false, info = r.value)
                is Outcome.Err -> _ui.value = _ui.value.copy(busy = false, message = errText(r))
            }
        }
    }

    fun refreshFiles(path: String = "/") {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            when (val r = link.listFiles(path)) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(busy = false, files = r.value,
                    message = "Listed ${r.value.size} entries")
                is Outcome.Err -> _ui.value = _ui.value.copy(busy = false, message = errText(r))
            }
        }
    }

    /** Read-test: read a file and show a short preview. */
    fun readFile(path: String) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            when (val r = link.readFile(path)) {
                is Outcome.Ok -> {
                    val preview = r.value.decodeToString().take(120)
                    _ui.value = _ui.value.copy(busy = false,
                        lastResult = "READ $path (${r.value.size} B): $preview")
                }
                is Outcome.Err -> _ui.value = _ui.value.copy(busy = false, message = errText(r))
            }
        }
    }

    /** Write-test: write a timestamped note to the SD card, then re-list. */
    fun writeTestFile() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            val path = "/vaani_from_app.txt"
            val body = "Written by Vaani app at ${System.currentTimeMillis()} over ${_ui.value.transport}"
            when (val r = link.writeFile(path, body.toByteArray())) {
                is Outcome.Ok -> {
                    _ui.value = _ui.value.copy(busy = false, lastResult = "WROTE $path (${r.value} B)")
                    refreshFiles()
                }
                is Outcome.Err -> _ui.value = _ui.value.copy(busy = false, message = errText(r))
            }
        }
    }

    fun syncRecordings() {
        _ui.value = _ui.value.copy(syncing = true, syncStatus = "Starting sync…", message = null)
        viewModelScope.launch {
            link.syncRecordings().collect { p ->
                val line = when (p) {
                    is com.vaani.domain.device.SyncProgress.Started -> "Found ${p.total} recording(s) on device"
                    is com.vaani.domain.device.SyncProgress.Item -> "Pulled ${p.name} (${p.bytes} B) → queued [${p.index}/${p.total}]"
                    is com.vaani.domain.device.SyncProgress.Skipped -> "Skipped ${p.name}: ${p.reason}"
                    is com.vaani.domain.device.SyncProgress.Done -> "Synced ${p.imported}/${p.total} — transcription queued"
                    is com.vaani.domain.device.SyncProgress.Failed -> "Sync failed: ${p.message}"
                }
                val done = p is com.vaani.domain.device.SyncProgress.Done || p is com.vaani.domain.device.SyncProgress.Failed
                _ui.value = _ui.value.copy(syncStatus = line, syncing = !done)
            }
            refreshFiles()
        }
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    override fun onCleared() {
        // P0: release the link when the screen goes away, or the BLE connection / bound Wi-Fi
        // socket factory leaks past the ViewModel. Use an app-scope launch since viewModelScope
        // is already cancelled at onCleared.
        kotlinx.coroutines.GlobalScope.launch { runCatching { link.disconnect() } }
    }

    private fun errText(e: Outcome.Err): String = when (val err = e.error) {
        is com.vaani.domain.model.AppError.Network -> err.message
        is com.vaani.domain.model.AppError.NotFound -> "Not found: ${err.id}"
        is com.vaani.domain.model.AppError.Unknown -> err.message
        else -> "Failed"
    }
}
