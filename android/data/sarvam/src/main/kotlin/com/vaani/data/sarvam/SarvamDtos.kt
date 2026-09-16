package com.vaani.data.sarvam

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the Sarvam API (ADR-001 §5.5). Kept internal to this module —
 * vendor types never cross into :domain or :feature. Mapped to domain models in
 * [SarvamMapping].
 */

// --- Speech-to-text (Saaras) -------------------------------------------------

@Serializable
internal data class SttResponse(
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("transcript") val transcript: String = "",
    @SerialName("language_code") val languageCode: String? = null,
    @SerialName("diarized_transcript") val diarized: DiarizedDto? = null,
)

@Serializable
internal data class DiarizedDto(
    val entries: List<DiarizedEntryDto> = emptyList(),
)

@Serializable
internal data class DiarizedEntryDto(
    @SerialName("start_time_seconds") val startSec: Double = 0.0,
    @SerialName("end_time_seconds") val endSec: Double = 0.0,
    @SerialName("speaker_id") val speakerId: String? = null,
    val transcript: String = "",
)

// --- Chat completions (enrichment + chat) ------------------------------------

@Serializable
internal data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessageDto>,
    @SerialName("response_format") val responseFormat: ResponseFormatDto? = null,
    val stream: Boolean = false,
    val temperature: Double = 0.2,
)

@Serializable
internal data class ChatMessageDto(
    val role: String,
    val content: String,
)

@Serializable
internal data class ResponseFormatDto(
    val type: String = "json_object",
)

@Serializable
internal data class ChatCompletionResponse(
    val choices: List<ChoiceDto> = emptyList(),
)

@Serializable
internal data class ChoiceDto(
    val message: ChatMessageDto? = null,
    val delta: ChatMessageDto? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

// --- Structured extraction payload the enrichment prompt asks for ------------

@Serializable
internal data class ExtractionDto(
    val title: String = "",
    @SerialName("summary_short") val summaryShort: String = "",
    @SerialName("summary_long") val summaryLong: String = "",
    @SerialName("key_points") val keyPoints: List<KeyPointDto> = emptyList(),
    val todos: List<TodoDto> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Serializable
internal data class KeyPointDto(
    val text: String = "",
    @SerialName("source_start_ms") val sourceStartMs: Long? = null,
)

@Serializable
internal data class TodoDto(
    val text: String = "",
    val assignee: String? = null,
    @SerialName("due_hint") val dueHint: String? = null,
    val priority: String = "MEDIUM",
    @SerialName("source_start_ms") val sourceStartMs: Long? = null,
)
