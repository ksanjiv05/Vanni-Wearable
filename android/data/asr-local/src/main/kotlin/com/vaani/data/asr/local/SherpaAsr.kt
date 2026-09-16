package com.vaani.data.asr.local

import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.model.TranscriptSegment
import java.io.File

/**
 * The single boundary to the sherpa-onnx native runtime (ADR-001 §3, §7).
 *
 * Drives the REAL on-device Whisper recognizer from the vendored sherpa-onnx
 * AAR. Whisper's receptive field is 30 s, so long recordings MUST be fed in
 * bounded windows: [newSession] builds one [OfflineRecognizer]; the caller then
 * streams 16 kHz mono float chunks through [Session.transcribeChunk], each
 * offset by its position in the recording. This keeps memory O(one chunk) and
 * makes 60-minute imports work instead of OOM-ing or only seeing the first 30 s.
 *
 * If the model files or native lib are absent, [newSession] returns null (caller
 * → AiError.ModelUnavailable) — never a fabricated transcript.
 *
 * Whisper is single-speaker here (no diarization model wired yet), so all
 * segments carry speaker "S1" and capabilities report diarization=false.
 */
internal class SherpaAsr {

    /** Raw recognizer output for one chunk. */
    data class ChunkResult(val text: String, val tokens: Array<String>, val timestamps: FloatArray, val lang: String)

    /**
     * A live recognizer bound to a model dir. Reused across every chunk of one
     * recording, then [close]d. Not thread-safe: drive from a single coroutine.
     */
    inner class Session internal constructor(private val recognizer: OfflineRecognizer) {

        /** Transcribe one <=30 s window of 16 kHz mono float PCM. Null on failure. */
        fun transcribeChunk(pcm16kMonoFloat: FloatArray): ChunkResult? = try {
            val stream = recognizer.createStream()
            stream.acceptWaveform(pcm16kMonoFloat, 16000)
            recognizer.decode(stream)
            val r = recognizer.getResult(stream)
            stream.release()
            ChunkResult(r.text, r.tokens, r.timestamps, r.lang.ifBlank { "unknown" })
        } catch (t: Throwable) {
            Log.e(TAG, "transcribeChunk failed", t)
            null
        }

        fun close() = runCatching { recognizer.release() }
    }

    /** True once the native lib is linked AND a usable Whisper model pair is on disk. */
    fun isAvailable(modelDir: File): Boolean =
        nativeLibPresent() && modelDir.isDirectory &&
            matchedPair(modelDir) != null && tokens(modelDir) != null

    /**
     * Build a recognizer for the Whisper model in [modelDir]. Returns null if the
     * native lib/model is missing or sherpa rejects the config (logged, never
     * throws to the caller).
     */
    fun newSession(modelDir: File, mode: AsrMode): Session? {
        if (!nativeLibPresent()) {
            Log.e(TAG, "sherpa-onnx native lib not present")
            return null
        }
        val pair = matchedPair(modelDir) ?: run {
            Log.e(TAG, "no matched encoder/decoder pair in ${modelDir.path}: ${modelDir.list()?.joinToString()}")
            return null
        }
        val tok = tokens(modelDir) ?: run {
            Log.e(TAG, "no tokens file in ${modelDir.path}")
            return null
        }
        val config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = pair.encoder.absolutePath,
                    decoder = pair.decoder.absolutePath,
                    // task=translate → English; else transcribe in source language.
                    task = if (mode == AsrMode.TRANSLATE) "translate" else "transcribe",
                    enableTokenTimestamps = true,
                ),
                tokens = tok.absolutePath,
                numThreads = 2,
                modelType = "whisper",
                debug = false,
            ),
            decodingMethod = "greedy_search",
        )
        return try {
            Session(OfflineRecognizer(config = config))
        } catch (t: Throwable) {
            Log.e(TAG, "OfflineRecognizer init failed (enc=${pair.encoder.name} dec=${pair.decoder.name})", t)
            null
        }
    }

    // --- model-file resolution ----------------------------------------------

    private data class ModelPair(val encoder: File, val decoder: File)

    /**
     * Pick a MATCHED encoder/decoder pair. The Whisper bundle ships four files
     * (fp32 + int8 for each); mixing an int8 encoder with an fp32 decoder makes
     * sherpa throw. Prefer int8 (smaller/faster on phones), else fp32; never mix.
     */
    private fun matchedPair(dir: File): ModelPair? {
        val files = dir.listFiles()?.toList() ?: return null
        fun ending(suffix: String) = files.firstOrNull { it.name.endsWith(suffix) }
        val encI = ending("-encoder.int8.onnx")
        val decI = ending("-decoder.int8.onnx")
        if (encI != null && decI != null) return ModelPair(encI, decI)
        // fp32: match "-encoder.onnx" but NOT the ".int8.onnx" variant.
        val enc = files.firstOrNull { it.name.endsWith("-encoder.onnx") && !it.name.contains(".int8.") }
        val dec = files.firstOrNull { it.name.endsWith("-decoder.onnx") && !it.name.contains(".int8.") }
        if (enc != null && dec != null) return ModelPair(enc, dec)
        return null
    }

    private fun tokens(dir: File): File? =
        File(dir, "tokens.txt").takeIf { it.exists() }
            ?: dir.listFiles()?.firstOrNull { it.name.contains("tokens") && it.name.endsWith(".txt") }

    // --- transcript assembly (accumulated across chunks) --------------------

    /**
     * Assemble the final [DiarizedTranscript] from every chunk's segments. Segment
     * ids/idx are re-keyed globally; [language] is the first non-blank detected.
     */
    fun assemble(
        recordingId: String,
        mode: AsrMode,
        language: String,
        segments: List<TranscriptSegment>,
    ): DiarizedTranscript {
        val transcriptId = "t-$recordingId"
        val reKeyed = segments.mapIndexed { i, s ->
            s.copy(id = "seg-$transcriptId-$i", transcriptId = transcriptId, idx = i)
        }
        val fullText = reKeyed.joinToString(" ") { it.text }.trim()
        return DiarizedTranscript(
            recordingId = recordingId,
            provider = "local",
            model = "whisper",
            mode = mode,
            languageCode = language,
            fullText = fullText,
            segments = reKeyed.ifEmpty {
                listOf(
                    TranscriptSegment(
                        id = "seg-$transcriptId-0", transcriptId = transcriptId, idx = 0,
                        startMs = 0, endMs = 0, speakerId = "S1", text = fullText, confidence = null,
                    ),
                )
            },
            speakerCount = 1,
        )
    }

    /**
     * Split one chunk's tokens into readable segments on sentence punctuation,
     * offsetting every timestamp by [baseOffsetMs] (the chunk's start in the full
     * recording). Falls back to a single segment when token timestamps are absent.
     */
    fun segmentsForChunk(chunk: ChunkResult, baseOffsetMs: Long): List<TranscriptSegment> {
        val tokens = chunk.tokens
        val ts = chunk.timestamps
        if (tokens.isEmpty() || ts.size != tokens.size) {
            val text = chunk.text.trim()
            return if (text.isEmpty()) emptyList()
            else listOf(
                TranscriptSegment(
                    id = "seg", transcriptId = "t", idx = 0,
                    startMs = baseOffsetMs, endMs = baseOffsetMs, speakerId = "S1",
                    text = text, confidence = null,
                ),
            )
        }
        val out = mutableListOf<TranscriptSegment>()
        val sb = StringBuilder()
        var segStartSec = ts.first()
        for (i in tokens.indices) {
            sb.append(tokens[i])
            val boundary = tokens[i].contains(Regex("[.!?。！？]")) || i == tokens.lastIndex
            if (boundary) {
                val piece = sb.toString().replace("▁", " ").trim()
                if (piece.isNotEmpty()) {
                    out += TranscriptSegment(
                        id = "seg", transcriptId = "t", idx = 0,
                        startMs = baseOffsetMs + (segStartSec * 1000).toLong(),
                        endMs = baseOffsetMs + (ts[i] * 1000).toLong(),
                        speakerId = "S1", text = piece, confidence = null,
                    )
                }
                sb.setLength(0)
                if (i + 1 < ts.size) segStartSec = ts[i + 1]
            }
        }
        return out
    }

    private fun nativeLibPresent(): Boolean = runCatching {
        System.loadLibrary("sherpa-onnx-jni")
        true
    }.getOrDefault(false)

    private companion object { const val TAG = "SherpaAsr" }
}
