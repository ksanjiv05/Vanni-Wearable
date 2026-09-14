package com.vaani.domain.ai

import com.vaani.domain.model.Outcome
import com.vaani.domain.model.Priority
import com.vaani.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow

/**
 * Pluggable AI backends (ADR-001). The two cloud stages of the pipeline —
 * speech-to-text and enrichment — each run through an interface so the user
 * can choose a fully on-device engine or the Sarvam API, per stage, at
 * runtime. The durable pipeline, retrieval, and UI depend ONLY on these
 * interfaces; vendor/native types never cross into :domain or :feature.
 *
 * This file is pure Kotlin (no Android, no vendor SDKs) on purpose.
 */

/** Which implementation backs a given AI stage. Persisted in settings. */
enum class AiBackend { LOCAL, SARVAM }

/** The two AI stages the user can independently route (ADR-001 §2). */
enum class AiStage { ASR, ENRICH }

// ---------------------------------------------------------------------------
// Speech-to-text
// ---------------------------------------------------------------------------

/** A handle to persisted audio the engine can read. Kept opaque to :domain. */
data class AudioRef(
    val recordingId: String,
    val storageUri: String,
    val durationMs: Long,
    val sampleRate: Int,
    val codec: String,
)

/** Transcription request options, backend-agnostic. Mirrors Saaras modes (§0.4). */
data class AsrOptions(
    val mode: AsrMode = AsrMode.TRANSCRIBE,
    val expectMultipleSpeakers: Boolean = false,
    val languageHint: String? = null,
    val quality: AsrQuality = AsrQuality.BEST,
)

enum class AsrMode { TRANSCRIBE, TRANSLATE, VERBATIM, TRANSLIT, CODEMIX }

enum class AsrQuality { FAST, BEST }

/**
 * What a concrete ASR engine can actually do, so the router/UI can adapt
 * (e.g. hide "codemix" or "diarization" when the local engine lacks them,
 * ADR-001 §6). Never assume parity across backends.
 */
data class AsrCapabilities(
    val backend: AiBackend,
    val supportsDiarization: Boolean,
    val supportsCodemix: Boolean,
    val supportsTranslate: Boolean,
    val maxDurationMs: Long,
    val languages: Set<String>,
    val requiresNetwork: Boolean,
)

/** A finished transcription with speaker-attributed, timestamped segments. */
data class DiarizedTranscript(
    val recordingId: String,
    val provider: String,
    val model: String,
    val mode: AsrMode,
    val languageCode: String,
    val fullText: String,
    val segments: List<TranscriptSegment>,
    val speakerCount: Int,
)

/** Progress while transcribing, surfaced to the pipeline/UI (0f..1f). */
data class AsrProgress(val fraction: Float, val stageLabel: String)

interface AsrEngine {
    val id: AiBackend

    fun capabilities(): AsrCapabilities

    /**
     * Transcribe one recording. [onProgress] may be called any number of times
     * before completion. Long audio is chunked internally (VAD for local,
     * batch/chunked-sync for Sarvam) — callers do not route by length here;
     * that policy lives in the engine impl (§5.5.2).
     */
    suspend fun transcribe(
        audio: AudioRef,
        opts: AsrOptions,
        onProgress: (AsrProgress) -> Unit = {},
    ): Outcome<DiarizedTranscript>
}

// ---------------------------------------------------------------------------
// Enrichment (note / summary / to-do extraction + chat)
// ---------------------------------------------------------------------------

data class EnrichOptions(
    val languageCode: String,
    val maxKeyPoints: Int = 8,
)

/**
 * Schema-guaranteed extraction result (ADR-001 §3; §5.5.4). Sarvam enforces
 * this via strict json_schema; the local engine via a llama.cpp GBNF grammar
 * generated from the same schema — so both backends return valid, typed data
 * rather than free-form text to regex.
 */
data class NoteExtraction(
    val title: String,
    val summaryShort: String,
    val summaryLong: String,
    val keyPoints: List<ExtractedKeyPoint>,
    val todos: List<ExtractedTodo>,
    val tags: List<String>,
)

data class ExtractedKeyPoint(
    val text: String,
    val sourceStartMs: Long?,
)

data class ExtractedTodo(
    val text: String,
    val assignee: String?,
    val dueHint: String?,
    val priority: Priority,
    val sourceStartMs: Long?,
)

/** A single streamed token/delta from a chat completion. */
data class ChatDelta(val text: String, val done: Boolean)

/** A grounded chat request over retrieved note context (§6.7). */
data class ChatRequest(
    val question: String,
    val contextBlocks: List<String>,
    val languageCode: String,
)

interface Enricher {
    val id: AiBackend

    val requiresNetwork: Boolean

    /** Transcript -> structured Note fields, schema-guaranteed. */
    suspend fun enrich(
        transcript: DiarizedTranscript,
        opts: EnrichOptions,
    ): Outcome<NoteExtraction>

    /** Streamed, citation-grounded answer over supplied context blocks. */
    fun chatStream(req: ChatRequest): Flow<ChatDelta>
}
