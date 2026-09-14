package com.vaani.feature.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.VaaniButton
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.MonoStyle
import com.vaani.core.designsystem.theme.OverlineStyle
import com.vaani.core.designsystem.theme.VaaniPalette
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

/**
 * Device — charcoal hero (battery/storage), backlog, firmware/OTA, identify,
 * unpair (screen 07). Milestone A renders the paired-device state from a
 * static snapshot; live BLE status lands in :data:device later.
 */
@Composable
fun DeviceScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) { VaaniIconView(VaaniIcon.ChevronLeft, tint = colors.ink) }
        }
        Text(
            "Device",
            color = colors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            modifier = Modifier.padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.sm),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = VaaniSpacing.screenH, vertical = VaaniSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VaaniSpacing.md),
        ) {
            HeroCard()
            PendingCard()
            Column {
                DeviceRow("Firmware", "v1.0.3 · up to date", "Check")
                HairlineDivider()
                DeviceRow("Identify device", "Blink the LED to find it", "Blink")
                HairlineDivider()
                DeviceRow("Recording indicator", "LED always on while recording", "On")
                HairlineDivider()
                DeviceRow("Time sync", "Drift corrected on every connect", "±12 ms", mono = true)
            }
            UnpairRow()
        }
    }
}

@Composable
private fun HeroCard() {
    val colors = VaaniTheme.colors
    Column(
        Modifier.fillMaxWidth().background(VaaniPalette.Ink).padding(VaaniSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).border(VaaniSpacing.hairline, colors.coffeeLo),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.width(10.dp).height(28.dp).background(colors.coffee))
            }
            Column(Modifier.weight(1f).padding(start = VaaniSpacing.md)) {
                Text("Vaani One", color = VaaniPalette.Cream, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Row(
                    Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier.border(VaaniSpacing.hairline, Color(0xFF39352F)).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(8.dp).background(colors.success))
                            Text("Connected", color = VaaniPalette.Cream, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text("Last sync 2 min ago · BLE", color = VaaniPalette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = VaaniSpacing.lg)) {
            StatColumn("BATTERY", "82%", 0.82f, colors.success, Modifier.weight(1f))
            StatColumn("STORAGE", "6.1 / 32 GB", 0.19f, colors.coffee, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, progress: Float, barColor: Color, modifier: Modifier = Modifier) {
    Column(modifier.padding(end = VaaniSpacing.lg)) {
        Text(label, style = OverlineStyle, color = VaaniPalette.Muted)
        Text(value, color = VaaniPalette.Cream, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 2.dp, bottom = 6.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).background(VaaniPalette.DarkRaised)) {
            Box(Modifier.fillMaxWidth(progress).height(4.dp).background(barColor))
        }
    }
}

@Composable
private fun PendingCard() {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(colors.surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(VaaniSpacing.accentBar).fillMaxHeight().background(colors.coffee))
        Column(Modifier.weight(1f).padding(VaaniSpacing.lg)) {
            Text("Pending on device", color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("3 recordings · 47 MB · ready to sync", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        VaaniButton(label = "Sync now", onClick = {}, modifier = Modifier.padding(end = VaaniSpacing.md))
    }
}

@Composable
private fun DeviceRow(title: String, subtitle: String, action: String, mono: Boolean = false) {
    val colors = VaaniTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (mono) {
            Text(action, color = colors.muted, style = MonoStyle)
        } else {
            Text(action, color = colors.slate, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun UnpairRow() {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(VaaniSpacing.hairline, colors.danger)
            .clickable {}
            .padding(VaaniSpacing.lg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Unpair device", color = colors.danger, fontWeight = FontWeight.SemiBold)
        VaaniIconView(VaaniIcon.Trash, tint = colors.danger, size = 18.dp, modifier = Modifier.padding(start = 10.dp))
    }
}
