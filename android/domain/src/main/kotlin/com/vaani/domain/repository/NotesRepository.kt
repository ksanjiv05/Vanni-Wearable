package com.vaani.domain.repository

import com.vaani.domain.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Read surface for notes. Milestone A ships only a fake implementation
 * ([com.vaani.domain.repository.FakeNotesRepository]); the real Room-backed
 * repository lands in :data:notes in a later milestone.
 */
interface NotesRepository {
    fun observeNotes(): Flow<List<Note>>
    fun observeNote(id: String): Flow<Note?>
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
