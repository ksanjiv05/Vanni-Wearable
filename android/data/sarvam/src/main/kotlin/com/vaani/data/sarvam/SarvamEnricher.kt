package com.vaani.data.sarvam

import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.ChatDelta
import com.vaani.domain.ai.ChatRequest
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.ai.Enricher
import com.vaani.domain.ai.NoteExtraction
import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sarvam chat-completion enrichment adapter (ADR-001 §5.5.4). Turns a transcript
 * into schema-guaranteed [NoteExtraction] via a strict-JSON completion, and
 * streams grounded chat answers. Contributed as [AiBackend.SARVAM].
 */
@Singleton
class SarvamEnricher @Inject internal constructor(
    private val http: SarvamHttp,
    private val json: Json,
) : Enricher {

    override val id: AiBackend = AiBackend.SARVAM
    override val requiresNetwork: Boolean = true

    override suspend fun enrich(
        transcript: DiarizedTranscript,
        opts: EnrichOptions,
    ): Outcome<NoteExtraction> {
        val req = ChatCompletionRequest(
            model = EnrichmentPrompt.MODEL,
            messages = listOf(
                ChatMessageDto("system", EnrichmentPrompt.system(opts.languageCode)),
                ChatMessageDto("user", EnrichmentPrompt.user(transcript.fullText)),
            ),
            responseFormat = ResponseFormatDto(type = "json_object"),
        )
        return when (val r = http.chatJson(req)) {
            is Outcome.Err -> r
            is Outcome.Ok -> {
                val content = r.value.choices.firstOrNull()?.message?.content
                    ?: return Outcome.Err(AiError.InferenceFailed(AiBackend.SARVAM, "empty completion"))
                runCatching { json.decodeFromString(ExtractionDto.serializer(), content) }
                    .map { Outcome.Ok(SarvamMapping.toNoteExtraction(it, opts)) as Outcome<NoteExtraction> }
                    .getOrElse {
                        Outcome.Err(AiError.InferenceFailed(AiBackend.SARVAM, "malformed extraction JSON: ${it.message}"))
                    }
            }
        }
    }

    override fun chatStream(req: ChatRequest): Flow<ChatDelta> = flow {
        val context = req.contextBlocks.joinToString("\n\n---\n\n")
        val completion = ChatCompletionRequest(
            model = EnrichmentPrompt.MODEL,
            messages = listOf(
                ChatMessageDto(
                    "system",
                    "Answer ONLY from the provided context. Cite nothing you cannot support. " +
                        "Reply in language ${req.languageCode}.",
                ),
                ChatMessageDto("user", "Context:\n$context\n\nQuestion: ${req.question}"),
            ),
            stream = true,
        )
        http.chatStream(completion).collect { delta -> emit(ChatDelta(delta, done = false)) }
        emit(ChatDelta("", done = true))
    }
}
