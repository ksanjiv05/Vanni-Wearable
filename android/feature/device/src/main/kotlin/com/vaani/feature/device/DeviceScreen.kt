package com.vaani.feature.device

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.StatusChip
import com.vaani.core.designsystem.component.VaaniButton
import com.vaani.core.designsystem.component.VaaniCard
import com.vaani.core.designsystem.component.VaaniToggle
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme
import com.vaani.domain.device.LinkState
import com.vaani.domain.device.LinkTransport

/**
 * Device — live wearable link over BLE (primary) with Wi-Fi fallback.
 * Scan → connect → see device/SD info → list files → read/write test.
 */
@Composable
fun DeviceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceViewModel = hiltViewModel(),
) {
    val colors = VaaniTheme.colors
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) viewModel.startScan()
    }
    fun requestScan() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permLauncher.launch(perms)
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                VaaniIconView(VaaniIcon.ChevronLeft, tint = colors.ink)
            }
            Text("Device", style = MaterialTheme.typography.titleLarge, color = colors.ink,
                fontWeight = FontWeight.SemiBold)
        }
        HairlineDivider()

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(VaaniSpacing.screenH),
            verticalArrangement = Arrangement.spacedBy(VaaniSpacing.md),
        ) {
            // Connection status
            item {
                val connected = ui.state == LinkState.CONNECTED
                VaaniCard {
                    Column(Modifier.padding(VaaniSpacing.md).fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (ui.state) {
                                    LinkState.CONNECTED -> ui.deviceName ?: "Connected"
                                    LinkState.CONNECTING -> "Connecting…"
                                    LinkState.SCANNING -> "Scanning…"
                                    LinkState.ERROR -> "Connection error"
                                    else -> "No device paired"
                                },
                                style = MaterialTheme.typography.titleMedium, color = colors.ink,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (connected) {
                                StatusChip(
                                    label = if (ui.transport == LinkTransport.BLE) "BLE" else "Wi-Fi",
                                    variant = ChipVariant.Coffee,
                                )
                            }
                        }
                        ui.info?.let { info ->
                            Spacer(Modifier.height(VaaniSpacing.sm))
                            InfoRow("Chip", info.chip)
                            InfoRow("SD card", if (info.sdOk) "${info.sdType} · ${info.sdSizeMb} MB (${info.sdUsedMb} MB used)" else "not mounted")
                            InfoRow("Free heap", "${info.freeHeap / 1024} KB")
                            InfoRow("PSRAM free", "${info.psramFree / (1024 * 1024)} MB")
                            if (info.ssid.isNotBlank()) InfoRow("Wi-Fi AP", "${info.ssid} @ ${info.ip}")
                        }
                    }
                }
            }

            // Actions
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(VaaniSpacing.sm)) {
                    if (ui.state != LinkState.CONNECTED) {
                        VaaniButton(
                            label = if (ui.scanning) "Scanning…" else "Scan",
                            onClick = { requestScan() },
                            enabled = !ui.scanning,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        VaaniButton("Refresh", { viewModel.refreshInfo() }, Modifier.weight(1f))
                        VaaniButton("Write test", { viewModel.writeTestFile() }, Modifier.weight(1f), enabled = !ui.busy)
                        VaaniButton("Disconnect", { viewModel.disconnect() }, Modifier.weight(1f))
                    }
                }
            }

            // Sync recordings → pipeline (the core Vaani flow)
            if (ui.state == LinkState.CONNECTED) {
                item {
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.setDeleteAfterSync(!ui.deleteAfterSync) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Delete from device after sync", style = MaterialTheme.typography.bodyMedium,
                                color = colors.ink, fontWeight = FontWeight.Medium)
                            Text("Frees SD space. Only deletes after the recording is safely on your phone.",
                                style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        }
                        Spacer(Modifier.width(VaaniSpacing.sm))
                        VaaniToggle(checked = ui.deleteAfterSync, onCheckedChange = { viewModel.setDeleteAfterSync(it) })
                    }
                }
                item {
                    VaaniButton(
                        label = if (ui.syncing) "Syncing recordings…" else "Sync recordings → notes",
                        onClick = { viewModel.syncRecordings() },
                        enabled = !ui.syncing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                ui.syncStatus?.let { s ->
                    item {
                        VaaniCard(accent = true) {
                            Text(s, style = MaterialTheme.typography.bodySmall, color = colors.ink,
                                modifier = Modifier.padding(VaaniSpacing.md))
                        }
                    }
                }
            }

            // Discovered devices (while not yet connected)
            if (ui.state != LinkState.CONNECTED && ui.found.isNotEmpty()) {
                item { SectionLabel("FOUND DEVICES") }
                items(ui.found, key = { it.id }) { dev ->
                    VaaniCard(onClick = { viewModel.connect(dev) }) {
                        Row(
                            Modifier.padding(VaaniSpacing.md).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(dev.name, style = MaterialTheme.typography.bodyLarge, color = colors.ink,
                                    fontWeight = FontWeight.Medium)
                                Text("${dev.id} · ${dev.rssi} dBm", style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted)
                            }
                            StatusChip("BLE", ChipVariant.Slate)
                        }
                    }
                }
            }

            // Files in the app's /vaani folder (when connected) — never the whole SD card
            if (ui.state == LinkState.CONNECTED) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("VAANI FILES", Modifier.weight(1f))
                        Text("Reload", style = MaterialTheme.typography.labelLarge, color = colors.coffee,
                            modifier = Modifier.clickable { viewModel.refreshFiles() })
                    }
                }
                if (ui.files.isEmpty()) {
                    item { Text("No Vaani files yet.", style = MaterialTheme.typography.bodyMedium, color = colors.muted) }
                }
                items(ui.files, key = { it.name }) { f ->
                    val fullPath = "/vaani/" + f.name.trimStart('/')
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !f.isDir) { viewModel.readFile(fullPath) }
                            .padding(vertical = VaaniSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (f.isDir) "📁 ${f.name}" else "📄 ${f.name}",
                            style = MaterialTheme.typography.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
                        Text(if (f.isDir) "dir" else "${f.size} B",
                            style = MaterialTheme.typography.bodySmall, color = colors.muted)
                        if (!f.isDir) {
                            Spacer(Modifier.width(VaaniSpacing.sm))
                            Box(
                                Modifier.size(32.dp)
                                    .clickable(enabled = !ui.busy) { viewModel.requestDelete(fullPath) },
                                contentAlignment = Alignment.Center,
                            ) {
                                VaaniIconView(VaaniIcon.Trash, tint = colors.muted)
                            }
                        }
                    }
                }
            }

            // Last read/write result
            ui.lastResult?.let {
                item {
                    VaaniCard(accent = false) {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = colors.ink,
                            modifier = Modifier.padding(VaaniSpacing.md))
                    }
                }
            }

            ui.message?.let {
                item { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.muted) }
            }
        }
    }

    // Confirm dialog for manual delete from the wearable's SD card.
    ui.pendingDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete from device?") },
            text = { Text("Permanently delete $path from the wearable's SD card. This can't be undone.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmDelete() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val colors = VaaniTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = colors.ink)
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = VaaniTheme.colors.muted, modifier = modifier)
}
