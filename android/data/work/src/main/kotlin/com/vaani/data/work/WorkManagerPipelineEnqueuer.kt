package com.vaani.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vaani.domain.pipeline.PipelineEnqueuer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WorkManager-backed [PipelineEnqueuer].
 *
 * All transcribe→enrich→note work runs on ONE serial queue (a single unique-work
 * chain, [PIPELINE_QUEUE], with [ExistingWorkPolicy.APPEND_OR_REPLACE]) so recordings
 * process STRICTLY ONE AT A TIME. This is deliberate: the on-device LLM enricher
 * (MediaPipe/Gemma) mmaps hundreds of MB per instance, so letting WorkManager run
 * several recordings' workers in parallel (the default when each has its own unique
 * name) loads multiple LLM engines at once and OOM-crashes the whole process — seen
 * when syncing a batch of wearable recordings at once. Serialising bounds peak memory
 * to a single inference at a time.
 *
 * Idempotency: the worker no-ops a recording already READY, and APPEND_OR_REPLACE keeps
 * the queue intact, so re-enqueueing the same id just adds another (cheap, self-skipping)
 * node rather than running duplicates concurrently. Exponential backoff on retry; no
 * network constraint (LOCAL works offline; the API backend surfaces its own error).
 */
@Singleton
class WorkManagerPipelineEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
) : PipelineEnqueuer {

    private val workManager get() = WorkManager.getInstance(context)

    override fun enqueue(recordingId: String) {
        val request = OneTimeWorkRequestBuilder<TranscriptionWorker>()
            .setInputData(Data.Builder().putString(TranscriptionWorker.KEY_RECORDING_ID, recordingId).build())
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .addTag(tagFor(recordingId))
            .build()
        // APPEND to the single serial queue: each recording waits for the previous to
        // finish, so at most one LLM inference is resident at a time.
        workManager.enqueueUniqueWork(
            PIPELINE_QUEUE,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request,
        )
    }

    override fun cancel(recordingId: String) {
        // Cancel just this recording's node by its per-id tag (the queue itself lives on).
        workManager.cancelAllWorkByTag(tagFor(recordingId))
    }

    private fun tagFor(recordingId: String) = TranscriptionWorker.WORK_NAME_PREFIX + recordingId

    private companion object {
        const val TAG = "vaani-transcription"
        const val PIPELINE_QUEUE = "vaani-pipeline-queue"
    }
}
