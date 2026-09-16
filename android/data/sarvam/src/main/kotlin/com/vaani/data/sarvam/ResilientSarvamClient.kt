package com.vaani.data.sarvam

import com.vaani.domain.ai.SarvamCredentials
import com.vaani.domain.model.AiError
import com.vaani.domain.model.AppError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Resilient Sarvam HTTP client (ADR-001 §5.5.1): auth header from
 * [SarvamCredentials], shared account-wide [RateLimiter], bounded retry with
 * exponential backoff on 429/5xx, and typed [AiError] mapping. All calls fail
 * closed with [AiError.KeyMissing] when no key is configured — never an
 * unauthenticated request.
 */
internal class ResilientSarvamClient(
    private val client: OkHttpClient,
    private val credentials: SarvamCredentials,
    private val limiter: RateLimiter,
    private val json: Json,
    private val baseUrl: String = "https://api.sarvam.ai",
    private val maxRetries: Int = 3,
    private val backoff: suspend (attempt: Int) -> Unit = { attempt ->
        kotlinx.coroutines.delay(250L * (1L shl attempt))
    },
) : SarvamHttp {

    override suspend fun transcribe(
        audioBytes: ByteArray,
        languageHint: String?,
        translate: Boolean,
        diarize: Boolean,
    ): Outcome<SttResponse> {
        val key = credentials.apiKey() ?: return Outcome.Err(AiError.KeyMissing(com.vaani.domain.ai.AiBackend.SARVAM))
        val path = if (translate) "/speech-to-text-translate" else "/speech-to-text"
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "saaras:v3")
            .apply {
                if (diarize) addFormDataPart("with_diarization", "true")
                languageHint?.let { addFormDataPart("language_code", it) }
            }
            .addFormDataPart(
                "file", "audio.wav",
                audioBytes.toRequestBody("audio/wav".toMediaType()),
            )
            .build()
        val request = Request.Builder()
            .url("$baseUrl$path")
            .addHeader("api-subscription-key", key)
            .post(body)
            .build()
        return executeJson(request) { json.decodeFromString(SttResponse.serializer(), it) }
    }

    override suspend fun chatJson(request: ChatCompletionRequest): Outcome<ChatCompletionResponse> {
        val key = credentials.apiKey() ?: return Outcome.Err(AiError.KeyMissing(com.vaani.domain.ai.AiBackend.SARVAM))
        val payload = json.encodeToString(ChatCompletionRequest.serializer(), request.copy(stream = false))
        val httpReq = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .addHeader("api-subscription-key", key)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return executeJson(httpReq) { json.decodeFromString(ChatCompletionResponse.serializer(), it) }
    }

    override fun chatStream(request: ChatCompletionRequest): Flow<String> = flow {
        val key = credentials.apiKey() ?: return@flow
        limiter.acquire()
        val payload = json.encodeToString(ChatCompletionRequest.serializer(), request.copy(stream = true))
        val httpReq = Request.Builder()
            .url("$baseUrl/v1/chat/completions")
            .addHeader("api-subscription-key", key)
            .addHeader("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(httpReq).execute().use { resp ->
            val source = resp.body?.source() ?: return@flow
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]" || data.isEmpty()) continue
                val delta = runCatching {
                    json.decodeFromString(ChatCompletionResponse.serializer(), data)
                        .choices.firstOrNull()?.delta?.content
                }.getOrNull()
                if (!delta.isNullOrEmpty()) emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO) // blocking OkHttp read must not run on the collector's (often Main) thread

    /** Run [request] with limiter + bounded retry; decode the body with [decode]. */
    private suspend fun <T> executeJson(request: Request, decode: (String) -> T): Outcome<T> {
        var lastErr: AppError = AppError.Network("no attempt made")
        for (attempt in 0..maxRetries) {
            limiter.acquire()
            val result = runCatching {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        Outcome.Ok(decode(bodyStr))
                    } else {
                        Outcome.Err(httpErrorToAiError(resp.code, bodyStr.take(500)))
                    }
                }
            }.getOrElse { t ->
                if (t is IOException) Outcome.Err(AppError.Network(t.message ?: "io error"))
                else Outcome.Err(AiError.InferenceFailed(com.vaani.domain.ai.AiBackend.SARVAM, t.message ?: "decode error"))
            }
            when (result) {
                is Outcome.Ok -> return result
                is Outcome.Err -> {
                    lastErr = result.error
                    if (!isRetryable(result.error) || attempt == maxRetries) return result
                    backoff(attempt)
                }
            }
        }
        return Outcome.Err(lastErr)
    }

    private fun isRetryable(err: AppError): Boolean = err is AppError.Network
}
