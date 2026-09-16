package com.vaani.domain.ai

/**
 * On-device model catalog + device-aware recommendation (ADR-001 §4, §5).
 *
 * Pure Kotlin: the catalog and all "which model suits this phone" logic live in
 * :domain so they're testable and shared by the picker UI, the router, and the
 * installer. Weights are never in the APK — each entry is downloaded on demand,
 * SHA-256-verified, and cached app-private.
 */

/** What pipeline stage a model serves. */
enum class ModelRole { ASR, LLM, EMBEDDING }

/** Coarse device capability tier, derived from RAM + ABI. */
enum class DeviceClass { LOW, MID, HIGH }

/** How well a model fits the current device. Drives the UI badge + copy. */
enum class Suitability { RECOMMENDED, SUPPORTED, NOT_RECOMMENDED, UNSUPPORTED }

/** A concrete measurement of the running device, built at the Android edge. */
data class DeviceSpec(
    val supportedAbis: List<String>,
    val totalRamBytes: Long,
) {
    val isArm64: Boolean get() = supportedAbis.any { it == "arm64-v8a" }
}

/** Supplies the running device's [DeviceSpec] (implemented in :data:ai over Android APIs). */
interface DeviceSpecProvider {
    fun current(): DeviceSpec
}

/**
 * One catalogue entry. [minRamBytes] is the floor to run at all; a device with
 * at least [recommendedRamBytes] runs it comfortably (no thermal/OOM risk).
 * [downloadBytes] is the on-the-wire size; [pinned] is true once we have a
 * verified SHA-256 (the installer refuses to fetch an unpinned model).
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val role: ModelRole,
    val family: String,
    val quant: String,
    val downloadBytes: Long,
    val minRamBytes: Long,
    val recommendedRamBytes: Long,
    val requiresArm64: Boolean,
    val languages: List<String>,
    val license: String,
    val tagline: String,
    val strengths: String,
    val limits: String,
    val pinned: Boolean,
    val url: String,
    val sha256: String,
    val fileName: String,
    /** When true, [fileName] is a .tar.bz2 to extract into the model dir after download. */
    val extract: Boolean = false,
    /** True when the source requires manual license acceptance (can't be a clean in-app download). */
    val gated: Boolean = false,
) {
    val downloadMb: Int get() = (downloadBytes / (1024 * 1024)).toInt()
}

private const val GB = 1024L * 1024 * 1024

/**
 * The vetted set from ADR-001. Sizes/RAM are the design's honest estimates; a
 * model becomes downloadable ([pinned]=true) once its release asset + SHA-256
 * are locked. Everything here is Indic-capable and commercial-safe.
 */
object ModelCatalog {

    // --- Speech-to-text (local ASR) -----------------------------------------

    /** Whisper Tiny multilingual — REAL, pinned, downloadable + on-device runnable today. */
    val WHISPER_TINY = ModelInfo(
        id = "whisper-tiny-multilingual",
        displayName = "Whisper Tiny (multilingual)",
        role = ModelRole.ASR,
        family = "OpenAI Whisper · sherpa-onnx",
        quant = "fp32+int8",
        downloadBytes = 116_204_861L,
        minRamBytes = 2 * GB,
        recommendedRamBytes = 3 * GB,
        requiresArm64 = true,
        languages = listOf("hi", "en", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "or"),
        license = "MIT",
        tagline = "Fast offline speech-to-text — runs fully on-device today.",
        strengths = "Small + quick; the proven default local transcriber. No network, nothing leaves the phone.",
        limits = "Lower accuracy than Small/Sarvam; per-language (no Hinglish codemix); no diarization.",
        pinned = true,
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2",
        sha256 = "c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1",
        fileName = "sherpa-onnx-whisper-tiny.tar.bz2",
        extract = true,
    )

    val WHISPER_SMALL = ModelInfo(
        id = "whisper-small-multilingual",
        displayName = "Whisper Small (multilingual)",
        role = ModelRole.ASR,
        family = "OpenAI Whisper · sherpa-onnx",
        quant = "fp32+int8",
        downloadBytes = 639_387_718L,
        minRamBytes = 3 * GB,
        recommendedRamBytes = 4 * GB,
        requiresArm64 = true,
        languages = listOf("hi", "en", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "or"),
        license = "MIT",
        tagline = "Higher-accuracy offline speech-to-text for 11 Indian languages.",
        strengths = "Much cleaner transcripts than Tiny (fewer loops/hallucinations). Runs fully on-device.",
        limits = "Larger + slower than Tiny; per-language (no Hinglish codemix); no diarization.",
        pinned = true,
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2",
        sha256 = "486a46afbb7ba798507190ffe02fea2dd726049af212e774537efac6afb210a6",
        fileName = "sherpa-onnx-whisper-small.tar.bz2",
        extract = true,
    )

    val INDIC_CONFORMER = ModelInfo(
        id = "indic-conformer-600m",
        displayName = "IndicConformer 600M",
        role = ModelRole.ASR,
        family = "AI4Bharat · sherpa-onnx",
        quant = "int8",
        downloadBytes = 600L * 1024 * 1024,
        minRamBytes = 4 * GB,
        recommendedRamBytes = 6 * GB,
        requiresArm64 = true,
        languages = listOf("hi", "bn", "ta", "te", "mr", "gu", "kn", "ml", "pa", "or", "as", "ur"),
        license = "MIT",
        tagline = "Higher Indic accuracy, tuned for 22 Indian languages.",
        strengths = "Best local accuracy on Indian speech and accents.",
        limits = "Larger + heavier than Whisper; ONNX export still being verified.",
        pinned = false,
        url = "https://example.invalid/indic-conformer-600m.tar.bz2",
        sha256 = "",
        fileName = "indic-conformer-600m.tar.bz2",
    )

    // --- Enrichment LLM (local, phase 2) ------------------------------------

    val GEMMA_3_1B = ModelInfo(
        id = "gemma-3-1b-it-task",
        displayName = "Gemma 3 · 1B (instruct)",
        role = ModelRole.LLM,
        family = "Google Gemma · MediaPipe LiteRT",
        quant = "q8 .task",
        downloadBytes = 1_073_765_694L,
        minRamBytes = 3 * GB,
        recommendedRamBytes = 4 * GB,
        requiresArm64 = true,
        languages = listOf("multi"),
        license = "Gemma (accept on Hugging Face)",
        tagline = "Lightweight on-device notes LLM from Google.",
        strengths = "Small, cool, fast; strong quality for its size.",
        limits = "License-gated: accept Google's Gemma terms on Hugging Face, then it's downloadable.",
        pinned = false,
        url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q8_ekv2048.task",
        sha256 = "",
        fileName = "gemma-3-1b-it.task",
        gated = true,
    )

    val TINYLLAMA_11B = ModelInfo(
        id = "tinyllama-1.1b-chat-task",
        displayName = "TinyLlama · 1.1B (chat)",
        role = ModelRole.LLM,
        family = "TinyLlama · MediaPipe LiteRT",
        quant = "q8 .task",
        downloadBytes = 1_148_331_545L,
        minRamBytes = 3 * GB,
        recommendedRamBytes = 4 * GB,
        requiresArm64 = true,
        languages = listOf("en"),
        license = "Apache-2.0",
        tagline = "Compact English notes LLM. Fully open, no license wall.",
        strengths = "Apache-2.0, ungated, runs on-device via MediaPipe. Good English summaries.",
        limits = "English-focused; weaker on Indic than Qwen/Sarvam.",
        pinned = true,
        url = "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
        sha256 = "0f09dc7f792bb8d49b6629effaee3ed1a99e4506b082cd353471bdf391dee053",
        fileName = "tinyllama-1.1b-chat.task",
    )

    val SARVAM_1_2B = ModelInfo(
        id = "sarvam-1-2b-task",
        displayName = "Sarvam-1 · 2B",
        role = ModelRole.LLM,
        family = "Sarvam · MediaPipe LiteRT",
        quant = "q8 .task",
        downloadBytes = 2000L * 1024 * 1024,
        minRamBytes = 6 * GB,
        recommendedRamBytes = 6 * GB,
        requiresArm64 = true,
        languages = listOf("multi", "indic"),
        license = "check Sarvam license",
        tagline = "Strongest on-device Indic notes. For 6 GB+ phones.",
        strengths = "Best local Indic enrichment quality.",
        limits = "No LiteRT (.task) build published yet — not runnable on-device today. Use Qwen locally or the Sarvam API for Indic-strong notes.",
        pinned = false,
        url = "https://example.invalid/sarvam-1-2b.task",
        sha256 = "",
        fileName = "sarvam-1-2b.task",
    )

    val QWEN25_05B = ModelInfo(
        id = "qwen2.5-0.5b-instruct-task",
        displayName = "Qwen2.5 · 0.5B (instruct)",
        role = ModelRole.LLM,
        family = "Alibaba Qwen · MediaPipe LiteRT",
        quant = "q8 .task",
        downloadBytes = 546_660_344L,
        minRamBytes = 3 * GB,
        recommendedRamBytes = 4 * GB,
        requiresArm64 = true,
        languages = listOf("multi"),
        license = "Apache-2.0",
        tagline = "Ultra-light on-device notes LLM. Runs on most 64-bit phones.",
        strengths = "Tiny + fast; Apache-2.0. Real on-device summaries/to-dos, no network.",
        limits = "Shorter, simpler summaries than 1.5B; limited Indic vs Sarvam.",
        pinned = true,
        url = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        sha256 = "e608953f169aeb1bd7b9155fec2559825e08453fc209b84eda3a781ed0452fd2",
        fileName = "qwen2.5-0.5b-instruct.task",
    )

    val QWEN25_15B = ModelInfo(
        id = "qwen2.5-1.5b-instruct-task",
        displayName = "Qwen2.5 · 1.5B (instruct)",
        role = ModelRole.LLM,
        family = "Alibaba Qwen · MediaPipe LiteRT",
        quant = "q8 .task",
        downloadBytes = 1_597_913_616L,
        minRamBytes = 4 * GB,
        recommendedRamBytes = 6 * GB,
        requiresArm64 = true,
        languages = listOf("multi"),
        license = "Apache-2.0",
        tagline = "Real on-device notes LLM — proper summaries, key points & to-dos.",
        strengths = "Runs fully on-device via MediaPipe; Apache-2.0, commercial-safe. Much better than the extractive fallback.",
        limits = "~1.6 GB; wants 6 GB+ RAM for smooth use; Indic weaker than Sarvam-1.",
        pinned = true,
        url = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        sha256 = "8d867a7c93a6acf2892f08e0174e2f6f351ad256b7e3cfb6d6cd9c89794b42e0",
        fileName = "qwen2.5-1.5b-instruct.task",
    )

    // --- Embeddings (RAG) ---------------------------------------------------

    val E5_SMALL = ModelInfo(
        id = "multilingual-e5-small-int8",
        displayName = "multilingual-e5-small",
        role = ModelRole.EMBEDDING,
        family = "intfloat · ONNX Runtime",
        quant = "int8",
        downloadBytes = 35L * 1024 * 1024,
        minRamBytes = 2 * GB,
        recommendedRamBytes = 3 * GB,
        requiresArm64 = false,
        languages = listOf("multi"),
        license = "MIT",
        tagline = "Optional neural upgrade for semantic search.",
        strengths = "Would add true synonym/meaning matching on top of the built-in offline vector search.",
        limits = "Not required — vector search already works offline without it. Neural embedder wiring is a future upgrade.",
        pinned = false,
        url = "https://example.invalid/multilingual-e5-small-int8.onnx",
        sha256 = "",
        fileName = "multilingual-e5-small-int8.onnx",
    )

    val all: List<ModelInfo> = listOf(
        WHISPER_TINY, WHISPER_SMALL, INDIC_CONFORMER,
        QWEN25_15B, QWEN25_05B, TINYLLAMA_11B, GEMMA_3_1B, SARVAM_1_2B,
        E5_SMALL,
    )

    fun byRole(role: ModelRole): List<ModelInfo> = all.filter { it.role == role }

    fun byId(id: String): ModelInfo? = all.firstOrNull { it.id == id }
}
