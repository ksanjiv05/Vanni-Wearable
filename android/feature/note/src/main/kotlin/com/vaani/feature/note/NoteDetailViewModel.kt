package com.vaani.feature.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.core.common.formatClock
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.domain.audio.AudioPlayer
import com.vaani.domain.model.Note
import com.vaani.domain.model.TodoStatus
import com.vaani.domain.model.Transcript
import com.vaani.domain.model.TranscriptSegment
import com.vaani.domain.repository.NotesRepository
import com.vaani.domain.repository.RecordingStore
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
    private val repository: NotesRepository,
    private val writer: com.vaani.domain.repository.NotesWriter,
    private val recordings: RecordingStore,
    private val audioPlayer: AudioPlayer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: String? = savedStateHandle[NOTE_ID_ARG]

    private val _uiState = MutableStateFlow(NoteDetailUiState(isLoading = true))
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    /** True once the recording's audio file has been handed to the player. */
    private var audioLoaded = false

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
                        // Load the real audio file into the player once we know the
                        // recording (its content-addressed path is the playback source).
                        if (note != null && !audioLoaded) loadAudio(note.recordingId)
                    }
            }
            // Reflect real Media3 playback state (position/duration/isPlaying) live.
            viewModelScope.launch {
                audioPlayer.state.collect { p ->
                    _uiState.update {
                        it.copy(
                            player = it.player.copy(
                                isPlaying = p.isPlaying,
                                positionLabel = formatClock(p.positionMs),
                                durationLabel = formatClock(p.durationMs.coerceAtLeast(0)),
                                progress = if (p.durationMs > 0) {
                                    (p.positionMs.toFloat() / p.durationMs).coerceIn(0f, 1f)
                                } else 0f,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadAudio(recordingId: String) {
        val uri = recordings.get(recordingId)?.storageUri ?: return
        audioLoaded = true
        audioPlayer.load(uri)
    }

    fun toggleTodo(id: String) {
        // Persist to Room; the note flow re-emits with the new status, so no local
        // UI-state mutation is needed (and it survives navigation/relaunch).
        val current = _uiState.value.todos.firstOrNull { it.id == id }?.done ?: false
        val newStatus = if (current) com.vaani.domain.model.TodoStatus.OPEN else com.vaani.domain.model.TodoStatus.DONE
        val completedAt = if (newStatus == com.vaani.domain.model.TodoStatus.DONE) System.currentTimeMillis() else null
        viewModelScope.launch { writer.setTodoStatus(id, newStatus, completedAt) }
    }

    fun togglePlay() {
        if (_uiState.value.player.isPlaying) audioPlayer.pause() else audioPlayer.play()
    }

    override fun onCleared() {
        // AudioPlayer is an app-scoped singleton — do NOT release it here or a
        // second note-open gets a dead player (0:00, no sound). Just stop playback.
        audioPlayer.pause()
        super.onCleared()
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
