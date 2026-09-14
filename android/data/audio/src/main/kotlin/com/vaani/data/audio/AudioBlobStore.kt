package com.vaani.data.audio

/**
 * A blob that has been imported into the content-addressed audio store.
 *
 * @property sha256 lowercase hex SHA-256 of the stored bytes; the content address.
 * @property absolutePath absolute path to the stored file on disk.
 * @property bytes number of bytes stored.
 */
data class StoredBlob(
    val sha256: String,
    val absolutePath: String,
    val bytes: Long,
)

/**
 * Content-addressed store for audio blobs.
 *
 * Blobs live in app-private storage and are keyed by the SHA-256 of their
 * content, so importing identical bytes is idempotent and never duplicates.
 */
interface AudioBlobStore {

    /** Import [source] file, returning the stored (deduplicated) blob. */
    suspend fun import(source: java.io.File, suggestedExt: String): StoredBlob

    /** Import from a raw [source] stream. The stream is fully consumed and closed by the caller's [source]; this method reads it to completion. */
    suspend fun import(source: java.io.InputStream, suggestedExt: String): StoredBlob

    /** Absolute file path (usable as a `file://`-style uri) for [sha256], or null if absent. */
    fun uriFor(sha256: String): String?

    /** Delete the blob for [sha256]; returns true if a file was removed. */
    suspend fun delete(sha256: String): Boolean

    /** Whether a blob for [sha256] exists. */
    suspend fun exists(sha256: String): Boolean
}
