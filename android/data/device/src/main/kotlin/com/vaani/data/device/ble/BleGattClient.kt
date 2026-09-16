package com.vaani.data.device.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.vaani.domain.device.DiscoveredDevice
import com.vaani.domain.device.LinkTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level BLE GATT client for the Vaani wearable. Speaks the firmware protocol:
 *  - write a UTF-8 command to CMD (e.g. "LIST /", "READ /x", "WRITE /x" then "D:"+bytes then "WEND")
 *  - collect notifications on DATA until a terminal "<END>" marker
 * Terminal markers and chunking match vaani_link firmware v2.
 */
@Singleton
class BleGattClient @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    companion object {
        val SVC: UUID = UUID.fromString("6e40fda0-b5a3-f393-e0a9-e50e24dcca9e")
        val INFO: UUID = UUID.fromString("6e40fda1-b5a3-f393-e0a9-e50e24dcca9e")
        val CMD: UUID = UUID.fromString("6e40fda2-b5a3-f393-e0a9-e50e24dcca9e")
        val DATA: UUID = UUID.fromString("6e40fda3-b5a3-f393-e0a9-e50e24dcca9e")
        val STAT: UUID = UUID.fromString("6e40fda4-b5a3-f393-e0a9-e50e24dcca9e")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        // DATA-frame opcodes (byte 0) — must match firmware. Structural framing so raw audio
        // bytes can never be mistaken for a control marker.
        const val OP_DATA: Byte = 0x01
        const val OP_END: Byte = 0x02
        const val OP_SZ: Byte = 0x03
        const val OP_ERR: Byte = 0x04
        const val NAME_PREFIX = "Vaani-"
    }

    private val manager get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter: BluetoothAdapter? get() = manager.adapter

    private var gatt: BluetoothGatt? = null
    @Volatile private var currentGatt: BluetoothGatt? = null   // the gatt for the in-flight connect attempt
    private val opLock = Mutex()
    // active scan callbacks so we can force-stop before connecting (radio can't scan + connect at once)
    private val activeScanCbs = mutableListOf<ScanCallback>()

    /** Invoked when an ESTABLISHED connection drops unexpectedly (not our own disconnect()). */
    @Volatile var onDisconnected: (() -> Unit)? = null

    // pending single-shot GATT continuations
    @Volatile private var onConnected: CompletableDeferred<Boolean>? = null
    @Volatile private var onServices: CompletableDeferred<Boolean>? = null
    @Volatile private var onWrite: CompletableDeferred<Boolean>? = null
    @Volatile private var onReadInfo: CompletableDeferred<ByteArray?>? = null
    @Volatile private var established = false   // true once a connect fully succeeded
    // notification accumulator for the in-flight command (read on the binder callback thread)
    @Volatile private var collector: ((ByteArray) -> Unit)? = null

    fun hasPermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    // ---- scanning ----
    @SuppressLint("MissingPermission")
    fun scan(timeoutMs: Long): Flow<List<DiscoveredDevice>> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
        if (scanner == null) { trySend(emptyList()); close(); return@callbackFlow }
        val found = LinkedHashMap<String, DiscoveredDevice>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device
                val name = dev.name ?: result.scanRecord?.deviceName ?: return
                if (!name.startsWith(NAME_PREFIX)) return
                found[dev.address] = DiscoveredDevice(dev.address, name, result.rssi, LinkTransport.BLE)
                trySend(found.values.toList())
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(SVC)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // also allow a name-only scan (some stacks don't advertise the 128-bit svc in the primary adv)
        scanner.startScan(listOf(filter), settings, cb)
        val nameCb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val dev = result.device
                val name = dev.name ?: result.scanRecord?.deviceName ?: return
                if (!name.startsWith(NAME_PREFIX)) return
                found[dev.address] = DiscoveredDevice(dev.address, name, result.rssi, LinkTransport.BLE)
                trySend(found.values.toList())
            }
        }
        scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), nameCb)
        synchronized(activeScanCbs) { activeScanCbs.add(cb); activeScanCbs.add(nameCb) }
        // Enforce the scan window so a forgotten screen doesn't leave two low-latency scanners
        // draining the radio indefinitely (P1 battery).
        val timer = launch { kotlinx.coroutines.delay(timeoutMs); close() }
        awaitClose {
            timer.cancel()
            runCatching { scanner.stopScan(cb) }
            runCatching { scanner.stopScan(nameCb) }
            synchronized(activeScanCbs) { activeScanCbs.remove(cb); activeScanCbs.remove(nameCb) }
        }
    }

    /** Force-stop every scan currently running (must precede a connect). */
    @SuppressLint("MissingPermission")
    fun stopAllScans() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        synchronized(activeScanCbs) {
            activeScanCbs.forEach { cb -> runCatching { scanner.stopScan(cb) } }
            activeScanCbs.clear()
        }
    }

    // ---- connection ----
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            // Ignore callbacks from a superseded gatt (a late teardown of a prior attempt must not
            // fail the current attempt's deferred — P1 stale-callback race).
            if (g !== currentGatt) { runCatching { g.close() }; return }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                onConnected?.complete(true)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onConnected?.complete(false)
                if (established) {   // an unexpected drop of a live link → notify upward
                    established = false
                    onDisconnected?.invoke()
                }
            }
        }
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (g !== currentGatt) return
            onServices?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            onWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (c.uuid == INFO) onReadInfo?.complete(if (status == BluetoothGatt.GATT_SUCCESS) c.value else null)
        }
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (c.uuid == DATA) collector?.invoke(c.value ?: ByteArray(0))
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Boolean = opLock.withLock {
        stopAllScans()
        kotlinx.coroutines.delay(600)   // let the controller leave scan mode before initiating
        val dev = adapter?.getRemoteDevice(address) ?: return false
        // up to 2 attempts — status 133 on the first connectGatt is common on many stacks
        repeat(2) { attempt ->
            disconnectInternal()
            onConnected = CompletableDeferred()
            val g = dev.connectGatt(context, false, gattCallback, BluetoothDevice_TRANSPORT_LE)
            gatt = g
            currentGatt = g
            val ok = withTimeoutOrNull(12_000) { onConnected!!.await() } ?: false
            if (ok) {
                onServices = CompletableDeferred()
                gatt?.discoverServices()
                val sok = withTimeoutOrNull(8_000) { onServices!!.await() } ?: false
                if (sok) {
                    runCatching { gatt?.requestMtu(517) }
                    kotlinx.coroutines.delay(200)
                    // Ensure the link is bonded/encrypted BEFORE any command. The characteristics
                    // require encryption; issuing a WRITE_NO_RESPONSE command before bonding
                    // completes would be silently dropped. createBond() is idempotent (returns fast
                    // if already bonded from a previous session — keys persist on both sides).
                    ensureBonded(dev)
                    enableNotifications()
                    kotlinx.coroutines.delay(300)
                    established = true
                    return true
                }
            }
            disconnectInternal()
            kotlinx.coroutines.delay(800)
        }
        false
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications() {
        val data = gatt?.getService(SVC)?.getCharacteristic(DATA) ?: return
        gatt?.setCharacteristicNotification(data, true)
        val cccd = data.getDescriptor(CCCD) ?: return
        @Suppress("DEPRECATION")
        cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        @Suppress("DEPRECATION")
        gatt?.writeDescriptor(cccd)
    }

    /**
     * Bond (pair + encrypt) with the wearable and wait until it completes. The firmware's
     * characteristics are encryption-required, so we must be bonded before issuing any command.
     * If already bonded (keys persisted from a prior session) this returns immediately.
     */
    @SuppressLint("MissingPermission")
    private suspend fun ensureBonded(dev: android.bluetooth.BluetoothDevice) {
        if (dev.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) return
        val bonded = CompletableDeferred<Boolean>()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, i: android.content.Intent) {
                val d = i.getParcelableExtra<android.bluetooth.BluetoothDevice>(android.bluetooth.BluetoothDevice.EXTRA_DEVICE)
                if (d?.address != dev.address) return
                val state = i.getIntExtra(android.bluetooth.BluetoothDevice.EXTRA_BOND_STATE, -1)
                if (state == android.bluetooth.BluetoothDevice.BOND_BONDED) { if (!bonded.isCompleted) bonded.complete(true) }
                else if (state == android.bluetooth.BluetoothDevice.BOND_NONE) { if (!bonded.isCompleted) bonded.complete(false) }
            }
        }
        runCatching {
            context.registerReceiver(receiver, android.content.IntentFilter(android.bluetooth.BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        }
        try {
            if (dev.bondState != android.bluetooth.BluetoothDevice.BOND_BONDED) dev.createBond()
            withTimeoutOrNull(20_000) { bonded.await() }
        } finally {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    @SuppressLint("MissingPermission")
    private fun disconnectInternal() {
        established = false
        currentGatt = null
        runCatching { gatt?.disconnect() }
        runCatching { gatt?.close() }
        gatt = null
    }

    @SuppressLint("MissingPermission")
    fun disconnect() { disconnectInternal() }

    fun isConnected(): Boolean = gatt != null

    // ---- one command → accumulated response until <END> ----
    @SuppressLint("MissingPermission")
    private suspend fun writeCmd(cmd: ByteArray): Boolean {
        val ch = gatt?.getService(SVC)?.getCharacteristic(CMD) ?: return false
        // Use WRITE_NO_RESPONSE: the response-write callback congests behind the DATA
        // notification stream on the ESP32 stack and times out. NR is issue-and-go.
        @Suppress("DEPRECATION")
        run {
            ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ch.value = cmd
            if (gatt?.writeCharacteristic(ch) != true) return false
        }
        kotlinx.coroutines.delay(35)   // let the write drain before the next op
        return true
    }

    /**
     * Send [command], accumulate OP_DATA frames until OP_END (or OP_ERR). Frame byte 0 is the
     * opcode; payload is bytes[1..]. Used for text commands (INFO/LIST/DEL).
     */
    suspend fun command(command: String, timeoutMs: Long = 12_000): ByteArray? = opLock.withLock {
        val out = java.io.ByteArrayOutputStream()
        val ended = CompletableDeferred<Boolean>()
        collector = { frame ->
            if (frame.isNotEmpty()) when (frame[0]) {
                OP_END -> ended.complete(true)
                OP_ERR -> ended.complete(true)
                OP_DATA -> out.write(frame, 1, frame.size - 1)
                else -> { /* OP_SZ etc. ignored for text commands */ }
            }
        }
        val wrote = writeCmd(command.toByteArray(Charsets.UTF_8))
        if (!wrote) { collector = null; return null }
        withTimeoutOrNull(timeoutMs) { ended.await() }
        collector = null
        out.toByteArray()
    }

    /** Read binary via reliable block protocol: RN <path> <off> <len> → OP_SZ(total) + OP_DATA… + OP_END.
     *  Opcode framing means audio bytes can never collide with a marker. A short/dropped block is
     *  re-requested by offset, so no data is lost on BLE notification drops. */
    suspend fun readBinary(path: String, timeoutMs: Long = 120_000): ByteArray? = opLock.withLock {
        val blockSize = 480
        val maxRetryPerBlock = 8
        var total = -1L
        val out = java.io.ByteArrayOutputStream()
        var offset = 0L

        suspend fun requestBlock(off: Long, len: Int): Pair<ByteArray, Boolean>? {
            val blk = java.io.ByteArrayOutputStream()
            val ended = CompletableDeferred<Boolean>()
            var sawSz = false
            var errored = false
            collector = { frame ->
                if (frame.isNotEmpty()) when (frame[0]) {
                    OP_END -> ended.complete(true)
                    OP_ERR -> { errored = true; ended.complete(true) }
                    OP_SZ -> { if (total < 0) total = String(frame, 1, frame.size - 1).trim().toLongOrNull() ?: -1L; sawSz = true }
                    OP_DATA -> blk.write(frame, 1, frame.size - 1)
                    else -> {}
                }
            }
            val wrote = writeCmd("RN $path $off $len".toByteArray(Charsets.UTF_8))
            if (!wrote) { collector = null; return null }
            withTimeoutOrNull(8_000) { ended.await() }
            collector = null
            return if (errored) null else blk.toByteArray() to sawSz
        }

        // first block establishes total size; a missing SZ header is an ERROR, not "whole file"
        val (first, sawSz) = requestBlock(0, blockSize) ?: return null
        if (!sawSz || total < 0) return null          // P2: don't import a truncated file as complete
        if (total == 0L) return ByteArray(0)
        out.write(first); offset = first.size.toLong()

        while (offset < total) {
            val want = minOf(blockSize.toLong(), total - offset).toInt()
            var got: ByteArray? = null
            var tries = 0
            while (tries < maxRetryPerBlock) {
                val r = requestBlock(offset, want)
                if (r != null && r.first.size == want) { got = r.first; break }
                tries++
                kotlinx.coroutines.delay(50)
            }
            if (got == null) return null
            out.write(got); offset += got.size.toLong()
        }
        // final integrity check: assembled size must equal the device-reported total
        if (out.size().toLong() != total) return null
        out.toByteArray()
    }

    /** Streaming write session: WRITE path, then D:<chunk>… then WEND. */
    suspend fun writeSession(path: String, bytes: ByteArray): Boolean = opLock.withLock {
        val ended = CompletableDeferred<Boolean>()
        collector = { frame -> if (frame.isNotEmpty() && (frame[0] == OP_END || frame[0] == OP_ERR)) ended.complete(true) }
        if (!writeCmd("WRITE $path".toByteArray())) { collector = null; return false }
        // chunk the payload under the ATT MTU (conservative 160B data + 2B "D:")
        val chunkSize = 160
        var i = 0
        while (i < bytes.size) {
            val end = minOf(i + chunkSize, bytes.size)
            val frame = ByteArray(2 + (end - i))
            frame[0] = 'D'.code.toByte(); frame[1] = ':'.code.toByte()
            System.arraycopy(bytes, i, frame, 2, end - i)
            if (!writeCmd(frame)) { collector = null; return false }
            i = end
        }
        val ok = writeCmd("WEND".toByteArray())
        withTimeoutOrNull(4_000) { ended.await() }
        collector = null
        ok
    }
}

// connectGatt transport constant (android.bluetooth.BluetoothDevice.TRANSPORT_LE == 2)
private const val BluetoothDevice_TRANSPORT_LE = 2
