package com.vaani.domain.pipeline

/**
 * Seam for scheduling durable transcription/enrichment work (ARCHITECTURE.md
 * §5.7). Features/sync enqueue a recording by id and the durable layer
 * (WorkManager) guarantees it runs to completion across process death, with
 * retry/backoff. Kept in :domain so callers never touch WorkManager directly.
 */
interface PipelineEnqueuer {
    /**
     * Enqueue processing for [recordingId]. Idempotent: enqueueing the same
     * recording while one is pending/running keeps the existing work (unique).
     */
    fun enqueue(recordingId: String)

    /** Cancel any pending/running work for [recordingId]. */
    fun cancel(recordingId: String)
}
