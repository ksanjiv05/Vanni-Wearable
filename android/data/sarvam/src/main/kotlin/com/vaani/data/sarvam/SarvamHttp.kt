package com.vaani.data.sarvam

import com.vaani.domain.model.Outcome

/**
 * The single boundary to Sarvam over HTTPS (ADR-001 §5.5.1). Kept as an
 * interface so [SarvamAsrEngine]/[SarvamEnricher] are unit-testable with a fake
 * and the retry/limiter/auth policy lives in one place ([ResilientSarvamClient]).
 * DTOs stay internal; nothing here leaks a Retrofit/OkHttp type to the engines.
 */
internal interface SarvamHttp {

    /** POST audio for transcription. [audioBytes] is 16 kHz mono; server-side diarization requested. */
    suspend fun transcribe(
        audioBytes: ByteArray,
        languageHint: String?,
        translate: Boolean,
        diarize: Boolean,
    ): Outcome<SttResponse>

    /** Chat completion with a strict-JSON response format (extraction). */
    suspend fun chatJson(request: ChatCompletionRequest): Outcome<ChatCompletionResponse>

    /** Streaming chat completion; emits raw content deltas until done. */
    fun chatStream(request: ChatCompletionRequest): kotlinx.coroutines.flow.Flow<String>
}
