package com.vaani.core.ui

import com.vaani.domain.model.PipelineState
import com.vaani.core.designsystem.component.ChipVariant

/** Maps a pipeline state to a status-chip label + variant per the mockups. */
fun PipelineState.chip(): Pair<String, ChipVariant> = when (this) {
    PipelineState.READY -> "Ready" to ChipVariant.Sage
    PipelineState.TRANSCRIBING -> "Transcribing" to ChipVariant.Coffee
    PipelineState.ENRICHING -> "Enriching" to ChipVariant.Coffee
    PipelineState.QUEUED -> "Queued" to ChipVariant.Sunken
    PipelineState.FAILED -> "Failed" to ChipVariant.Slate
}
