package com.vaani.data.sarvam

import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SarvamMappingTest {

    @Test
    fun mapsDiarizedResponse_toDomainSegments_withMsTimestamps() {
        val dto = SttResponse(
            transcript = "full text",
            languageCode = "hi-IN",
            diarized = DiarizedDto(
                entries = listOf(
                    DiarizedEntryDto(startSec = 0.0, endSec = 2.5, speakerId = "S1", transcript = "hello"),
                    DiarizedEntryDto(startSec = 2.5, endSec = 4.0, speakerId = "S2", transcript = "namaste"),
                ),
            ),
        )
        val t = SarvamMapping.toDiarizedTranscript("rec-1", "saaras:v3", AsrMode.TRANSCRIBE, dto)

        assertEquals("sarvam", t.provider)
        assertEquals("hi-IN", t.languageCode)
        assertEquals(2, t.segments.size)
        assertEquals(0L, t.segments[0].startMs)
        assertEquals(2500L, t.segments[0].endMs)
        assertEquals(2, t.speakerCount)
        assertEquals("seg-rec-1-0", t.segments[0].id)
    }

    @Test
    fun fullText_fallsBackToJoinedSegments_whenTopLevelBlank() {
        val dto = SttResponse(
            transcript = "",
            diarized = DiarizedDto(listOf(DiarizedEntryDto(0.0, 1.0, "S1", "a"), DiarizedEntryDto(1.0, 2.0, "S1", "b"))),
        )
        val t = SarvamMapping.toDiarizedTranscript("r", "m", AsrMode.TRANSCRIBE, dto)
        assertEquals("a b", t.fullText)
        assertEquals(1, t.speakerCount)
    }

    @Test
    fun extraction_mapsAndClampsKeyPoints_andParsesPriority() {
        val dto = ExtractionDto(
            title = "Standup",
            summaryShort = "s",
            summaryLong = "l",
            keyPoints = (1..10).map { KeyPointDto("kp$it", it * 1000L) },
            todos = listOf(TodoDto("do it", "Ravi", "Fri", "high", 2000)),
            tags = listOf("atlas"),
        )
        val x = SarvamMapping.toNoteExtraction(dto, EnrichOptions(languageCode = "hi", maxKeyPoints = 3))
        assertEquals(3, x.keyPoints.size)                 // clamped
        assertEquals(Priority.HIGH, x.todos[0].priority)  // case-insensitive parse
        assertEquals(2000L, x.todos[0].sourceStartMs)
    }

    @Test
    fun unknownPriority_defaultsMedium() {
        val dto = ExtractionDto(todos = listOf(TodoDto("x", priority = "urgent-ish")))
        val x = SarvamMapping.toNoteExtraction(dto, EnrichOptions(languageCode = "en"))
        assertEquals(Priority.MEDIUM, x.todos[0].priority)
    }

    @Test
    fun httpErrors_mapToTypedAiErrors() {
        assertTrue(httpErrorToAiError(401, "no") is com.vaani.domain.model.AiError.KeyMissing)
        assertTrue(httpErrorToAiError(503, "down") is com.vaani.domain.model.AppError.Network)
        assertTrue(httpErrorToAiError(429, "slow") is com.vaani.domain.model.AppError.Network)
        assertTrue(httpErrorToAiError(422, "bad") is com.vaani.domain.model.AiError.InferenceFailed)
    }
}
