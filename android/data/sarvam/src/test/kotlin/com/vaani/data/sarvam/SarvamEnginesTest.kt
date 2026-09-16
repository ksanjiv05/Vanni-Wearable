package com.vaani.data.sarvam

import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.AsrOptions
import com.vaani.domain.ai.AudioRef
import com.vaani.domain.ai.ChatRequest
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SarvamEnginesTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private class FakeHttp(
        val stt: Outcome<SttResponse>? = null,
        val chat: Outcome<ChatCompletionResponse>? = null,
        val stream: List<String> = emptyList(),
    ) : SarvamHttp {
        override suspend fun transcribe(a: ByteArray, l: String?, t: Boolean, d: Boolean) = stt!!
        override suspend fun chatJson(request: ChatCompletionRequest) = chat!!
        override fun chatStream(request: ChatCompletionRequest): Flow<String> = flowOf(*stream.toTypedArray())
    }

    @Test
    fun enricher_parsesStrictJson_toNoteExtraction() = runBlocking {
        val payload = """
            {"title":"Standup","summary_short":"s","summary_long":"l",
             "key_points":[{"text":"kp","source_start_ms":1000}],
             "todos":[{"text":"do","assignee":"Ravi","due_hint":"Fri","priority":"HIGH","source_start_ms":2000}],
             "tags":["atlas"]}
        """.trimIndent()
        val http = FakeHttp(chat = Outcome.Ok(ChatCompletionResponse(listOf(ChoiceDto(message = ChatMessageDto("assistant", payload))))))
        val enricher = SarvamEnricher(http, json)

        val transcript = DiarizedTranscript("r", "sarvam", "saaras:v3", AsrMode.TRANSCRIBE, "hi", "text", emptyList(), 1)
        val result = enricher.enrich(transcript, EnrichOptions(languageCode = "hi"))

        assertTrue(result is Outcome.Ok)
        val x = (result as Outcome.Ok).value
        assertEquals("Standup", x.title)
        assertEquals(1, x.todos.size)
        assertEquals(1, x.keyPoints.size)
    }

    @Test
    fun enricher_malformedJson_returnsInferenceFailed() = runBlocking {
        val http = FakeHttp(chat = Outcome.Ok(ChatCompletionResponse(listOf(ChoiceDto(message = ChatMessageDto("assistant", "not json"))))))
        val result = SarvamEnricher(http, json).enrich(
            DiarizedTranscript("r", "sarvam", "m", AsrMode.TRANSCRIBE, "hi", "t", emptyList(), 1),
            EnrichOptions(languageCode = "hi"),
        )
        assertTrue(result is Outcome.Err)
        assertTrue((result as Outcome.Err).error is AiError.InferenceFailed)
    }

    @Test
    fun enricher_propagatesHttpError() = runBlocking {
        val http = FakeHttp(chat = Outcome.Err(AiError.KeyMissing(AiBackend.SARVAM)))
        val result = SarvamEnricher(http, json).enrich(
            DiarizedTranscript("r", "sarvam", "m", AsrMode.TRANSCRIBE, "hi", "t", emptyList(), 1),
            EnrichOptions(languageCode = "hi"),
        )
        assertTrue((result as Outcome.Err).error is AiError.KeyMissing)
    }

    @Test
    fun chatStream_emitsDeltasThenDone() = runBlocking {
        val http = FakeHttp(stream = listOf("Hel", "lo"))
        val deltas = SarvamEnricher(http, json)
            .chatStream(ChatRequest("q", listOf("ctx"), "en")).toList()
        assertEquals("Hel", deltas[0].text)
        assertEquals("lo", deltas[1].text)
        assertTrue(deltas.last().done)
    }

    @Test
    fun asr_missingFile_returnsInferenceFailed() = runBlocking {
        val http = FakeHttp(stt = Outcome.Ok(SttResponse()))
        val audio = AudioRef("r", "/no/such/file.wav", 1000, 16000, "wav")
        val result = SarvamAsrEngine(http).transcribe(audio, AsrOptions()) {}
        assertTrue(result is Outcome.Err)
        assertTrue((result as Outcome.Err).error is AiError.InferenceFailed)
    }

    @Test
    fun asr_capabilities_reportNetworkAndCodemix() {
        val caps = SarvamAsrEngine(FakeHttp()).capabilities()
        assertTrue(caps.requiresNetwork)
        assertTrue(caps.supportsCodemix)
        assertTrue(caps.supportsDiarization)
    }
}
