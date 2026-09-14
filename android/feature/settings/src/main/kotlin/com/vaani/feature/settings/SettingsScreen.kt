package com.vaani.feature.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.component.HairlineDivider
import com.vaani.core.designsystem.component.VaaniOutlineButton
import com.vaani.core.designsystem.component.VaaniToggle
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.MonoStyle
import com.vaani.core.designsystem.theme.OverlineStyle
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme
import com.vaani.domain.ai.AiBackend

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onBatterySaver = viewModel::setBatterySaver,
        onLocalOnly = viewModel::setLocalOnly,
        onAsrBackend = viewModel::setAsrBackend,
        onEnrichBackend = viewModel::setEnrichBackend,
        modifier = modifier,
    )
}

@Composable
internal fun SettingsContent(
    state: SettingsUiState,
    onBatterySaver: (Boolean) -> Unit,
    onLocalOnly: (Boolean) -> Unit,
    onAsrBackend: (AiBackend) -> Unit = {},
    onEnrichBackend: (AiBackend) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    val showMessage = com.vaani.core.designsystem.LocalShowMessage.current
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = VaaniSpacing.screenH),
    ) {
        Text(
            "Settings",
            color = colors.ink,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.padding(vertical = VaaniSpacing.md),
        )

        ApiKeyCard(state.apiKeyMasked, onManage = { showMessage("API key management lands with the key vault") })
        Box(Modifier.height(VaaniSpacing.md))
        BudgetCard(state, onAdjustCap = { showMessage("Budget cap editor lands in a later milestone") })

        SectionLabel("AI ENGINE")
        EnginePicker(
            title = "Speech-to-text",
            subtitle = "On-device is free & private; API is higher accuracy",
            selected = state.asrBackend,
            onSelect = onAsrBackend,
        )
        HairlineDivider()
        EnginePicker(
            title = "Note enrichment",
            subtitle = "Summaries & to-dos — on-device or Sarvam",
            selected = state.enrichBackend,
            onSelect = onEnrichBackend,
        )

        SectionLabel("PROCESSING")
        NavRow("Transcription quality", state.transcriptionQuality, "Best") { showMessage("Quality options land in a later milestone") }
        HairlineDivider()
        NavRow("Default mode", state.defaultMode, "Codemix") { showMessage("Mode options land in a later milestone") }
        HairlineDivider()
        ToggleRow("Battery & cost saver", "Skip silence with on-device VAD", state.batterySaver, onBatterySaver)
        HairlineDivider()
        ToggleRow("Local-only mode", "Never send audio; queue for later", state.localOnly, onLocalOnly)

        SectionLabel("DATA")
        NavRow("Export everything", "Notes + audio as JSON + files", chevron = true) { showMessage("Export lands in a later milestone") }
        Box(Modifier.height(VaaniSpacing.sm))
        DeleteRow(onDelete = { showMessage("Delete everything — confirm dialog lands in a later milestone") })
        Box(Modifier.height(VaaniSpacing.xxl))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = OverlineStyle, color = VaaniTheme.colors.muted, modifier = Modifier.padding(top = VaaniSpacing.lg, bottom = VaaniSpacing.sm))
}

/**
 * Per-stage AI backend selector (ADR-001). A sharp-cornered two-segment
 * control: On-device (Local) vs API (Sarvam). Selected segment fills with
 * coffee; unselected is a hairline-bordered surface. No rounded corners.
 */
@Composable
private fun EnginePicker(
    title: String,
    subtitle: String,
    selected: AiBackend,
    onSelect: (AiBackend) -> Unit,
) {
    val colors = VaaniTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.lg)) {
        Text(title, color = colors.ink, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = VaaniSpacing.md)) {
            EngineSegment("On-device", selected == AiBackend.LOCAL, Modifier.weight(1f)) {
                onSelect(AiBackend.LOCAL)
            }
            Box(Modifier.width(VaaniSpacing.sm))
            EngineSegment("API (Sarvam)", selected == AiBackend.SARVAM, Modifier.weight(1f)) {
                onSelect(AiBackend.SARVAM)
            }
        }
    }
}

@Composable
private fun EngineSegment(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = VaaniTheme.colors
    Box(
        modifier
            .height(44.dp)
            .background(if (active) colors.coffee else colors.surface)
            .border(VaaniSpacing.hairline, if (active) colors.coffee else colors.hairline)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) colors.onCoffee else colors.secondary,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ApiKeyCard(masked: String, onManage: () -> Unit) {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(colors.surface)
            .clickable(onClick = onManage),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(VaaniSpacing.accentBar).fillMaxHeight().background(colors.coffee))
        Column(Modifier.weight(1f).padding(VaaniSpacing.lg)) {
            Text("Sarvam API key", color = colors.ink, style = MaterialTheme.typography.titleMedium)
            Text(masked, color = colors.muted, style = MonoStyle, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(colors.success))
                Text("Billed to your account", color = colors.secondary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
            }
        }
        Text("Manage", color = colors.slate, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = VaaniSpacing.lg))
    }
}

@Composable
private fun BudgetCard(state: SettingsUiState, onAdjustCap: () -> Unit) {
    val colors = VaaniTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.lg)) {
        Text("MONTHLY BUDGET", style = OverlineStyle, color = colors.muted)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(state.budgetSpentLabel, color = colors.ink, style = MaterialTheme.typography.displaySmall)
            Text(
                "  ${state.budgetCapLabel}",
                color = colors.muted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(6.dp).background(colors.sunken)) {
            Box(Modifier.fillMaxWidth(state.budgetProgress).height(6.dp).background(colors.coffee))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(state.budgetDetail, color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
            VaaniOutlineButton(label = "Adjust cap", onClick = onAdjustCap)
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, action: String = "", chevron: Boolean = false, onClick: () -> Unit = {}) {
    val colors = VaaniTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.surface).clickable(onClick = onClick).padding(VaaniSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
        }
        if (action.isNotEmpty()) {
            Text(action, color = colors.slate, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
        }
        if (chevron || action.isNotEmpty()) {
            VaaniIconView(VaaniIcon.ChevronRight, tint = colors.muted, size = 20.dp)
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = VaaniTheme.colors
    Row(
        Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
        }
        VaaniToggle(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun DeleteRow(onDelete: () -> Unit) {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(VaaniSpacing.hairline, colors.danger)
            .clickable(onClick = onDelete)
            .padding(VaaniSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VaaniIconView(VaaniIcon.Trash, tint = colors.danger, size = 20.dp)
        Column(Modifier.weight(1f).padding(start = VaaniSpacing.md)) {
            Text("Delete everything", color = colors.danger, style = MaterialTheme.typography.titleMedium)
            Text("Wipes notes, audio, vectors & key", color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
