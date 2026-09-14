package com.vaani.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.VaaniButton
import com.vaani.core.designsystem.component.VaaniCheckbox
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme

/**
 * Onboarding — brand, hero waveform, 3-step setup, privacy strip (screen 01).
 * Stateless: navigation actions are hoisted. Milestone A shows the visual
 * flow; step actions land with pairing / key vault in a later milestone.
 */
@Composable
fun OnboardingScreen(
    onPair: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VaaniSpacing.screenH),
    ) {
        // Brand row
        Row(
            Modifier.fillMaxWidth().padding(top = VaaniSpacing.lg, bottom = VaaniSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("VAANI", color = colors.ink, style = MaterialTheme.typography.headlineLarge, letterSpacing = 2.sp)
            Text(
                "beta",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 10.dp),
            )
            Box(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.size(14.dp).background(colors.coffee))
                Box(Modifier.size(14.dp).background(colors.coffeeHi))
                Box(Modifier.size(14.dp).background(colors.sunken))
            }
        }

        HeroWaveform()

        Text(
            "Your voice,",
            color = colors.ink,
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(top = VaaniSpacing.xl),
        )
        Text("quietly organised.", color = colors.coffee, style = MaterialTheme.typography.displayLarge)
        Text(
            "Record with your Vaani device. It syncs to your phone, transcribes on " +
                "Sarvam, and turns every conversation into searchable notes — all " +
                "indexed privately, on-device.",
            color = colors.secondary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = VaaniSpacing.md),
        )

        Spacer(Modifier.height(VaaniSpacing.xl))

        StepRow(1, done = true, title = "Pair your device", subtitle = "Bluetooth · secure numeric match")
        HairlineDivider()
        StepRow(2, done = false, title = "Add your Sarvam key", subtitle = "Billed to your own account")
        HairlineDivider()
        StepRow(3, done = false, title = "Set recording consent", subtitle = "Know the law where you record", trailingCheckbox = true)

        Spacer(Modifier.height(VaaniSpacing.xl))

        VaaniButton(label = "Pair your device", onClick = onPair, modifier = Modifier.fillMaxWidth())
        Box(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSkip)
                .padding(vertical = VaaniSpacing.md),
            contentAlignment = Alignment.Center,
        ) {
            Text("I'll set up later", color = colors.secondary, fontWeight = FontWeight.SemiBold)
        }
        HairlineDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = VaaniSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VaaniIconView(VaaniIcon.Shield, tint = colors.muted, size = 16.dp)
            Text("Audio is encrypted on device · search stays offline", color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
        }
        Spacer(Modifier.height(VaaniSpacing.xl))
    }
}

@Composable
private fun HeroWaveform() {
    val colors = VaaniTheme.colors
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(colors.ink)
            .padding(10.dp),
    ) {
        Box(Modifier.fillMaxSize().background(colors.surface)) {
            // REC chip
            Box(
                Modifier.padding(12.dp).background(colors.coffee).padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text("REC", color = colors.onCoffee, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            // Bars
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val heights = listOf(28, 46, 70, 40, 96, 120, 60, 84, 50, 108, 44, 72, 90, 36, 64, 100, 52, 78)
                heights.forEachIndexed { i, h ->
                    val c = when (i % 3) {
                        0 -> colors.coffee
                        1 -> colors.slate
                        else -> colors.coffeeLo
                    }
                    Box(Modifier.weight(1f).height(h.dp).background(c))
                }
            }
        }
    }
}

@Composable
private fun StepRow(
    number: Int,
    done: Boolean,
    title: String,
    subtitle: String,
    trailingCheckbox: Boolean = false,
) {
    val colors = VaaniTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).background(if (done) colors.coffee else colors.sunken),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                number.toString(),
                color = if (done) colors.onCoffee else colors.secondary,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(Modifier.weight(1f).padding(start = VaaniSpacing.md)) {
            Text(title, color = colors.ink, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
        }
        if (trailingCheckbox) {
            VaaniCheckbox(checked = false, onCheckedChange = {}, boxSize = 24.dp)
        } else {
            VaaniIconView(VaaniIcon.ChevronRight, tint = colors.muted, size = 22.dp)
        }
    }
}
