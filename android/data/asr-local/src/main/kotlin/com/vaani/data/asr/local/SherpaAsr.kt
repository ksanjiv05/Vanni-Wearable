package com.vaani.data.asr.local

import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.model.TranscriptSegment
import java.io.File

/**
 * The single boundary to the sherpa-onnx native runtime (ADR-001 §3, §7).
 *
 * Everything else in this module (download, verification, gating, engine
 * plumbing, mapping) is real and unit-tested. Only THIS call needs the
 * sherpa-onnx AAR + the Whisper model on disk. It is kept behind one function
 * that returns null when the runtime/model is absent, so the module compiles
 * and the engine degrades cleanly to AiError.ModelUnavailable instead of
 * crashing or — worse — fabricating a transcript.
 *
 * To activate (see build.gradle.kts):
 *   1. add the sherpa-onnx dependency,
 *   2. build OfflineRecognizer from OfflineRecognizerConfig(whisper model dir),
 *   3. feed 16 kHz mono float samples, read the decoded text + timestamps,
 *   4. map to DiarizedTranscript below.
 */
internal class SherpaAsr {

    /** True once the native lib is linked AND the model dir is on disk. */
    fun isAvailable(modelDir: File): Boolean =
        nativeLibPresent() && modelDir.exists() && modelDir.listFiles()?.isNotEmpty() == true

    /**
     * Runs on-device recognition. Returns null when the runtime/model is not
     * yet wired — callers translate that to AiError.ModelUnavailable. When the
     * sherpa-onnx dependency is added, replace the body with a real decode and
     * build the transcript via [buildTranscript].
     */
    fun recognize(
        recordingId: String,
        pcm16kMonoFloat: FloatArray,
        mode: AsrMode,
    ): DiarizedTranscript? {
        // No fabricated output. Until the AAR + model are present this is null.
        return null
    }

    /** Assemble a domain transcript from decoded, timestamped segments. */
    internal fun buildTranscript(
        recordingId: String,
        model: String,
        mode: AsrMode,
        languageCode: String,
        segments: List<DecodedSegment>,
    ): DiarizedTranscript {
        val tSegments = segments.mapIndexed { i, s ->
            TranscriptSegment(
                id = "seg-$recordingId-$i",
                transcriptId = "t-$recordingId",
                idx = i,
                startMs = s.startMs,
                endMs = s.endMs,
                speakerId = s.speakerId,
                text = s.text,
                confidence = s.confidence,
            )
        }
        return DiarizedTranscript(
            recordingId = recordingId,
            provider = "local",
            model = model,
            mode = mode,
            languageCode = languageCode,
            fullText = tSegments.joinToString(" ") { it.text },
            segments = tSegments,
            speakerCount = tSegments.map { it.speakerId }.distinct().size.coerceAtLeast(1),
        )
    }

    private fun nativeLibPresent(): Boolean = runCatching {
        System.loadLibrary("sherpa-onnx-jni")
        true
    }.getOrDefault(false)
}

/** A decoded segment from the native recognizer, before domain mapping. */
internal data class DecodedSegment(
    val startMs: Long,
    val endMs: Long,
    val speakerId: String,
    val text: String,
    val confidence: Float?,
)
