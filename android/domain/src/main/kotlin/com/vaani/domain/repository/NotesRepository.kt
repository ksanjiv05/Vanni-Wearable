package com.vaani.domain.repository

import com.vaani.domain.model.Note
import com.vaani.domain.model.Transcript
import kotlinx.coroutines.flow.Flow

/**
 * Read surface for notes. Presentation layers depend ONLY on this interface;
 * the concrete data source (fake now, Room-backed :data:notes later) is bound
 * in :app and is never referenced directly by features.
 */
interface NotesRepository {
    fun observeNotes(): Flow<List<Note>>
    fun observeNote(id: String): Flow<Note?>
    fun observeTranscript(noteId: String): Flow<Transcript?>
    fun syncStatus(): Flow<SyncStatus>
}

/** Snapshot of the device→phone sync, drives the Library banner. */
data class SyncStatus(
    val isSyncing: Boolean,
    val pendingRecordings: Int,
    val pendingBytes: Long,
    val progress: Float,
    val transport: String,
    val lastSyncedLabel: String,
)
