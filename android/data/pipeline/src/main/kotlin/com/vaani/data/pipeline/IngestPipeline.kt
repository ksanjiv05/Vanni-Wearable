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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     *
     * The whole ASR+enrich body runs under [inferenceLock] so that even when
     * WorkManager runs several recordings' workers in parallel (batch sync), only
     * ONE recording is doing heavy on-device inference at a time. This bounds peak
     * memory to a single Whisper + a single MediaPipe/Gemma instance — loading
     * several LLM engines at once was OOM-crashing the process mid-batch. Retries
     * stay independent (per-recording work), they just queue on this lock.
     */
    suspend fun process(audio: AudioRef, opts: PipelineOptions = PipelineOptions()): Outcome<Note> =
        inferenceLock.withLock { processLocked(audio, opts) }

    private suspend fun processLocked(audio: AudioRef, opts: PipelineOptions): Outcome<Note> {
        // Resolve the ASR engine BEFORE marking TRANSCRIBING: a missing/unsupported
        // backend must fail cleanly, never crash and never strand the recording
        // mid-state. Router resolution is typed (Outcome), so it routes through fail().
        val asrEngine = when (val r = router.asr()) {
            is Outcome.Ok -> r.value
            is Outcome.Err -> return fail(audio.recordingId, r)
        }
        writer.setRecordingPipelineState(audio.recordingId, PipelineState.TRANSCRIBING)

        val transcript = when (val r = asrEngine.transcribe(audio, opts.asr) { p ->
            // progress hook — the note may not exist yet, so this is best-effort.
        }) {
            is Outcome.Ok -> r.value
            is Outcome.Err -> return fail(audio.recordingId, r)
        }

        // Enrichment is best-effort: if no enricher is available (no local LLM
        // wired, no API key) OR enrichment fails, we STILL persist a real
        // transcript-only note built from the actual ASR output — never fabricate
        // a summary, never throw away a good transcript. Only ASR failure is fatal.
        val extraction: NoteExtraction = when (val r = router.enricher()) {
            is Outcome.Ok -> {
                writer.setRecordingPipelineState(audio.recordingId, PipelineState.ENRICHING)
                when (val e = r.value.enrich(transcript, EnrichOptions(languageCode = transcript.languageCode))) {
                    is Outcome.Ok -> e.value
                    is Outcome.Err -> transcriptOnlyExtraction(transcript)
                }
            }
            is Outcome.Err -> transcriptOnlyExtraction(transcript)
        }

        val note = assembleNote(audio, transcript, extraction)

        writer.upsertNote(note, transcript.toDomain(noteRecordingId = audio.recordingId))
        writer.setRecordingPipelineState(audio.recordingId, PipelineState.READY)
        return Outcome.Ok(note)
    }

    /**
     * Fallback note fields from the transcript alone, when no enricher is
     * available/succeeds. Honest: a real title + summary taken verbatim from the
     * recognized speech, no invented key points/todos.
     */
    private fun transcriptOnlyExtraction(t: DiarizedTranscript): NoteExtraction {
        val text = t.fullText.trim()
        val firstSentence = text.split(Regex("(?<=[.!?。！?])\\s+")).firstOrNull()?.take(80).orEmpty()
        val title = firstSentence.ifBlank { "Voice note" }
        val summary = text.take(140).let { if (text.length > 140) "$it…" else it }
        return NoteExtraction(
            title = title,
            summaryShort = summary.ifBlank { "Transcript ready" },
            summaryLong = text,
            keyPoints = emptyList(),
            todos = emptyList(),
            tags = emptyList(),
        )
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
        // Real duration: prefer the source's known length, else the last segment's
        // end time from the actual transcript (import sets durationMs=0 up front).
        val durationMs = if (audio.durationMs > 0) audio.durationMs
        else transcript.segments.maxOfOrNull { it.endMs } ?: 0L
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
            durationMs = durationMs,
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

/**
 * Process-global lock serialising heavy on-device inference across ALL pipeline
 * instances/workers, so a batch sync never loads several LLM engines at once.
 */
private val inferenceLock = Mutex()
