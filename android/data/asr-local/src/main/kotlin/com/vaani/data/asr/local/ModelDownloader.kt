package com.vaani.data.asr.local

import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * A downloadable on-device model (ADR-001 §4). Weights are never shipped in the
 * APK; they are fetched on first use, verified by SHA-256, and cached in
 * app-private storage.
 */
data class ModelSpec(
    val id: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long,
    val fileName: String,
)

/** Catalogue of the local ASR models this build knows how to fetch. */
object AsrModels {
    const val WHISPER_TINY_ID = "whisper-tiny-multilingual"
    const val WHISPER_SMALL_ID = "whisper-small-multilingual"

    /** Whisper-small multilingual, the proven local-v1 model (ADR-001 §4). */
    val WHISPER_SMALL = ModelSpec(
        id = WHISPER_SMALL_ID,
        url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/" +
            "sherpa-onnx-whisper-small.tar.bz2",
        sha256 = "", // filled when the exact release asset is pinned
        sizeBytes = 0L,
        fileName = "sherpa-onnx-whisper-small.tar.bz2",
    )
}

/** Outcome of a resumable download, kept vendor-free for JVM tests. */
sealed interface DownloadResult {
    data class Done(val file: File) : DownloadResult
    data class Failed(val reason: String) : DownloadResult
}

/**
 * Content-verified, resumable model downloader. Pure file/stream logic so it is
 * JVM-testable without Android or the network: callers supply an [openRange]
 * that yields bytes from a given offset (an OkHttp `Range` request in prod, a
 * byte-array slice in tests). A partial download resumes from the `.part`
 * file's current length; the finished file is only promoted to its final name
 * after the SHA-256 matches.
 */
class ModelDownloader(private val baseDir: File) {

    fun resolved(spec: ModelSpec): File = File(baseDir, spec.fileName)

    fun isPresent(spec: ModelSpec): Boolean {
        val f = resolved(spec)
        return f.exists() && (spec.sizeBytes == 0L || f.length() == spec.sizeBytes)
    }

    /**
     * Downloads [spec] resumably. [openRange] returns a stream starting at the
     * given byte offset (or null on error). [onProgress] reports fraction done.
     */
    fun download(
        spec: ModelSpec,
        openRange: (offset: Long) -> InputStream?,
        onProgress: (Float) -> Unit = {},
    ): DownloadResult {
        // Fail closed: never fetch/promote a model whose integrity isn't pinned.
        // An empty sha256 would make verify() a no-op and let a corrupt or
        // MITM'd payload be promoted as if valid. A model must be pinned first.
        if (spec.sha256.isBlank()) {
            return DownloadResult.Failed("model ${spec.id} has no pinned sha256; refusing to download")
        }
        baseDir.mkdirs()
        val target = resolved(spec)
        if (isPresent(spec) && verify(target, spec.sha256)) return DownloadResult.Done(target)

        val part = File(baseDir, spec.fileName + ".part")
        var offset = if (part.exists()) part.length() else 0L

        val stream = openRange(offset) ?: return DownloadResult.Failed("open failed at $offset")
        stream.use { input ->
            RandomAccessFile(part, "rw").use { out ->
                out.seek(offset)
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    offset += n
                    if (spec.sizeBytes > 0) onProgress((offset.toFloat() / spec.sizeBytes).coerceIn(0f, 1f))
                }
            }
        }

        if (spec.sha256.isNotEmpty() && !verify(part, spec.sha256)) {
            part.delete()
            return DownloadResult.Failed("sha256 mismatch")
        }
        if (!part.renameTo(target)) return DownloadResult.Failed("promote failed")
        onProgress(1f)
        return DownloadResult.Done(target)
    }

    /** True when [file]'s SHA-256 hex equals [expected] (empty expected = skip). */
    fun verify(file: File, expected: String): Boolean {
        if (expected.isEmpty()) return true
        if (!file.exists()) return false
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }.equals(expected, ignoreCase = true)
    }
}
