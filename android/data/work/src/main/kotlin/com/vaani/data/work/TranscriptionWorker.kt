package com.vaani.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaani.data.pipeline.IngestPipeline
import com.vaani.domain.ai.AudioRef
import com.vaani.domain.model.Outcome
import com.vaani.domain.repository.RecordingStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Durable wrapper over [IngestPipeline] (ARCHITECTURE.md §5.7). Resolves the
 * queued recording, runs it through the two AI stages, and translates the
 * outcome into a WorkManager result:
 *
 *  - success            → [Result.success]
 *  - retryable failure  → [Result.retry] (WorkManager applies exponential backoff)
 *  - permanent failure  → [Result.failure] (pipeline already marked the row FAILED)
 *
 * Injected with Hilt (@HiltWorker) so it can depend on app-scoped singletons.
 */
@HiltWorker
class TranscriptionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val pipeline: IngestPipeline,
    private val recordings: RecordingStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo =
        TranscriptionNotifications.foregroundInfo(applicationContext, "Processing your note…")

    override suspend fun doWork(): Result {
        val recordingId = inputData.getString(KEY_RECORDING_ID)
            ?: return Result.failure()

        // Promote to a foreground service so on-device Whisper (minutes-long) is
        // not killed when the app is backgrounded. Best-effort: if the OS refuses
        // (e.g. notifications disabled), keep processing in the background.
        runCatching { setForeground(getForegroundInfo()) }

        val recording = recordings.get(recordingId) ?: return Result.failure()
        val storageUri = recording.storageUri
            ?: return Result.failure() // no audio to process

        val audio = AudioRef(
            recordingId = recording.id,
            storageUri = storageUri,
            durationMs = recording.durationMs,
            sampleRate = recording.sampleRate,
            codec = recording.codec,
        )

        return when (val outcome = pipeline.process(audio)) {
            is Outcome.Ok -> Result.success()
            is Outcome.Err ->
                if (isTransient(outcome)) Result.retry() else Result.failure()
        }
    }

    /** Network / transient failures are worth a backed-off retry; the rest aren't. */
    private fun isTransient(err: Outcome.Err): Boolean = when (err.error) {
        is com.vaani.domain.model.AppError.Network -> runAttemptCount < MAX_ATTEMPTS
        else -> false
    }

    companion object {
        const val KEY_RECORDING_ID = "recordingId"
        const val WORK_NAME_PREFIX = "transcription-"
        private const val MAX_ATTEMPTS = 5
    }
}
