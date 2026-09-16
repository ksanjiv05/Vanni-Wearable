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
 * WorkManager-backed [PipelineEnqueuer]. One unique work per recording (keyed by
 * id) so re-enqueueing while pending/running is a no-op rather than a duplicate
 * run. Exponential backoff on retry; no network constraint (the LOCAL backend
 * works offline — the API backend surfaces its own KeyMissing/Network error and
 * the worker decides retry).
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
            .build()
        workManager.enqueueUniqueWork(
            uniqueName(recordingId),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel(recordingId: String) {
        workManager.cancelUniqueWork(uniqueName(recordingId))
    }

    private fun uniqueName(recordingId: String) = TranscriptionWorker.WORK_NAME_PREFIX + recordingId

    private companion object {
        const val TAG = "vaani-transcription"
    }
}
