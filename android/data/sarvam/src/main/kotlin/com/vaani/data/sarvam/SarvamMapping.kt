package com.vaani.data.sarvam

import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.ai.ExtractedKeyPoint
import com.vaani.domain.ai.ExtractedTodo
import com.vaani.domain.ai.NoteExtraction
import com.vaani.domain.model.AiError
import com.vaani.domain.model.AppError
import com.vaani.domain.model.Priority
import com.vaani.domain.model.TranscriptSegment
import kotlin.math.roundToLong

/**
 * Pure vendor-DTO → domain mapping (ADR-001). No Android/network types, so it is
 * unit-testable on the JVM. Any vendor value that can't be mapped degrades to a
 * safe default rather than throwing mid-pipeline.
 */
internal object SarvamMapping {

    fun toDiarizedTranscript(
        recordingId: String,
        model: String,
        mode: AsrMode,
        dto: SttResponse,
    ): DiarizedTranscript {
        val entries = dto.diarized?.entries.orEmpty()
        val segments = entries.mapIndexed { i, e ->
            TranscriptSegment(
                id = "seg-$recordingId-$i",
                transcriptId = "t-$recordingId",
                idx = i,
                startMs = (e.startSec * 1000).roundToLong(),
                endMs = (e.endSec * 1000).roundToLong(),
                speakerId = e.speakerId ?: "S1",
                text = e.transcript,
                confidence = null,
            )
        }
        val fullText = if (dto.transcript.isNotBlank()) {
            dto.transcript
        } else {
            segments.joinToString(" ") { it.text }
        }
        return DiarizedTranscript(
            recordingId = recordingId,
            provider = "sarvam",
            model = model,
            mode = mode,
            languageCode = dto.languageCode ?: "unknown",
            fullText = fullText,
            segments = segments,
            speakerCount = segments.map { it.speakerId }.distinct().size.coerceAtLeast(1),
        )
    }

    fun toNoteExtraction(dto: ExtractionDto, opts: EnrichOptions): NoteExtraction = NoteExtraction(
        title = dto.title,
        summaryShort = dto.summaryShort,
        summaryLong = dto.summaryLong,
        keyPoints = dto.keyPoints.take(opts.maxKeyPoints).map {
            ExtractedKeyPoint(text = it.text, sourceStartMs = it.sourceStartMs)
        },
        todos = dto.todos.map {
            ExtractedTodo(
                text = it.text,
                assignee = it.assignee,
                dueHint = it.dueHint,
                priority = parsePriority(it.priority),
                sourceStartMs = it.sourceStartMs,
            )
        },
        tags = dto.tags,
    )

    private fun parsePriority(raw: String): Priority =
        runCatching { Priority.valueOf(raw.trim().uppercase()) }.getOrDefault(Priority.MEDIUM)
}

/** Maps an HTTP status/exception to a typed error at the module edge. */
internal fun httpErrorToAiError(status: Int?, message: String?): AppError = when (status) {
    401, 403 -> AiError.KeyMissing(AiBackend.SARVAM)
    429 -> AppError.Network("Sarvam rate limited (429)")
    in 500..599 -> AppError.Network("Sarvam server error ${status}: ${message.orEmpty()}")
    null -> AppError.Network(message ?: "network failure")
    else -> AiError.InferenceFailed(AiBackend.SARVAM, "HTTP $status: ${message.orEmpty()}")
}
