package com.vaani.data.database.entity

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

/**
 * Aggregate read POJOs used by [com.vaani.data.database.dao] with @Transaction
 * so Room hydrates a note (or transcript) and all its children in one shot.
 */

/** An entity edge for a note, carrying the note-scoped mentionCount + the entity row. */
data class EntityEdgeWithEntity(
    @Embedded val edge: NoteEntityCrossRef,
    @Relation(parentColumn = "entityId", entityColumn = "id")
    val entity: EntityEntity,
)

data class NoteWithRelations(
    @Embedded val note: NoteEntity,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val keyPoints: List<KeyPointEntity>,
    @Relation(parentColumn = "id", entityColumn = "noteId")
    val todos: List<TodoEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteTagCrossRef::class,
            parentColumn = "noteId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
    @Relation(
        entity = NoteEntityCrossRef::class,
        parentColumn = "id",
        entityColumn = "noteId",
    )
    val entityEdges: List<EntityEdgeWithEntity>,
)

data class TranscriptWithSegments(
    @Embedded val transcript: TranscriptEntity,
    @Relation(parentColumn = "id", entityColumn = "transcriptId")
    val segments: List<TranscriptSegmentEntity>,
)
