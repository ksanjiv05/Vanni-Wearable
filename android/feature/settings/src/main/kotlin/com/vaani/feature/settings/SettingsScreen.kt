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
import androidx.compose.runtime.setValue
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
    onManageModels: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        state = state,
        onBatterySaver = viewModel::setBatterySaver,
        onLocalOnly = viewModel::setLocalOnly,
        onAsrBackend = viewModel::setAsrBackend,
        onEnrichBackend = viewModel::setEnrichBackend,
        onManageModels = onManageModels,
        onSaveApiKey = viewModel::setApiKey,
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
    onManageModels: () -> Unit = {},
    onSaveApiKey: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = VaaniTheme.colors
    val showMessage = com.vaani.core.designsystem.LocalShowMessage.current
    var showKeyDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
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

        SectionLabel("ACTIVE ENGINES")
        ActiveEnginesCard(state)

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
        HairlineDivider()
        NavRow(
            "On-device models",
            "Download & manage local ASR / LLM models",
            chevron = true,
            onClick = onManageModels,
        )

        SectionLabel("SARVAM API")
        ApiKeyCard(
            present = state.apiKeyPresent,
            masked = state.apiKeyMasked,
            onManage = { showKeyDialog = true },
        )

        SectionLabel("PROCESSING")
        ToggleRow("Battery & cost saver", "Skip silence with on-device VAD", state.batterySaver, onBatterySaver)
        HairlineDivider()
        ToggleRow("Local-only mode", "Never send audio; queue for later", state.localOnly, onLocalOnly)

        SectionLabel("DATA")
        NavRow("Export everything", "Notes + audio as JSON + files", chevron = true) { showMessage("Export lands in a later milestone") }
        Box(Modifier.height(VaaniSpacing.sm))
        DeleteRow(onDelete = { showMessage("Delete everything — confirm dialog lands in a later milestone") })
        Box(Modifier.height(VaaniSpacing.xxl))
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                onSaveApiKey(key)
                showKeyDialog = false
                showMessage(if (key.isBlank()) "Sarvam key cleared" else "Sarvam key saved")
            },
        )
    }
}

/** Shows which backend is actually driving each stage right now (real state). */
@Composable
private fun ActiveEnginesCard(state: SettingsUiState) {
    val colors = VaaniTheme.colors
    fun label(b: AiBackend, modelName: String?) = when (b) {
        // Name the REAL on-device model per stage (Whisper for ASR, the chosen LLM
        // for enrichment) — never hardcode Whisper for both.
        AiBackend.LOCAL -> modelName?.let { "On-device · $it" } ?: "On-device · no model installed"
        AiBackend.SARVAM -> if (state.apiKeyPresent) "Sarvam API" else "Sarvam API · no key"
    }
    Column(Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.lg)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Speech-to-text", color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
            Text(label(state.asrBackend, state.asrModelName), color = colors.ink, style = MaterialTheme.typography.titleSmall)
        }
        Box(Modifier.height(VaaniSpacing.sm))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Enrichment", color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
            Text(label(state.enrichBackend, state.enrichModelName), color = colors.ink, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun ApiKeyDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = VaaniTheme.colors
    var text by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.lg),
        ) {
            Text("Sarvam API key", color = colors.ink, style = MaterialTheme.typography.titleMedium)
            Text(
                "Billed to your own Sarvam account. Stored on-device.",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 4.dp, bottom = VaaniSpacing.md),
            )
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = MonoStyle.copy(color = colors.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.sunken)
                    .border(VaaniSpacing.hairline, colors.hairline)
                    .padding(VaaniSpacing.md),
            )
            Row(Modifier.fillMaxWidth().padding(top = VaaniSpacing.md), horizontalArrangement = Arrangement.spacedBy(VaaniSpacing.md)) {
                VaaniOutlineButton(label = "Cancel", onClick = onDismiss, modifier = Modifier.weight(1f))
                Box(
                    Modifier.weight(1f).height(44.dp).background(colors.coffee).clickable { onSave(text) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Save", color = colors.onCoffee, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
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
private fun ApiKeyCard(present: Boolean, masked: String, onManage: () -> Unit) {
    val colors = VaaniTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(colors.surface)
            .clickable(onClick = onManage),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(VaaniSpacing.accentBar).fillMaxHeight().background(if (present) colors.coffee else colors.hairline))
        Column(Modifier.weight(1f).padding(VaaniSpacing.lg)) {
            Text("Sarvam API key", color = colors.ink, style = MaterialTheme.typography.titleMedium)
            Text(masked, color = colors.muted, style = MonoStyle, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(10.dp).background(if (present) colors.success else colors.muted))
                Text(
                    if (present) "Billed to your account" else "Optional — on-device works without it",
                    color = colors.secondary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        Text(if (present) "Change" else "Add", color = colors.slate, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = VaaniSpacing.lg))
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
