package com.vaani.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.vaani.data.database.entity.EntityEntity
import com.vaani.data.database.entity.KeyPointEntity
import com.vaani.data.database.entity.NoteEntity
import com.vaani.data.database.entity.NoteEntityCrossRef
import com.vaani.data.database.entity.NoteTagCrossRef
import com.vaani.data.database.entity.NoteWithRelations
import com.vaani.data.database.entity.RecordingEntity
import com.vaani.data.database.entity.TagEntity
import com.vaani.data.database.entity.TodoEntity
import com.vaani.data.database.entity.TranscriptEntity
import com.vaani.data.database.entity.TranscriptSegmentEntity
import com.vaani.data.database.entity.TranscriptWithSegments
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Transaction
    @Query("SELECT * FROM note ORDER BY createdAtEpochMs DESC, id ASC")
    fun observeNotes(): Flow<List<NoteWithRelations>>

    @Transaction
    @Query("SELECT * FROM note WHERE id = :id LIMIT 1")
    fun observeNote(id: String): Flow<NoteWithRelations?>

    @Query("SELECT COUNT(*) FROM note")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertNote(note: NoteEntity)

    @Query("DELETE FROM note_key_point WHERE noteId = :noteId")
    suspend fun clearKeyPoints(noteId: String)

    @Query("DELETE FROM todo WHERE noteId = :noteId")
    suspend fun clearTodos(noteId: String)

    @Query("UPDATE todo SET status = :status, completedAtEpochMs = :completedAt WHERE id = :todoId")
    suspend fun setTodoStatus(todoId: String, status: String, completedAt: Long?)

    @Query("DELETE FROM note_tag WHERE noteId = :noteId")
    suspend fun clearTagLinks(noteId: String)

    @Query("DELETE FROM note_entity WHERE noteId = :noteId")
    suspend fun clearEntityLinks(noteId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyPoints(rows: List<KeyPointEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodos(rows: List<TodoEntity>)

    @Upsert
    suspend fun upsertTags(rows: List<TagEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagLinks(rows: List<NoteTagCrossRef>)

    @Upsert
    suspend fun upsertEntities(rows: List<EntityEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntityLinks(rows: List<NoteEntityCrossRef>)

    @Query("UPDATE note SET pipelineState = :state, pipelineProgress = :progress WHERE id = :noteId")
    suspend fun setPipelineState(noteId: String, state: String, progress: Float?)
}

@Dao
interface RecordingDao {

    @Upsert
    suspend fun upsert(recording: RecordingEntity)

    @Query("SELECT * FROM recording WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): RecordingEntity?

    /** Recordings not yet turned into a READY note — drives the Library "processing" rows. */
    @Query("SELECT * FROM recording WHERE pipelineState != 'READY' ORDER BY startedAtEpochMs DESC")
    fun observeActive(): Flow<List<RecordingEntity>>

    @Query("SELECT COUNT(*) FROM recording")
    suspend fun count(): Int

    @Query("UPDATE recording SET pipelineState = :state WHERE id = :recordingId")
    suspend fun setPipelineState(recordingId: String, state: String)
}

@Dao
interface TranscriptDao {

    @Transaction
    @Query("SELECT * FROM transcript WHERE recordingId = :recordingId LIMIT 1")
    fun observeByRecording(recordingId: String): Flow<TranscriptWithSegments?>

    @Transaction
    @Query(
        "SELECT t.* FROM transcript t " +
            "INNER JOIN note n ON n.recordingId = t.recordingId " +
            "WHERE n.id = :noteId LIMIT 1",
    )
    fun observeByNote(noteId: String): Flow<TranscriptWithSegments?>

    @Upsert
    suspend fun upsertTranscript(transcript: TranscriptEntity)

    @Query("DELETE FROM transcript_segment WHERE transcriptId = :transcriptId")
    suspend fun clearSegments(transcriptId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSegments(rows: List<TranscriptSegmentEntity>)
}
