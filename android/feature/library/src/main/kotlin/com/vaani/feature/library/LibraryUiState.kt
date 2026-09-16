package com.vaani.feature.library

/** Immutable UI state for the Library screen. */
data class LibraryUiState(
    val isLoading: Boolean = true,
    val notesCount: Int = 0,
    val recordedLabel: String = "",
    val syncedLabel: String = "",
    val sync: SyncBannerState? = null,
    val processing: List<ProcessingRow> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
)

/** An in-flight or failed recording shown above the notes so it's never invisible. */
data class ProcessingRow(
    val recordingId: String,
    val title: String,
    val statusLabel: String,
    val statusVariant: com.vaani.core.designsystem.component.ChipVariant,
    val meta: String,
    val isFailed: Boolean,
)

/** A day-grouped section of note rows (TODAY / YESTERDAY / date). */
data class DayGroup(
    val header: String,
    val notes: List<NoteRow>,
)

/** A single note card's presentation model. */
data class NoteRow(
    val id: String,
    val title: String,
    val snippet: String,
    val meta: String,
    val durationLabel: String,
    val statusLabel: String,
    val statusVariant: com.vaani.core.designsystem.component.ChipVariant,
    val todoCount: Int,
)

/** Pending-sync banner state (charcoal banner with progress). */
data class SyncBannerState(
    val title: String,
    val detail: String,
    val progress: Float,
    val percentLabel: String,
)
