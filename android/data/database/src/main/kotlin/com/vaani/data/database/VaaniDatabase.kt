package com.vaani.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaani.data.database.dao.NoteDao
import com.vaani.data.database.dao.RecordingDao
import com.vaani.data.database.dao.TranscriptDao
import com.vaani.data.database.entity.EntityEntity
import com.vaani.data.database.entity.KeyPointEntity
import com.vaani.data.database.entity.NoteEntity
import com.vaani.data.database.entity.NoteEntityCrossRef
import com.vaani.data.database.entity.NoteTagCrossRef
import com.vaani.data.database.entity.RecordingEntity
import com.vaani.data.database.entity.TagEntity
import com.vaani.data.database.entity.TodoEntity
import com.vaani.data.database.entity.TranscriptEntity
import com.vaani.data.database.entity.TranscriptSegmentEntity

/**
 * Single Room database for Vaani (ARCHITECTURE.md §4.3). exportSchema is off
 * for now; enable + wire a schema dir once migrations start (post-milestone).
 * SQLCipher lands later via a SupportFactory — not needed for this milestone.
 */
@Database(
    entities = [
        RecordingEntity::class,
        TranscriptEntity::class,
        TranscriptSegmentEntity::class,
        NoteEntity::class,
        KeyPointEntity::class,
        TodoEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        EntityEntity::class,
        NoteEntityCrossRef::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class VaaniDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun recordingDao(): RecordingDao
    abstract fun transcriptDao(): TranscriptDao
}
