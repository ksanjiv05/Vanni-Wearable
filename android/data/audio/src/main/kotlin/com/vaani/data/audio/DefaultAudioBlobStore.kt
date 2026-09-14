package com.vaani.data.audio

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt-injected [AudioBlobStore] backed by app-private storage at
 * `context.filesDir/audio`. All blocking I/O runs on [Dispatchers.IO].
 */
@Singleton
internal class DefaultAudioBlobStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioBlobStore {

    private val core: AudioBlobStoreCore by lazy {
        AudioBlobStoreCore(File(context.filesDir, "audio"))
    }

    override suspend fun import(source: File, suggestedExt: String): StoredBlob =
        withContext(Dispatchers.IO) { core.import(source, suggestedExt) }

    override suspend fun import(source: InputStream, suggestedExt: String): StoredBlob =
        withContext(Dispatchers.IO) { core.import(source, suggestedExt) }

    override fun uriFor(sha256: String): String? = core.uriFor(sha256)

    override suspend fun delete(sha256: String): Boolean =
        withContext(Dispatchers.IO) { core.delete(sha256) }

    override suspend fun exists(sha256: String): Boolean =
        withContext(Dispatchers.IO) { core.exists(sha256) }
}
