package com.vaani.domain.repository

import com.vaani.domain.model.Recording

/**
 * Read access to a single recording row by id (ARCHITECTURE.md §4.3). The
 * durable pipeline worker uses this to resolve a queued recordingId into the
 * audio handle it transcribes. Kept in :domain so the worker depends on the
 * contract, not the Room module.
 */
interface RecordingStore {
    suspend fun get(id: String): Recording?
}
