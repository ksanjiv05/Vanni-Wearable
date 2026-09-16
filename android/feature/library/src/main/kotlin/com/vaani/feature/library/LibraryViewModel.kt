package com.vaani.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.core.common.formatBytes
import com.vaani.core.common.formatClock
import com.vaani.core.ui.chip
import com.vaani.domain.model.Note
import com.vaani.domain.model.PipelineState
import com.vaani.domain.repository.NotesRepository
import com.vaani.domain.repository.ProcessingRecording
import com.vaani.domain.repository.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class LibraryViewModel @Inject constructor(
    repository: NotesRepository,
    private val enqueuer: com.vaani.domain.pipeline.PipelineEnqueuer,
) : ViewModel() {

    /** Re-run the ingest pipeline for a failed recording (tap-to-retry). */
    fun retry(recordingId: String) {
        viewModelScope.launch { enqueuer.enqueue(recordingId) }
    }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            repository.observeNotes(),
            repository.syncStatus(),
            repository.observeProcessing(),
        ) { notes, sync, processing ->
            buildState(notes, sync, processing)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState(isLoading = true),
        )

    private fun buildState(
        notes: List<Note>,
        sync: SyncStatus,
        processing: List<ProcessingRecording>,
    ): LibraryUiState {
        val totalMs = notes.sumOf { it.durationMs }
        // Day-bucket reference is derived from the emitted notes, not a fixture.
        val newestDate = notes.maxByOrNull { it.createdAt }
            ?.createdAt
            ?.toLocalDateTime(TimeZone.currentSystemDefault())
            ?.date
        val groups = notes
            .groupBy { dayBucket(it.createdAt, newestDate) }
            .map { (header, groupNotes) -> DayGroup(header, groupNotes.map { it.toRow() }) }

        return LibraryUiState(
            isLoading = false,
            notesCount = notes.size,
            recordedLabel = "%.1f h recorded".format(Locale.US, totalMs / 3_600_000.0),
            syncedLabel = sync.lastSyncedLabel,
            sync = if (sync.isSyncing) {
                SyncBannerState(
                    title = "Syncing from device...",
                    detail = "${sync.pendingRecordings} recordings · " +
                        "${formatBytes(sync.pendingBytes)} · over ${sync.transport}",
                    progress = sync.progress,
                    percentLabel = "${(sync.progress * 100).roundToInt()}%",
                )
            } else null,
            processing = processing.map { it.toProcessingRow() },
            groups = groups,
        )
    }

    private fun ProcessingRecording.toProcessingRow(): ProcessingRow {
        val (label, variant) = state.chip()
        return ProcessingRow(
            recordingId = recordingId,
            title = title,
            statusLabel = label,
            statusVariant = variant,
            meta = if (state == PipelineState.FAILED) {
                "${formatBytes(bytes)}  ·  tap to retry"
            } else {
                "${formatBytes(bytes)}  ·  processing on-device"
            },
            isFailed = state == PipelineState.FAILED,
        )
    }

    private fun Note.toRow(): NoteRow {
        val (label, variant) = pipelineState.chip()
        val metaParts = buildList {
            add(formatClock(durationMs))
            if (speakerCount > 0) add("$speakerCount speakers")
            tags.firstOrNull()?.let { add("#${it.name}") }
        }
        val statusLabel = pipelineProgress.let { progress ->
            if (pipelineState == PipelineState.TRANSCRIBING && progress != null) {
                "Transcribing ${(progress * 100).roundToInt()}%"
            } else {
                label
            }
        }
        return NoteRow(
            id = id,
            title = title,
            snippet = summaryShort,
            meta = metaParts.joinToString("  ·  "),
            durationLabel = formatClock(durationMs),
            statusLabel = statusLabel,
            statusVariant = variant,
            todoCount = todos.size,
        )
    }

    /** Buckets by calendar day relative to the newest note in the emitted list. */
    private fun dayBucket(instant: Instant, newestDate: LocalDate?): String {
        val tz = TimeZone.currentSystemDefault()
        val date = instant.toLocalDateTime(tz).date
        val today = newestDate ?: date
        return when (today.toEpochDays() - date.toEpochDays()) {
            0 -> "TODAY"
            1 -> "YESTERDAY"
            else -> "${date.dayOfMonth} ${date.month.name.take(3)}"
        }
    }
}
