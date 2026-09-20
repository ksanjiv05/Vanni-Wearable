package com.vaani.data.asr.local

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File

/**
 * The single boundary to the MediaPipe LLM Inference native runtime (on-device
 * GGUF/.task execution). Isolated exactly like [SherpaAsr] is for ASR: build an
 * [LlmInference] from a downloaded `.task` model, run a prompt, return text.
 *
 * Honest failure: if the native lib or model is missing, or the runtime throws,
 * [runOrNull] returns null (caller → heuristic fallback / typed error) — never a
 * fabricated completion. The engine is created lazily and cached; it is heavy
 * (hundreds of MB of weights mmap'd) so we keep one instance per model path.
 */
internal class MediaPipeLlm(private val context: Context) {

    private var engine: LlmInference? = null
    private var loadedPath: String? = null

    /** True when the MediaPipe native lib is linked into this build. */
    fun nativeLibPresent(): Boolean = runCatching {
        // Touch a class from the AAR; if the native side is absent this throws.
        Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
        true
    }.getOrDefault(false)

    /** True if [modelFile] exists and looks like a usable model bundle. */
    fun isModelPresent(modelFile: File): Boolean =
        modelFile.isFile && modelFile.length() > 1_000_000L

    /**
     * Run [prompt] through the model at [modelPath], returning the raw completion
     * or null on any failure. [maxTokens] caps the total context+output budget.
     *
     * The engine is CLOSED after every run (not cached): [LlmInference] mmaps hundreds
     * of MB, so holding it resident across notes — especially while a batch of recordings
     * is being processed — blows past the memory budget and OOM-crashes the process.
     * Reload cost is negligible next to the inference itself (inference is seconds-to-minutes).
     */
    @Synchronized
    fun runOrNull(modelPath: String, prompt: String, maxTokens: Int = 1024): String? {
        val eng = engineFor(modelPath) ?: return null
        return try {
            eng.generateResponse(prompt)
        } catch (t: Throwable) {
            Log.e(TAG, "generateResponse failed", t)
            null
        } finally {
            // Free the mmap immediately so peak memory stays at one inference.
            runCatching { eng.close() }
            engine = null
            loadedPath = null
        }
    }

    private fun engineFor(modelPath: String): LlmInference? {
        if (engine != null && loadedPath == modelPath) return engine
        // Model changed (or first use): dispose the old engine and build fresh.
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
        if (!nativeLibPresent()) {
            Log.e(TAG, "MediaPipe genai native lib not present")
            return null
        }
        return try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(MAX_TOKENS)
                .setMaxTopK(TOP_K)
                .build()
            LlmInference.createFromOptions(context, options).also {
                engine = it
                loadedPath = modelPath
            }
        } catch (t: Throwable) {
            Log.e(TAG, "LlmInference init failed for $modelPath", t)
            null
        }
    }

    fun close() {
        runCatching { engine?.close() }
        engine = null
        loadedPath = null
    }

    private companion object {
        const val TAG = "MediaPipeLlm"
        // Context window for a ~1.5B .task; generous enough for a 5-min transcript
        // chunk + JSON output, small enough to stay within phone memory.
        const val MAX_TOKENS = 2048
        const val TOP_K = 40
    }
}
