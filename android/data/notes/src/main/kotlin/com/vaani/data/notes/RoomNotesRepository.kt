package com.vaani.data.notes

import com.vaani.data.database.dao.NoteDao
import com.vaani.data.database.dao.TranscriptDao
import com.vaani.data.database.mapper.toDomain
import com.vaani.domain.model.Note
import com.vaani.domain.model.Transcript
import com.vaani.domain.repository.NotesRepository
import com.vaani.domain.repository.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [NotesRepository]. Reads are Room [Flow]s mapped to domain models;
 * the UI observes exactly the same data the old fake exposed (seeded once on
 * first run, see [NotesSeeder]).
 */
@Singleton
class RoomNotesRepository @Inject constructor(
    private val noteDao: NoteDao,
    private val transcriptDao: TranscriptDao,
) : NotesRepository {

    // TODO(sync): the device→phone sync layer isn't built yet; serve the
    //  fixture snapshot from memory until it lands, then swap for a real source.
    private val syncFlow = MutableStateFlow(NotesSeedData.syncStatus)

    override fun observeNotes(): Flow<List<Note>> =
        noteDao.observeNotes().map { rows -> rows.map { it.toDomain() } }

    override fun observeNote(id: String): Flow<Note?> =
        noteDao.observeNote(id).map { it?.toDomain() }

    override fun observeTranscript(noteId: String): Flow<Transcript?> =
        transcriptDao.observeByNote(noteId).map { it?.toDomain() }

    override fun syncStatus(): Flow<SyncStatus> = syncFlow
}
