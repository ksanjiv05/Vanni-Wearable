package com.vaani.data.pipeline

import com.vaani.domain.ai.AsrOptions
import com.vaani.domain.ai.AudioRef
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.ai.EngineRouter
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.ai.NoteExtraction
import com.vaani.domain.model.KeyPoint
import com.vaani.domain.model.Note
import com.vaani.domain.model.Outcome
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.Priority
import com.vaani.domain.model.Tag
import com.vaani.domain.model.Todo
import com.vaani.domain.model.TodoStatus
import com.vaani.domain.model.Transcript
import com.vaani.domain.model.TranscriptSegment
import com.vaani.domain.repository.NotesWriter
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The durable ingest pipeline (§5). Drives one recording through the two
 * pluggable AI stages and persists the result:
 *
 *   QUEUED → [ASR via EngineRouter] → TRANSCRIBING
 *          → [Enrich via EngineRouter] → ENRICHING
 *          → persist Note+Transcript → READY   (or FAILED on any Err)
 *
 * The engines are resolved per-run from the user's setting, so the same
 * pipeline transparently uses on-device or Sarvam without any branching here.
 * State is pushed to [NotesWriter] at each transition so the Library/Note UI
 * reflects progress live.
 *
 * This is the in-process core; the WorkManager/foreground-service durability
 * wrapper (§5.7) composes over it and is added with the background layer.
 */
@Singleton
class IngestPipeline @Inject constructor(
    private val router: EngineRouter,
    private val writer: NotesWriter,
    private val clock: Clock = Clock.System,
) {

    /**
     * Process a single recording end-to-end. Returns the persisted [Note] on
     * success. Any stage failure marks the note FAILED and returns the error;
     * the recording row is never left in a half-transcribed limbo.
     */
    suspend fun process(audio: AudioRef, opts: PipelineOptions = PipelineOptions()): Outcome<Note> {
        writer.setRecordingPipelineState(audio.recordingId, PipelineState.TRANSCRIBING)

        val transcript = when (val r = router.asr().transcribe(audio, opts.asr) { p ->
            // progress hook — the note may not exist yet, so this is best-effort.
        }) {
            is Outcome.Ok -> r.value
            is Outcome.Err -> return fail(audio.recordingId, r)
        }

        writer.setRecordingPipelineState(audio.recordingId, PipelineState.ENRICHING)

        val extraction = when (
            val r = router.enricher().enrich(transcript, EnrichOptions(languageCode = transcript.languageCode))
        ) {
            is Outcome.Ok -> r.value
            is Outcome.Err -> return fail(audio.recordingId, r)
        }

        val note = assembleNote(audio, transcript, extraction)
        writer.upsertNote(note, transcript.toDomain(noteRecordingId = audio.recordingId))
        writer.setRecordingPipelineState(audio.recordingId, PipelineState.READY)
        return Outcome.Ok(note)
    }

    private suspend fun fail(recordingId: String, err: Outcome.Err): Outcome<Note> {
        writer.setRecordingPipelineState(recordingId, PipelineState.FAILED)
        return err
    }

    // --- mapping: AI results -> domain persistence types ---------------------

    private fun assembleNote(
        audio: AudioRef,
        transcript: DiarizedTranscript,
        x: NoteExtraction,
    ): Note {
        val now = clock.now()
        val noteId = "note-${audio.recordingId}"
        return Note(
            id = noteId,
            recordingId = audio.recordingId,
            title = x.title,
            summaryShort = x.summaryShort,
            summaryLong = x.summaryLong,
            languageCode = transcript.languageCode,
            createdAt = now,
            updatedAt = now,
            pinned = false,
            archived = false,
            userEdited = false,
            tags = x.tags.mapIndexed { i, t -> Tag("tag-$noteId-$i", t) },
            keyPoints = x.keyPoints.mapIndexed { i, kp ->
                KeyPoint("kp-$noteId-$i", noteId, i, kp.text, null, kp.sourceStartMs ?: 0L)
            },
            todos = x.todos.mapIndexed { i, td ->
                Todo(
                    id = "td-$noteId-$i",
                    noteId = noteId,
                    text = td.text,
                    dueHint = td.dueHint,
                    assignee = td.assignee,
                    priority = td.priority,
                    status = TodoStatus.OPEN,
                    sourceSegmentId = null,
                    sourceStartMs = td.sourceStartMs,
                    completedAt = null,
                )
            },
            entities = emptyList(),
            durationMs = audio.durationMs,
            speakerCount = transcript.speakerCount,
            pipelineState = PipelineState.READY,
            pipelineProgress = null,
        )
    }

    private fun DiarizedTranscript.toDomain(noteRecordingId: String): Transcript {
        val transcriptId = "t-$noteRecordingId"
        return Transcript(
            id = transcriptId,
            recordingId = noteRecordingId,
            provider = provider,
            model = model,
            mode = mode.name.lowercase(),
            languageCode = languageCode,
            fullText = fullText,
            createdAt = clock.now(),
            segments = segments.mapIndexed { i, s ->
                // Re-key segments under the deterministic transcript id.
                TranscriptSegment(
                    id = "seg-$transcriptId-$i",
                    transcriptId = transcriptId,
                    idx = i,
                    startMs = s.startMs,
                    endMs = s.endMs,
                    speakerId = s.speakerId,
                    text = s.text,
                    confidence = s.confidence,
                )
            },
        )
    }
}

/** Per-run pipeline options (extend as stages gain knobs). */
data class PipelineOptions(
    val asr: AsrOptions = AsrOptions(),
)
