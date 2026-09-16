package com.vaani.data.device

import com.vaani.data.audio.AudioBlobStore
import com.vaani.domain.device.DeviceLink
import com.vaani.domain.device.SyncProgress
import com.vaani.domain.model.Outcome
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.Recording
import com.vaani.domain.model.SyncState
import com.vaani.domain.pipeline.PipelineEnqueuer
import com.vaani.domain.repository.RecordingWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pulls recordings off the wearable's SD card and feeds them into the SAME ingest
 * pipeline as file-import: pull bytes → AudioBlobStore (content-addressed, deduped)
 * → Recording row → PipelineEnqueuer (durable WorkManager transcribe→enrich→note).
 *
 * Deduplication is free: the blob store is keyed by SHA-256 and the Recording id is
 * derived from it, so re-syncing an already-imported recording is idempotent.
 */
@Singleton
class WearableSyncManager @Inject constructor(
    private val blobStore: AudioBlobStore,
    private val recordings: RecordingWriter,
    private val enqueuer: PipelineEnqueuer,
) {
    /** [link] is passed in (not injected) to avoid a DI cycle with DeviceLinkImpl. */
    fun sync(link: DeviceLink, deleteAfterSync: Boolean = false): Flow<SyncProgress> = flow {
        when (val listed = link.listRecordings()) {
            is Outcome.Err -> { emit(SyncProgress.Failed(errText(listed))); return@flow }
            is Outcome.Ok -> {
                val files = listed.value.filter { !it.isDir && isAudio(it.name) }
                emit(SyncProgress.Started(files.size))
                var imported = 0
                var deleted = 0
                files.forEachIndexed { i, f ->
                    val path = VAANI_DIR + "/" + f.name.trimStart('/')
                    when (val pulled = link.pullRecording(path)) {
                        is Outcome.Err -> emit(SyncProgress.Skipped(f.name, errText(pulled)))
                        is Outcome.Ok -> {
                            val bytes = pulled.value
                            if (bytes.isEmpty()) {
                                emit(SyncProgress.Skipped(f.name, "empty"))
                            } else {
                                val ext = f.name.substringAfterLast('.', "wav")
                                val blob = blobStore.import(bytes.inputStream(), ext)
                                val id = "rec-" + blob.sha256.take(12)
                                val now = Clock.System.now()
                                recordings.upsert(
                                    Recording(
                                        id = id,
                                        deviceId = "wearable",
                                        sessionUlid = id,
                                        startedAt = now,
                                        tzOffsetMinutes = 0,
                                        durationMs = 0L,
                                        codec = ext,
                                        sampleRate = 16_000,
                                        sha256 = blob.sha256,
                                        bytes = blob.bytes,
                                        storageUri = blob.absolutePath,
                                        syncState = SyncState.PERSISTED,
                                        pipelineState = PipelineState.QUEUED,
                                    ),
                                )
                                enqueuer.enqueue(id)
                                imported++
                                emit(SyncProgress.Item(i + 1, files.size, f.name, blob.bytes, id))
                                // Auto-delete: only AFTER the bytes are durably persisted to the phone
                                // (blob store write + Recording row committed). Safe — the recording
                                // survives on-device even if the pipeline later fails, so no data loss.
                                if (deleteAfterSync) {
                                    when (link.deleteFile(path)) {
                                        is Outcome.Ok -> { deleted++; emit(SyncProgress.Deleted(f.name)) }
                                        is Outcome.Err -> { /* keep the file on failure; not fatal */ }
                                    }
                                }
                            }
                        }
                    }
                }
                emit(SyncProgress.Done(imported, files.size, deleted))
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun isAudio(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("wav", "m4a", "mp3", "aac", "opus", "ogg", "flac")

    private fun errText(e: Outcome.Err): String = when (val err = e.error) {
        is com.vaani.domain.model.AppError.Network -> err.message
        is com.vaani.domain.model.AppError.NotFound -> "not found: ${err.id}"
        is com.vaani.domain.model.AppError.Unknown -> err.message
        else -> "failed"
    }

    companion object { const val VAANI_DIR = "/vaani" }
}
