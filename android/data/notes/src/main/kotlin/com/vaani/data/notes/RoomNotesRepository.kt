package com.vaani.data.notes

import com.vaani.data.database.dao.NoteDao
import com.vaani.data.database.dao.RecordingDao
import com.vaani.data.database.dao.TranscriptDao
import com.vaani.data.database.mapper.toDomain
import com.vaani.domain.model.Note
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.Transcript
import com.vaani.domain.repository.NotesRepository
import com.vaani.domain.repository.ProcessingRecording
import com.vaani.domain.repository.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [NotesRepository]. Reads are Room [Flow]s mapped to domain models.
 * The app starts EMPTY (no seed data); content appears only from real imports/
 * recordings flowing through the ingest pipeline.
 */
@Singleton
class RoomNotesRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val transcriptDao: TranscriptDao,
    private val recordingDao: RecordingDao,
) : NotesRepository {

    override fun observeNotes(): Flow<List<Note>> =
        noteDao.observeNotes().map { rows -> rows.map { it.toDomain() } }

    override fun observeNote(id: String): Flow<Note?> =
        noteDao.observeNote(id).map { it?.toDomain() }

    override fun observeTranscript(noteId: String): Flow<Transcript?> =
        transcriptDao.observeByNote(noteId).map { it?.toDomain() }

    override fun observeProcessing(): Flow<List<ProcessingRecording>> =
        recordingDao.observeActive().map { rows ->
            rows.map { r ->
                ProcessingRecording(
                    recordingId = r.id,
                    title = r.sessionUlid.removePrefix("rec-").let { "Imported audio" },
                    state = runCatching { PipelineState.valueOf(r.pipelineState) }
                        .getOrDefault(PipelineState.QUEUED),
                    bytes = r.bytes,
                    startedAt = Instant.fromEpochMilliseconds(r.startedAtEpochMs),
                )
            }
        }

    // No device→phone sync layer yet: report "not syncing" rather than a fake
    // in-progress banner. Replaced with a real source when sync lands.
    override fun syncStatus(): Flow<SyncStatus> = flowOf(
        SyncStatus(
            isSyncing = false,
            pendingRecordings = 0,
            pendingBytes = 0L,
            progress = 0f,
            transport = "",
            lastSyncedLabel = "",
        ),
    )
}
