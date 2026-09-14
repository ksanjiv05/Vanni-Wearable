package com.vaani.data.audio

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Pure, Android-free core of the content-addressed audio store.
 *
 * Operates on a plain base directory so it can be exercised in JVM unit tests
 * with a temp dir, without an Android [android.content.Context]. The Hilt-injected
 * store delegates to an instance of this rooted at `context.filesDir/audio`.
 *
 * SHA-256 is streamed with an 8 KiB buffer while copying; the full file is never
 * held in memory.
 */
internal class AudioBlobStoreCore(private val baseDir: File) {

    init {
        if (!baseDir.exists()) baseDir.mkdirs()
    }

    private fun fileName(sha256: String, ext: String): String {
        val cleanExt = ext.trim().removePrefix(".")
        return if (cleanExt.isEmpty()) sha256 else "$sha256.$cleanExt"
    }

    /** Find any existing stored file whose name starts with [sha256] (ext-agnostic). */
    private fun existingFileFor(sha256: String): File? =
        baseDir.listFiles()?.firstOrNull { f ->
            val name = f.name
            name == sha256 || name.startsWith("$sha256.")
        }

    fun import(source: File, suggestedExt: String): StoredBlob =
        source.inputStream().use { import(it, suggestedExt) }

    fun import(source: InputStream, suggestedExt: String): StoredBlob {
        if (!baseDir.exists()) baseDir.mkdirs()
        // Stream into a temp file while computing SHA-256, then rename to its
        // content address. Avoids reading the whole file into memory.
        val digest = MessageDigest.getInstance("SHA-256")
        val temp = File.createTempFile("import", ".part", baseDir)
        var total = 0L
        try {
            source.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        total += read
                    }
                    output.flush()
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

            existingFileFor(sha256)?.let { existing ->
                // Idempotent: identical content already stored.
                temp.delete()
                return StoredBlob(sha256, existing.absolutePath, existing.length())
            }

            val dest = File(baseDir, fileName(sha256, suggestedExt))
            if (!temp.renameTo(dest)) {
                temp.copyTo(dest, overwrite = true)
                temp.delete()
            }
            return StoredBlob(sha256, dest.absolutePath, dest.length())
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    fun uriFor(sha256: String): String? = existingFileFor(sha256)?.absolutePath

    fun delete(sha256: String): Boolean = existingFileFor(sha256)?.delete() ?: false

    fun exists(sha256: String): Boolean = existingFileFor(sha256) != null
}
