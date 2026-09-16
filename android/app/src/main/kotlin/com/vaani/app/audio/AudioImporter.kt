package com.vaani.app.audio

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vaani.data.audio.AudioBlobStore
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.Recording
import com.vaani.domain.model.SyncState
import com.vaani.domain.pipeline.PipelineEnqueuer
import com.vaani.domain.repository.RecordingWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Imports a user-picked audio file into Vaani and kicks off transcription:
 *   content Uri → AudioBlobStore (content-addressed, deduped) → Recording row
 *   → PipelineEnqueuer (durable WorkManager job).
 *
 * The stored file's absolute path becomes [Recording.storageUri], which is what
 * both the ASR decoder and the Media3 player read — so import fixes playback and
 * feeds the real pipeline in one step.
 */
@Singleton
class AudioImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val blobStore: AudioBlobStore,
    private val recordings: RecordingWriter,
    private val enqueuer: PipelineEnqueuer,
) {

    data class Imported(val recordingId: String, val displayName: String)

    /** Import [uri], persist a Recording, enqueue processing. Returns the new recording id. */
    suspend fun import(uri: Uri): Imported = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val (displayName, size) = queryMeta(uri)
        val ext = displayName.substringAfterLast('.', "m4a")

        val blob = resolver.openInputStream(uri)?.use { input ->
            blobStore.import(input, ext)
        } ?: error("could not open $uri")

        val id = "rec-" + blob.sha256.take(12)
        val now = Clock.System.now()
        recordings.upsert(
            Recording(
                id = id,
                deviceId = "imported",
                sessionUlid = id,
                startedAt = now,
                tzOffsetMinutes = 0,
                durationMs = 0L, // filled by the pipeline once decoded
                codec = ext,
                sampleRate = 16_000,
                sha256 = blob.sha256,
                bytes = if (size > 0) size else blob.bytes,
                storageUri = blob.absolutePath,
                syncState = SyncState.PERSISTED,
                pipelineState = PipelineState.QUEUED,
            ),
        )
        enqueuer.enqueue(id)
        Imported(id, displayName)
    }

    private fun queryMeta(uri: Uri): Pair<String, Long> {
        var name = "audio"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }
}
