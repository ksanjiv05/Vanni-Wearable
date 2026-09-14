package com.vaani.feature.note

import com.vaani.core.designsystem.component.ChipVariant

/** Immutable UI state for the Note detail screen. */
data class NoteDetailUiState(
    val isLoading: Boolean = true,
    val isError: Boolean = false,
    val title: String = "",
    val meta: String = "",
    val tags: List<String> = emptyList(),
    val summary: String = "",
    val keyPoints: List<KeyPointRow> = emptyList(),
    val todos: List<TodoRow> = emptyList(),
    val transcriptMeta: String = "",
    val transcript: List<TranscriptRow> = emptyList(),
    val player: PlayerState = PlayerState(),
)

data class KeyPointRow(
    val id: String,
    val text: String,
    val seekLabel: String,
    val seekMs: Long,
)

data class TodoRow(
    val id: String,
    val text: String,
    val subtitle: String,
    val done: Boolean,
)

data class TranscriptRow(
    val id: String,
    val speaker: String,
    val speakerVariant: ChipVariant,
    val text: String,
    val timeLabel: String,
    val seekMs: Long,
)

data class PlayerState(
    val positionLabel: String = "00:00",
    val durationLabel: String = "00:00",
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
)
