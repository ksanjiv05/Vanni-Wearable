package com.vaani.data.pipeline

import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AiEnginePrefs
import com.vaani.domain.ai.AiEngineSettings
import com.vaani.domain.ai.AiStage
import com.vaani.domain.ai.AsrCapabilities
import com.vaani.domain.ai.AsrEngine
import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.AsrOptions
import com.vaani.domain.ai.AsrProgress
import com.vaani.domain.ai.AudioRef
import com.vaani.domain.ai.ChatDelta
import com.vaani.domain.ai.ChatRequest
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.ai.EngineRouter
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.ai.Enricher
import com.vaani.domain.ai.ExtractedKeyPoint
import com.vaani.domain.ai.ExtractedTodo
import com.vaani.domain.ai.NoteExtraction
import com.vaani.domain.model.AiError
import com.vaani.domain.model.Note
import com.vaani.domain.model.Outcome
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.Priority
import com.vaani.domain.model.Transcript
import com.vaani.domain.model.TranscriptSegment
import com.vaani.domain.repository.NotesWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IngestPipelineTest {

    private val audio = AudioRef(
        recordingId = "rec-9",
        storageUri = "/tmp/rec-9.opus",
        durationMs = 120_000,
        sampleRate = 16_000,
        codec = "opus",
    )

    // --- fakes ---------------------------------------------------------------

    private class RecordingStates {
        val notePipeline = mutableListOf<Pair<String, PipelineState>>()
        val recordingPipeline = mutableListOf<Pair<String, PipelineState>>()
        var savedNote: Note? = null
        var savedTranscript: Transcript? = null
    }

    private class FakeWriter(val log: RecordingStates) : NotesWriter {
        override suspend fun upsertNote(note: Note, transcript: Transcript?) {
            log.savedNote = note
            log.savedTranscript = transcript
        }
        override suspend fun setNotePipelineState(noteId: String, state: PipelineState, progress: Float?) {
            log.notePipeline += noteId to state
        }
        override suspend fun setRecordingPipelineState(recordingId: String, state: PipelineState) {
            log.recordingPipeline += recordingId to state
        }
        override suspend fun setTodoStatus(todoId: String, status: com.vaani.domain.model.TodoStatus, completedAtEpochMs: Long?) {}
        override suspend fun deleteRecording(recordingId: String): String? = null
    }

    private class OkAsr : AsrEngine {
        override val id = AiBackend.LOCAL
        override fun capabilities() = AsrCapabilities(
            id, supportsDiarization = true, supportsCodemix = false,
            supportsTranslate = false, maxDurationMs = Long.MAX_VALUE,
            languages = setOf("hi", "en"), requiresNetwork = false,
        )
        override suspend fun transcribe(
            audio: AudioRef, opts: AsrOptions, onProgress: (AsrProgress) -> Unit,
        ): Outcome<DiarizedTranscript> {
            onProgress(AsrProgress(0.5f, "decoding"))
            return Outcome.Ok(
                DiarizedTranscript(
                    recordingId = audio.recordingId,
                    provider = "local", model = "whisper-small", mode = AsrMode.TRANSCRIBE,
                    languageCode = "hi-IN",
                    fullText = "aaj standup mein Atlas migration decide hua.",
                    segments = listOf(
                        TranscriptSegment("x", "x", 0, 0, 4_000, "S1", "aaj standup mein Atlas migration decide hua.", 0.9f),
                    ),
                    speakerCount = 2,
                ),
            )
        }
    }

    private class FailingAsr : AsrEngine {
        override val id = AiBackend.LOCAL
        override fun capabilities() = OkAsr().capabilities()
        override suspend fun transcribe(
            audio: AudioRef, opts: AsrOptions, onProgress: (AsrProgress) -> Unit,
        ): Outcome<DiarizedTranscript> = Outcome.Err(AiError.InferenceFailed(id, "boom"))
    }

    private class OkEnricher : Enricher {
        override val id = AiBackend.LOCAL
        override val requiresNetwork = false
        override suspend fun enrich(transcript: DiarizedTranscript, opts: EnrichOptions): Outcome<NoteExtraction> =
            Outcome.Ok(
                NoteExtraction(
                    title = "Standup",
                    summaryShort = "Atlas migration decided.",
                    summaryLong = "The team decided to proceed with the Atlas migration.",
                    keyPoints = listOf(ExtractedKeyPoint("Atlas migration decided", 2_000)),
                    todos = listOf(ExtractedTodo("Write migration doc", "Ravi", "Fri", Priority.HIGH, 2_000)),
                    tags = listOf("atlas", "standup"),
                ),
            )
        override fun chatStream(req: ChatRequest): Flow<ChatDelta> = flowOf()
    }

    private fun settings(asr: AiBackend = AiBackend.LOCAL, enrich: AiBackend = AiBackend.LOCAL) =
        object : AiEngineSettings {
            override fun observe(): Flow<AiEnginePrefs> = flowOf(AiEnginePrefs(asr, enrich))
            override suspend fun current() = AiEnginePrefs(asr, enrich)
            override suspend fun setBackend(stage: AiStage, backend: AiBackend) {}
            override suspend fun setPreferredModel(role: com.vaani.domain.ai.ModelRole, modelId: String?) {}
        }

    // --- tests ---------------------------------------------------------------

    @Test
    fun happyPath_persistsNoteAndDrivesStatesToReady() = runBlocking {
        val log = RecordingStates()
        val router = EngineRouter(
            settings(),
            asrEngines = mapOf(AiBackend.LOCAL to OkAsr()),
            enrichers = mapOf(AiBackend.LOCAL to OkEnricher()),
        )
        val pipeline = IngestPipeline(router, FakeWriter(log))

        val result = pipeline.process(audio)

        assertTrue(result is Outcome.Ok)
        val note = (result as Outcome.Ok).value
        assertEquals("Standup", note.title)
        assertEquals("rec-9", note.recordingId)
        assertEquals(2, note.speakerCount)
        assertEquals(1, note.todos.size)
        assertEquals(Priority.HIGH, note.todos.first().priority)
        assertEquals(2, note.tags.size)
        assertEquals(PipelineState.READY, note.pipelineState)

        // recording state walked TRANSCRIBING -> ENRICHING -> READY, in order
        assertEquals(
            listOf(PipelineState.TRANSCRIBING, PipelineState.ENRICHING, PipelineState.READY),
            log.recordingPipeline.map { it.second },
        )
        // transcript persisted alongside the note
        assertEquals(note.recordingId, log.savedTranscript?.recordingId)
    }

    @Test
    fun asrFailure_marksFailedAndReturnsError() = runBlocking {
        val log = RecordingStates()
        val router = EngineRouter(
            settings(),
            asrEngines = mapOf(AiBackend.LOCAL to FailingAsr()),
            enrichers = mapOf(AiBackend.LOCAL to OkEnricher()),
        )
        val pipeline = IngestPipeline(router, FakeWriter(log))

        val result = pipeline.process(audio)

        assertTrue(result is Outcome.Err)
        assertEquals(PipelineState.FAILED, log.recordingPipeline.last().second)
        assertEquals(null, log.savedNote) // nothing persisted on failure
    }

    @Test
    fun routesPerSettingChoice() = runBlocking {
        // ASR set to SARVAM, enrich to LOCAL — router must pick each independently.
        val log = RecordingStates()
        val sarvamAsr = object : AsrEngine by OkAsr() {
            override val id = AiBackend.SARVAM
        }
        val router = EngineRouter(
            settings(asr = AiBackend.SARVAM, enrich = AiBackend.LOCAL),
            asrEngines = mapOf(AiBackend.LOCAL to OkAsr(), AiBackend.SARVAM to sarvamAsr),
            enrichers = mapOf(AiBackend.LOCAL to OkEnricher()),
        )
        assertEquals(AiBackend.SARVAM, (router.asr() as Outcome.Ok).value.id)
        assertEquals(AiBackend.LOCAL, (router.enricher() as Outcome.Ok).value.id)

        val result = IngestPipeline(router, FakeWriter(log)).process(audio)
        assertTrue(result is Outcome.Ok)
    }

    @Test
    fun missingEnricher_savesTranscriptOnlyNote_reachesReady() = runBlocking {
        // ASR succeeds but NO enricher is registered (no local LLM, no API key).
        // The pipeline must still persist a real transcript-only note and reach
        // READY — never discard a good transcript, never fabricate a summary.
        val log = RecordingStates()
        val router = EngineRouter(
            settings(),
            asrEngines = mapOf(AiBackend.LOCAL to OkAsr()),
            enrichers = emptyMap(),
        )
        val result = IngestPipeline(router, FakeWriter(log)).process(audio)

        assertTrue(result is Outcome.Ok)
        assertEquals(PipelineState.READY, log.recordingPipeline.last().second)
        assertTrue(log.savedNote != null)
    }

    @Test
    fun missingAsrEngine_marksFailed_beforeAnyTranscribing() = runBlocking {
        // LOCAL chosen but not registered → router returns ModelUnavailable.
        // Pipeline fails to FAILED without ever calling an engine.
        val log = RecordingStates()
        val router = EngineRouter(
            settings(asr = AiBackend.LOCAL),
            asrEngines = emptyMap(),
            enrichers = mapOf(AiBackend.LOCAL to OkEnricher()),
        )
        val result = IngestPipeline(router, FakeWriter(log)).process(audio)

        assertTrue(result is Outcome.Err)
        assertEquals(PipelineState.FAILED, log.recordingPipeline.last().second)
        assertEquals(null, log.savedNote)
    }
}
