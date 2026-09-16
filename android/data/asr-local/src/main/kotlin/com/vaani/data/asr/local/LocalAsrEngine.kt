package com.vaani.data.asr.local

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AsrCapabilities
import com.vaani.domain.ai.AsrEngine
import com.vaani.domain.ai.AsrOptions
import com.vaani.domain.ai.AsrProgress
import com.vaani.domain.ai.AudioRef
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import com.vaani.domain.model.TranscriptSegment
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
    private val settings: com.vaani.domain.ai.AiEngineSettings,
) : AsrEngine {

    override val id: AiBackend = AiBackend.LOCAL

    private val sherpa = SherpaAsr()

    /**
     * Find which ASR model to use under filesDir/models/<id>/. Honours the user's
     * chosen model (Settings) when it's installed; otherwise auto-picks the best
     * installed one (Small > Tiny).
     */
    private suspend fun installedModelDir(): File? {
        val root = File(context.filesDir, "models")
        fun dirOf(id: String) = File(root, id).takeIf {
            it.isDirectory && (it.listFiles()?.any { f -> f.name.endsWith(".onnx") } == true)
        }
        // 1) explicit user choice, if that model is downloaded
        settings.current().asrModelId?.let { chosen -> dirOf(chosen)?.let { return it } }
        // 2) auto: best installed (Small before Tiny)
        val candidates = listOf(
            AsrModels.WHISPER_SMALL_ID,
            AsrModels.WHISPER_TINY_ID,
        )
        return candidates.firstNotNullOfOrNull { dirOf(it) }
    }

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
        // Device gating (ADR-001 §6): a weak/old device thermal-throttles and
        // drains battery running Whisper. Refuse up front with a typed error so
        // the router/UI can fall back to API rather than cook the phone.
        val gate = AsrDeviceGating.evaluate(readDeviceProfile())
        if (!gate.allowed) {
            return@withContext Outcome.Err(AiError.Unsupported(AiBackend.LOCAL, gate.reason))
        }
        val modelDir = installedModelDir()
        if (modelDir == null || !sherpa.isAvailable(modelDir)) {
            return@withContext Outcome.Err(
                AiError.ModelUnavailable(AiBackend.LOCAL, AsrModels.WHISPER_TINY_ID),
            )
        }
        onProgress(AsrProgress(0.05f, "loading model"))
        val session = sherpa.newSession(modelDir, opts.mode)
            ?: return@withContext Outcome.Err(
                AiError.InferenceFailed(AiBackend.LOCAL, "recognizer init failed (see log)"),
            )

        // Stream-decode the file in 30 s windows (Whisper's receptive field) and
        // recognize each, so a 60-minute import runs in O(one chunk) memory
        // instead of OOM-ing or only seeing the first 30 s. Segment timestamps
        // are offset by each chunk's position in the recording.
        val segments = mutableListOf<TranscriptSegment>()
        var language = "unknown"
        var chunkIndex = 0
        val result = runCatching {
            AudioDecoder.decodeStreamingMono16k(File(audio.storageUri)) { pcm, fraction ->
                val baseOffsetMs = chunkIndex.toLong() * CHUNK_MS
                val chunk = session.transcribeChunk(pcm)
                if (chunk != null) {
                    if (language == "unknown" && chunk.lang.isNotBlank()) language = chunk.lang
                    segments += sherpa.segmentsForChunk(chunk, baseOffsetMs)
                }
                chunkIndex++
                // Reserve the top 5% for enrichment; ASR owns 0.05..0.95.
                onProgress(AsrProgress((0.05f + fraction * 0.9f).coerceIn(0.05f, 0.95f), "transcribing"))
            }
        }
        session.close()

        result.exceptionOrNull()?.let {
            return@withContext Outcome.Err(
                AiError.InferenceFailed(AiBackend.LOCAL, "decode/recognize failed: ${it.message}"),
            )
        }
        if (segments.isEmpty()) {
            return@withContext Outcome.Err(
                AiError.InferenceFailed(AiBackend.LOCAL, "no speech recognized"),
            )
        }

        onProgress(AsrProgress(1f, "done"))
        Outcome.Ok(sherpa.assemble(audio.recordingId, opts.mode, language, segments))
    }

    /** Snapshot the current device's ABI / RAM for [AsrDeviceGating]. */
    private fun readDeviceProfile(): DeviceProfile {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val totalRam = am?.let {
            ActivityManager.MemoryInfo().also(it::getMemoryInfo).totalMem
        } ?: 0L
        return DeviceProfile(
            supportedAbis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            totalRamBytes = totalRam,
            // A single fresh recording is cheap; charging is only required for
            // large backlogs, which the durable worker layer decides — not here.
            isCharging = true,
        )
    }

    /**
     * Decodes a stored audio file to the 16 kHz mono float PCM sherpa-onnx expects.
     * Delegates container/codec work to [AudioDecoder] (MediaCodec) and DSP to
     * [PcmMath]; streaming, so long files never materialise in memory at once.
     */
    private companion object {
        /** Milliseconds covered by one 30 s decode/recognition chunk. */
        const val CHUNK_MS = 30_000L
    }
}
