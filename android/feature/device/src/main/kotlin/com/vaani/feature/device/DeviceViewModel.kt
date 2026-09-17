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
import kotlinx.coroutines.flow.first
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
    val deleteAfterSync: Boolean = false,   // auto-delete each recording from SD after safe import
    val pendingDelete: String? = null,      // file path awaiting delete confirmation
    val message: String? = null,
)

@HiltViewModel
class DeviceViewModel @Inject constructor(
    private val link: DeviceLink,
    private val notes: com.vaani.domain.repository.NotesRepository,
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
            if (_ui.value.state == LinkState.CONNECTED) {
                refreshFiles()
                pushDashboard()
            }
        }
    }

    /**
     * Push a glanceable dashboard to the wearable's screen: app-linked flag, count of recordings
     * still pending transfer, and today's top todos (first 5). Called after connect and after sync
     * so the wearable stays informative even when the phone isn't in hand.
     */
    private fun pushDashboard() {
        viewModelScope.launch {
            val (pending, todos) = gatherDashboard()
            runCatching { link.pushDisplay(appLinked = true, pendingNotes = pending, todos = todos) }
        }
    }

    /** pending = recordings imported but not yet READY; todos = today's open items, HIGH priority first. */
    private suspend fun gatherDashboard(): Pair<Int, List<String>> {
        val noteList = runCatching { notes.observeNotes().first() }.getOrDefault(emptyList())
        val pending = runCatching { notes.observeProcessing().first().size }.getOrDefault(0)
        val openTodos = noteList
            .flatMap { it.todos }
            .filter { it.status == com.vaani.domain.model.TodoStatus.OPEN }
            .sortedByDescending { it.priority.ordinal }   // HIGH → MEDIUM → LOW
            .take(5)
            .map { t -> t.dueHint?.let { "${t.text} (${it})" } ?: t.text }
        return pending to openTodos
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

    /** Only the app's own /vaani folder is shown — never the whole SD card. */
    fun refreshFiles(path: String = VAANI_DIR) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            when (val r = link.listFiles(path)) {
                is Outcome.Ok -> _ui.value = _ui.value.copy(busy = false, files = r.value,
                    message = "Listed ${r.value.size} file(s)")
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

    /** Write-test: write a timestamped note into the app's /vaani folder, then re-list. */
    fun writeTestFile() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            val path = "$VAANI_DIR/vaani_from_app.txt"
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
        val deleteAfter = _ui.value.deleteAfterSync
        viewModelScope.launch {
            link.syncRecordings(deleteAfterSync = deleteAfter).collect { p ->
                val line = when (p) {
                    is com.vaani.domain.device.SyncProgress.Started -> "Found ${p.total} recording(s) on device"
                    is com.vaani.domain.device.SyncProgress.Item -> "Pulled ${p.name} (${p.bytes} B) → queued [${p.index}/${p.total}]"
                    is com.vaani.domain.device.SyncProgress.Skipped -> "Skipped ${p.name}: ${p.reason}"
                    is com.vaani.domain.device.SyncProgress.Deleted -> "Deleted ${p.name} from device"
                    is com.vaani.domain.device.SyncProgress.Done ->
                        "Synced ${p.imported}/${p.total} — transcription queued" +
                            if (p.deleted > 0) " · ${p.deleted} deleted from device" else ""
                    is com.vaani.domain.device.SyncProgress.Failed -> "Sync failed: ${p.message}"
                }
                val done = p is com.vaani.domain.device.SyncProgress.Done || p is com.vaani.domain.device.SyncProgress.Failed
                _ui.value = _ui.value.copy(syncStatus = line, syncing = !done)
            }
            refreshFiles()
            pushDashboard()   // pending count + todos changed after a sync
        }
    }

    fun setDeleteAfterSync(enabled: Boolean) {
        _ui.value = _ui.value.copy(deleteAfterSync = enabled)
    }

    /** Ask for confirmation before deleting a file from the wearable's SD card. */
    fun requestDelete(path: String) {
        _ui.value = _ui.value.copy(pendingDelete = path)
    }

    fun cancelDelete() {
        _ui.value = _ui.value.copy(pendingDelete = null)
    }

    /** Confirmed manual delete of a file on the wearable's SD card, then re-list. */
    fun confirmDelete() {
        val path = _ui.value.pendingDelete ?: return
        _ui.value = _ui.value.copy(pendingDelete = null, busy = true)
        viewModelScope.launch {
            when (val r = link.deleteFile(path)) {
                is Outcome.Ok -> {
                    _ui.value = _ui.value.copy(busy = false, lastResult = "DELETED $path")
                    refreshFiles()
                }
                is Outcome.Err -> _ui.value = _ui.value.copy(busy = false, message = errText(r))
            }
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

    companion object { const val VAANI_DIR = "/vaani" }
}
