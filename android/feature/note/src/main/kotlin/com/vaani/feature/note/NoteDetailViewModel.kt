package com.vaani.feature.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.core.common.formatClock
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.domain.model.Note
import com.vaani.domain.model.TodoStatus
import com.vaani.domain.model.Transcript
import com.vaani.domain.model.TranscriptSegment
import com.vaani.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import javax.inject.Inject

const val NOTE_ID_ARG = "noteId"

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    repository: NotesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: String? = savedStateHandle[NOTE_ID_ARG]

    private val _uiState = MutableStateFlow(NoteDetailUiState(isLoading = true))
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    init {
        val id = noteId
        if (id == null) {
            // A missing nav arg is a navigation bug, not a silent fallback.
            _uiState.value = NoteDetailUiState(isLoading = false, isError = true)
        } else {
            viewModelScope.launch {
                combine(
                    repository.observeNote(id),
                    repository.observeTranscript(id),
                ) { note, transcript -> note to transcript }
                    .collect { (note, transcript) ->
                        _uiState.value = note?.toDetailState(transcript)
                            ?: NoteDetailUiState(isLoading = false, isError = true)
                    }
            }
        }
    }

    fun toggleTodo(id: String) {
        _uiState.update { state ->
            state.copy(
                todos = state.todos.map {
                    if (it.id == id) it.copy(done = !it.done) else it
                },
            )
        }
    }

    fun togglePlay() {
        _uiState.update { it.copy(player = it.player.copy(isPlaying = !it.player.isPlaying)) }
    }

    private fun Note.toDetailState(transcript: Transcript?): NoteDetailUiState {
        val segments = transcript?.segments.orEmpty()
        val startLocal = createdAt.toLocalDateTime(TimeZone.currentSystemDefault())
        val timeLabel = "%02d:%02d".format(startLocal.hour, startLocal.minute)
        return NoteDetailUiState(
            isLoading = false,
            isError = false,
            title = title,
            meta = "$timeLabel  ·  ${formatClock(durationMs)}  ·  $speakerCount speakers",
            tags = tags.map { "#${it.name}" },
            summary = summaryLong.ifEmpty { summaryShort },
            keyPoints = keyPoints.map {
                KeyPointRow(it.id, it.text, formatClock(it.sourceStartMs), it.sourceStartMs)
            },
            todos = todos.map {
                TodoRow(
                    id = it.id,
                    text = it.text,
                    subtitle = listOfNotNull(it.assignee, it.dueHint?.let { d -> "by $d" })
                        .joinToString(" · "),
                    done = it.status == TodoStatus.DONE,
                )
            },
            transcriptMeta = if (segments.isEmpty()) "" else "Hinglish · codemix",
            transcript = segments.map { it.toRow() },
            player = PlayerState(
                positionLabel = formatClock(0),
                durationLabel = formatClock(durationMs),
                progress = 0f,
                isPlaying = false,
            ),
        )
    }

    private fun TranscriptSegment.toRow(): TranscriptRow {
        val variant = when (speakerId) {
            "S1" -> ChipVariant.Coffee
            "S2" -> ChipVariant.Slate
            else -> ChipVariant.Sunken
        }
        return TranscriptRow(
            id = id,
            speaker = speakerId,
            speakerVariant = variant,
            text = text,
            timeLabel = formatClock(startMs),
            seekMs = startMs,
        )
    }
}
