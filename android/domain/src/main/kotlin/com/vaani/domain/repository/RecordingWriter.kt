package com.vaani.domain.repository

import com.vaani.domain.model.Recording

/**
 * Write access for recordings (ARCHITECTURE.md §4.3). Used by the audio-import
 * flow to persist a new [Recording] row before the ingest pipeline processes it.
 * Kept in :domain so the importer depends on the contract, not the Room module.
 */
interface RecordingWriter {
    suspend fun upsert(recording: Recording)
}
