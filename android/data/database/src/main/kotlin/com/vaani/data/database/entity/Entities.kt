package com.vaani.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room persistence schema mirroring the pure-Kotlin domain models in :domain
 * (ARCHITECTURE.md §4.3). Time fields are stored as epoch-millis [Long];
 * enums are stored as their [Enum.name] [String]. Mapping to/from the domain
 * lives in [com.vaani.data.database.mapper].
 */

@Entity(tableName = "recording")
data class RecordingEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val sessionUlid: String,
    val startedAtEpochMs: Long,
    val tzOffsetMinutes: Int,
    val durationMs: Long,
    val codec: String,
    val sampleRate: Int,
    val sha256: String,
    val bytes: Long,
    val storageUri: String?,
    val syncState: String,
    val pipelineState: String,
)

@Entity(
    tableName = "transcript",
    foreignKeys = [
        ForeignKey(
            entity = RecordingEntity::class,
            parentColumns = ["id"],
            childColumns = ["recordingId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recordingId")],
)
data class TranscriptEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val provider: String,
    val model: String,
    val mode: String,
    val languageCode: String,
    val fullText: String,
    val createdAtEpochMs: Long,
)

@Entity(
    tableName = "transcript_segment",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transcriptId")],
)
data class TranscriptSegmentEntity(
    @PrimaryKey val id: String,
    val transcriptId: String,
    val idx: Int,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String,
    val text: String,
    val confidence: Float?,
)

@Entity(tableName = "note")
data class NoteEntity(
    @PrimaryKey val id: String,
    val recordingId: String,
    val title: String,
    val summaryShort: String,
    val summaryLong: String,
    val languageCode: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val pinned: Boolean,
    val archived: Boolean,
    val userEdited: Boolean,
    val durationMs: Long,
    val speakerCount: Int,
    val pipelineState: String,
    val pipelineProgress: Float?,
)

@Entity(
    tableName = "note_key_point",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class KeyPointEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val idx: Int,
    val text: String,
    val sourceSegmentId: String?,
    val sourceStartMs: Long,
)

@Entity(
    tableName = "todo",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class TodoEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val text: String,
    val dueHint: String?,
    val assignee: String?,
    val priority: String,
    val status: String,
    val sourceSegmentId: String?,
    val sourceStartMs: Long?,
    val completedAtEpochMs: Long?,
)

@Entity(tableName = "tag")
data class TagEntity(
    @PrimaryKey val id: String,
    val name: String,
)

/** Join table between [NoteEntity] and [TagEntity] (many-to-many). */
@Entity(
    tableName = "note_tag",
    primaryKeys = ["noteId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class NoteTagCrossRef(
    val noteId: String,
    val tagId: String,
)

@Entity(tableName = "entity")
data class EntityEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
)

/**
 * Join table between [NoteEntity] and [EntityEntity]. The per-note
 * [mentionCount] lives on the edge because it is note-scoped (domain
 * [com.vaani.domain.model.Entity.mentionCount]).
 */
@Entity(
    tableName = "note_entity",
    primaryKeys = ["noteId", "entityId"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EntityEntity::class,
            parentColumns = ["id"],
            childColumns = ["entityId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entityId")],
)
data class NoteEntityCrossRef(
    val noteId: String,
    val entityId: String,
    val mentionCount: Int,
)
