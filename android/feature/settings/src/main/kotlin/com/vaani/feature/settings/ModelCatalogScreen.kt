package com.vaani.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.core.designsystem.component.StatusChip
import com.vaani.core.designsystem.component.VaaniButton
import com.vaani.core.designsystem.component.VaaniOutlineButton
import com.vaani.core.designsystem.icon.VaaniIcon
import com.vaani.core.designsystem.icon.VaaniIconView
import com.vaani.core.designsystem.theme.OverlineStyle
import com.vaani.core.designsystem.theme.VaaniSpacing
import com.vaani.core.designsystem.theme.VaaniTheme
import com.vaani.domain.ai.InstallStatus
import com.vaani.domain.ai.Suitability

/**
 * On-device model catalog (ADR-001 §4-5). Shows ASR / LLM / embedding models,
 * badges the best fit for this phone, explains heavier/unsupported ones, and
 * downloads with live progress. Sharp corners throughout (design-system rule).
 */
@Composable
fun ModelCatalogScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModelCatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hasHfToken by viewModel.hasHfToken.collectAsStateWithLifecycle()
    val tokenPrompt by viewModel.tokenPrompt.collectAsStateWithLifecycle()
    val colors = VaaniTheme.colors
    val showMessage = com.vaani.core.designsystem.LocalShowMessage.current

    LazyColumn(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = VaaniSpacing.screenH),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = VaaniSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.clickable(onClick = onBack).padding(end = VaaniSpacing.sm)) {
                    VaaniIconView(VaaniIcon.ChevronLeft, tint = colors.ink, size = 24.dp)
                }
                Text("On-device models", color = colors.ink, style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                state.deviceSummary,
                color = colors.secondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(bottom = VaaniSpacing.md),
            )
            if (viewModel.hasGatedModels) {
                HfTokenCard(hasToken = hasHfToken, onManage = { viewModel.openTokenPrompt() })
            }
        }

        section("SPEECH-TO-TEXT", state.asr, viewModel)
        section("NOTE ENRICHMENT (LOCAL LLM)", state.llm, viewModel)
        section("SEARCH EMBEDDINGS", state.embedding, viewModel)

        item { Box(Modifier.height(VaaniSpacing.xxl)) }
    }

    if (tokenPrompt != null) {
        HfTokenDialog(
            forDownload = tokenPrompt?.pendingModelId != null,
            onDismiss = { viewModel.dismissTokenPrompt() },
            onSave = { token ->
                viewModel.saveHfToken(token)
                showMessage(if (token.isBlank()) "Hugging Face token cleared" else "Hugging Face token saved")
            },
        )
    }
}

/** Explains + captures the HF token that unlocks license-gated models (e.g. Gemma). */
@Composable
private fun HfTokenCard(hasToken: Boolean, onManage: () -> Unit) {
    val colors = VaaniTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(VaaniSpacing.hairline, colors.hairline)
            .clickable(onClick = onManage)
            .padding(VaaniSpacing.lg),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Gated models (Gemma)",
                color = colors.ink,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                if (hasToken) "Token set" else "Token needed",
                if (hasToken) ChipVariant.Sage else ChipVariant.Sunken,
            )
        }
        Text(
            if (hasToken)
                "A Hugging Face token is saved. You can download gated models you've licensed."
            else
                "Some models (e.g. Gemma) need a free Hugging Face token from an account that accepted the model's license. Tap to add it.",
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            if (hasToken) "Change token" else "Add token & how-to",
            color = colors.slate,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HfTokenDialog(forDownload: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val colors = VaaniTheme.colors
    var text by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().background(colors.surface).padding(VaaniSpacing.lg),
        ) {
            Text(
                if (forDownload) "License required" else "Hugging Face token",
                color = colors.ink,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                (if (forDownload) "This model is license-gated. To download it:\n" else "To download a gated model (e.g. Gemma):\n") +
                    "1. Open the model page on huggingface.co and click \"Agree and access\".\n" +
                    "2. Settings → Access Tokens → New token (Read).\n" +
                    "3. Paste it below. Stored securely on-device; sent only to huggingface.co.",
                color = colors.muted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = 6.dp, bottom = VaaniSpacing.md),
            )
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = com.vaani.core.designsystem.theme.MonoStyle.copy(color = colors.ink),
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

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<ModelRowState>,
    viewModel: ModelCatalogViewModel,
) {
    if (rows.isEmpty()) return
    item {
        Text(
            title,
            style = OverlineStyle,
            color = VaaniTheme.colors.muted,
            modifier = Modifier.padding(top = VaaniSpacing.lg, bottom = VaaniSpacing.sm),
        )
    }
    items(rows, key = { it.model.id }) { row ->
        ModelCard(
            row = row,
            onDownload = { viewModel.download(row.model.id) },
            onPause = { viewModel.pause(row.model.id) },
            onCancel = { viewModel.cancel(row.model.id) },
            onDelete = { viewModel.delete(row.model.id) },
            onUse = { viewModel.useModel(row.model) },
        )
        Box(Modifier.height(VaaniSpacing.sm))
    }
}

@Composable
private fun ModelCard(
    row: ModelRowState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    val colors = VaaniTheme.colors
    val model = row.model
    val accent = if (row.isRecommended) colors.coffee else colors.hairline

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(if (row.isRecommended) 2.dp else VaaniSpacing.hairline, accent)
            .padding(VaaniSpacing.lg),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(model.displayName, color = colors.ink, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (row.isRecommended) StatusChip("Recommended", ChipVariant.Coffee)
        }
        Text(
            "${model.family} · ${model.quant} · ${model.downloadMb} MB",
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            model.tagline,
            color = colors.secondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = VaaniSpacing.sm),
        )
        Text(
            "✓ ${model.strengths}",
            color = colors.secondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = VaaniSpacing.xs),
        )
        Text(
            "! ${model.limits}",
            color = colors.muted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )

        // Fit line (why recommended / heavier / unsupported).
        FitLine(row)

        Box(Modifier.height(VaaniSpacing.md))
        InstallControl(row, onDownload, onPause, onCancel, onDelete, onUse)
    }
}

@Composable
private fun FitLine(row: ModelRowState) {
    val colors = VaaniTheme.colors
    val (label, variant) = when (row.suitability) {
        Suitability.RECOMMENDED -> "Best fit" to ChipVariant.Sage
        Suitability.SUPPORTED -> "Supported" to ChipVariant.Slate
        Suitability.NOT_RECOMMENDED -> "Heavy for this phone" to ChipVariant.Sunken
        Suitability.UNSUPPORTED -> "Not supported" to ChipVariant.Danger
    }
    Row(
        Modifier.fillMaxWidth().padding(top = VaaniSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VaaniSpacing.sm),
    ) {
        StatusChip(label, variant)
        Text(row.suitabilityReason, color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
    }
}

@Composable
private fun InstallControl(
    row: ModelRowState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onUse: () -> Unit,
) {
    val colors = VaaniTheme.colors
    when (row.install.status) {
        InstallStatus.INSTALLED -> Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(if (row.isActive) "In use" else "Installed", if (row.isActive) ChipVariant.Coffee else ChipVariant.Sage)
                Box(Modifier.weight(1f))
                VaaniOutlineButton("Remove", onClick = onDelete, strokeColor = colors.danger)
            }
            // Model picker: only when this role has >1 installed model to choose from.
            if (row.selectable && !row.isActive) {
                Box(Modifier.height(VaaniSpacing.sm))
                VaaniButton(label = "Use this model", onClick = onUse, modifier = Modifier.fillMaxWidth())
            } else if (row.selectable && row.isActive) {
                Box(Modifier.height(VaaniSpacing.xs))
                Text("Active for ${roleLabel(row.model.role)} — auto-selected unless you pick another.",
                    color = colors.muted, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Normal)
            }
        }
        InstallStatus.DOWNLOADING, InstallStatus.QUEUED -> Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Downloading ${(row.install.progress * 100).toInt()}%",
                    color = colors.coffee, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                Text("Pause", color = colors.slate, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onPause).padding(end = VaaniSpacing.md))
                Text("Cancel", color = colors.danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onCancel))
            }
            ProgressBar(row.install.progress)
        }
        InstallStatus.PAUSED -> Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Paused · ${(row.install.progress * 100).toInt()}%",
                    color = colors.muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
                )
                Text("Resume", color = colors.coffee, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDownload).padding(end = VaaniSpacing.md))
                Text("Cancel", color = colors.danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onCancel))
            }
            ProgressBar(row.install.progress)
        }
        InstallStatus.EXTRACTING -> Text("Extracting…", color = colors.coffee, style = MaterialTheme.typography.labelLarge)
        InstallStatus.VERIFYING -> Text("Verifying…", color = colors.coffee, style = MaterialTheme.typography.labelLarge)
        InstallStatus.UNAVAILABLE -> VaaniOutlineButton(
            label = row.install.detail ?: "Coming soon",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        InstallStatus.FAILED -> Column {
            Text(row.install.detail ?: "Download failed", color = colors.danger, style = MaterialTheme.typography.labelLarge)
            Box(Modifier.height(VaaniSpacing.xs))
            downloadButton(row, onDownload)
        }
        InstallStatus.NOT_INSTALLED -> downloadButton(row, onDownload)
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val colors = VaaniTheme.colors
    Box(Modifier.fillMaxWidth().height(6.dp).padding(top = VaaniSpacing.xs).background(colors.sunken)) {
        Box(Modifier.fillMaxWidth(progress).height(6.dp).background(colors.coffee))
    }
}

@Composable
private fun downloadButton(row: ModelRowState, onDownload: () -> Unit) {
    val enabled = row.suitability != Suitability.UNSUPPORTED
    val label = if (enabled) "Download · ${row.model.downloadMb} MB" else "Not supported on this phone"
    VaaniButton(label = label, onClick = onDownload, enabled = enabled, modifier = Modifier.fillMaxWidth())
}

private fun roleLabel(role: com.vaani.domain.ai.ModelRole): String = when (role) {
    com.vaani.domain.ai.ModelRole.ASR -> "transcription"
    com.vaani.domain.ai.ModelRole.LLM -> "note enrichment"
    com.vaani.domain.ai.ModelRole.EMBEDDING -> "search"
}
