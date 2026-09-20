package com.vaani.domain.repository

import com.vaani.domain.model.Note
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.TodoStatus
import com.vaani.domain.model.Transcript

/**
 * Write surface for the transcription/enrichment pipeline to persist its
 * output. Read access stays behind [NotesRepository]; this is the mirror-image
 * seam for producers. Lives in :domain (pure interface) so the pipeline
 * depends on the contract, not on the Room module that implements it.
 */
interface NotesWriter {

    /**
     * Persists a [note] and, if present, its [transcript] (with segments) in a
     * single transaction. Existing children (key points, todos, tag/entity
     * links, segments) are replaced so the write is idempotent.
     */
    suspend fun upsertNote(note: Note, transcript: Transcript? = null)

    /** Updates the coarse pipeline state + progress surfaced to the UI for a note. */
    suspend fun setNotePipelineState(noteId: String, state: PipelineState, progress: Float? = null)

    /** Updates the pipeline state of the underlying recording. */
    suspend fun setRecordingPipelineState(recordingId: String, state: PipelineState)

    /**
     * Persists a single to-do's completion state (durable across sessions).
     * [completedAtEpochMs] is the moment it was marked DONE (null when reopened).
     */
    suspend fun setTodoStatus(todoId: String, status: TodoStatus, completedAtEpochMs: Long?)

    /**
     * Permanently delete a recording and everything derived from it — the note,
     * transcript, key points, todos, tag/entity links (all cascade in the DB) AND
     * the stored audio blob on disk. Returns the deleted recording's sha256 (or
     * null if there was no such recording) so callers can also drop the on-device
     * (wearable SD) copy if desired.
     */
    suspend fun deleteRecording(recordingId: String): String?
}
