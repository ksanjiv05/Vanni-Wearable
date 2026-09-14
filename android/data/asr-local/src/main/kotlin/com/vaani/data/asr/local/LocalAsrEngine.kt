package com.vaani.data.asr.local

import android.content.Context
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AsrCapabilities
import com.vaani.domain.ai.AsrEngine
import com.vaani.domain.ai.AsrOptions
import com.vaani.domain.ai.AsrProgress
import com.vaani.domain.ai.AudioRef
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device [AsrEngine] backed by sherpa-onnx (ADR-001). Contributed into the
 * router's multibinding as [AiBackend.LOCAL]; selected when the user picks
 * "On-device" for speech-to-text in Settings.
 *
 * Real, exercised behaviour today: capability reporting, model-presence checks,
 * and clean typed failures. The native decode is the single unwired step
 * ([SherpaAsr.recognize]); until the AAR + Whisper model are present the engine
 * returns [AiError.ModelUnavailable] so the pipeline can fall back to Sarvam
 * rather than crash or fabricate a transcript.
 */
@Singleton
class LocalAsrEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) : AsrEngine {

    override val id: AiBackend = AiBackend.LOCAL

    private val sherpa = SherpaAsr()
    private val modelDir: File get() = File(context.filesDir, "models/${AsrModels.WHISPER_SMALL.id}")

    override fun capabilities(): AsrCapabilities = AsrCapabilities(
        backend = AiBackend.LOCAL,
        // Whisper local-v1 is single-pass, no speaker turns and no true Hinglish
        // codemix (ADR-001 §6) — report honestly so the UI hides what we can't do.
        supportsDiarization = false,
        supportsCodemix = false,
        supportsTranslate = true, // Whisper task=translate → English
        maxDurationMs = Long.MAX_VALUE, // chunked internally on VAD silence
        languages = setOf("hi", "en", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "or"),
        requiresNetwork = false, // once the model is downloaded
    )

    override suspend fun transcribe(
        audio: AudioRef,
        opts: AsrOptions,
        onProgress: (AsrProgress) -> Unit,
    ): Outcome<DiarizedTranscript> = withContext(Dispatchers.Default) {
        if (!sherpa.isAvailable(modelDir)) {
            return@withContext Outcome.Err(
                AiError.ModelUnavailable(AiBackend.LOCAL, AsrModels.WHISPER_SMALL.id),
            )
        }
        onProgress(AsrProgress(0.05f, "loading model"))

        val pcm = runCatching { AudioDecoding.readPcm16kMonoFloat(File(audio.storageUri)) }
            .getOrElse {
                return@withContext Outcome.Err(
                    AiError.InferenceFailed(AiBackend.LOCAL, "decode failed: ${it.message}"),
                )
            }

        onProgress(AsrProgress(0.5f, "transcribing"))
        val transcript = sherpa.recognize(audio.recordingId, pcm, opts.mode)
            ?: return@withContext Outcome.Err(
                AiError.InferenceFailed(AiBackend.LOCAL, "recognizer returned no result"),
            )

        onProgress(AsrProgress(1f, "done"))
        Outcome.Ok(transcript)
    }
}

/**
 * Decodes a stored audio file to the 16 kHz mono float PCM sherpa-onnx expects.
 * Wired to MediaCodec/Media3 when the runtime lands; isolated so [LocalAsrEngine]
 * stays testable and the decode path is swappable.
 */
internal object AudioDecoding {
    fun readPcm16kMonoFloat(file: File): FloatArray {
        // Real MediaCodec decode is added with the native runtime; the file
        // must exist so failures are surfaced honestly rather than silently ok.
        require(file.exists()) { "audio file not found: ${file.path}" }
        return FloatArray(0)
    }
}
