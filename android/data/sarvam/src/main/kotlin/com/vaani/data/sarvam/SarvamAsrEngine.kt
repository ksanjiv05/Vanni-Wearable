package com.vaani.data.sarvam

import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AsrCapabilities
import com.vaani.domain.ai.AsrEngine
import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.AsrOptions
import com.vaani.domain.ai.AsrProgress
import com.vaani.domain.ai.AudioRef
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sarvam Saaras STT adapter (ADR-001). Wraps [ResilientSarvamClient] behind the
 * :domain [AsrEngine] interface; contributed into the router multibinding as
 * [AiBackend.SARVAM]. Reads the stored audio file, sends it, maps the diarized
 * response to a domain [DiarizedTranscript].
 */
@Singleton
class SarvamAsrEngine @Inject internal constructor(
    private val http: SarvamHttp,
) : AsrEngine {

    override val id: AiBackend = AiBackend.SARVAM

    override fun capabilities(): AsrCapabilities = AsrCapabilities(
        backend = AiBackend.SARVAM,
        supportsDiarization = true,      // Sarvam batch diarization
        supportsCodemix = true,          // bespoke Hinglish codemix mode
        supportsTranslate = true,
        maxDurationMs = 2L * 60 * 60 * 1000, // batch ≤ 2h/file
        languages = INDIC_LANGS,
        requiresNetwork = true,
    )

    override suspend fun transcribe(
        audio: AudioRef,
        opts: AsrOptions,
        onProgress: (AsrProgress) -> Unit,
    ): Outcome<DiarizedTranscript> = withContext(Dispatchers.IO) {
        val file = File(audio.storageUri)
        if (!file.exists()) {
            return@withContext Outcome.Err(
                AiError.InferenceFailed(AiBackend.SARVAM, "audio file not found: ${audio.storageUri}"),
            )
        }
        onProgress(AsrProgress(0.1f, "uploading"))
        val bytes = runCatching { file.readBytes() }.getOrElse {
            return@withContext Outcome.Err(AiError.InferenceFailed(AiBackend.SARVAM, "read failed: ${it.message}"))
        }
        onProgress(AsrProgress(0.5f, "transcribing"))
        val result = http.transcribe(
            audioBytes = bytes,
            languageHint = opts.languageHint,
            translate = opts.mode == AsrMode.TRANSLATE,
            diarize = opts.expectMultipleSpeakers,
        )
        onProgress(AsrProgress(1f, "done"))
        when (result) {
            is Outcome.Ok -> Outcome.Ok(
                SarvamMapping.toDiarizedTranscript(audio.recordingId, "saaras:v3", opts.mode, result.value),
            )
            is Outcome.Err -> result
        }
    }

    private companion object {
        val INDIC_LANGS = setOf(
            "hi", "en", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "or", "as",
        )
    }
}
