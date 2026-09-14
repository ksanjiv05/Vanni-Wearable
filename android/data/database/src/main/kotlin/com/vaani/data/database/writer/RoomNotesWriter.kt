package com.vaani.data.database.writer

import androidx.room.withTransaction
import com.vaani.data.database.VaaniDatabase
import com.vaani.data.database.mapper.toCrossRef
import com.vaani.data.database.mapper.toEntity
import com.vaani.data.database.mapper.toEntityRow
import com.vaani.data.database.entity.NoteTagCrossRef
import com.vaani.domain.model.Note
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.Transcript
import com.vaani.domain.repository.NotesWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room-backed [NotesWriter]. All child rows for a note are cleared and
 * re-inserted inside one transaction so repeated pipeline emissions converge
 * to exactly the supplied state.
 */
@Singleton
class RoomNotesWriter @Inject constructor(
    private val db: VaaniDatabase,
) : NotesWriter {

    override suspend fun upsertNote(note: Note, transcript: Transcript?) {
        db.withTransaction {
            val noteDao = db.noteDao()
            noteDao.upsertNote(note.toEntity())

            noteDao.clearKeyPoints(note.id)
            noteDao.insertKeyPoints(note.keyPoints.map { it.toEntity() })

            noteDao.clearTodos(note.id)
            noteDao.insertTodos(note.todos.map { it.toEntity() })

            noteDao.clearTagLinks(note.id)
            if (note.tags.isNotEmpty()) {
                noteDao.upsertTags(note.tags.map { it.toEntity() })
                noteDao.insertTagLinks(note.tags.map { NoteTagCrossRef(note.id, it.id) })
            }

            noteDao.clearEntityLinks(note.id)
            if (note.entities.isNotEmpty()) {
                noteDao.upsertEntities(note.entities.map { it.toEntityRow() })
                noteDao.insertEntityLinks(note.entities.map { it.toCrossRef(note.id) })
            }

            if (transcript != null) {
                val transcriptDao = db.transcriptDao()
                transcriptDao.upsertTranscript(transcript.toEntity())
                transcriptDao.clearSegments(transcript.id)
                transcriptDao.insertSegments(transcript.segments.map { it.toEntity() })
            }
        }
    }

    override suspend fun setNotePipelineState(noteId: String, state: PipelineState, progress: Float?) {
        db.noteDao().setPipelineState(noteId, state.name, progress)
    }

    override suspend fun setRecordingPipelineState(recordingId: String, state: PipelineState) {
        db.recordingDao().setPipelineState(recordingId, state.name)
    }
}
