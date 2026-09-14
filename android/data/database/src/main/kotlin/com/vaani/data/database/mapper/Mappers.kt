package com.vaani.data.database.mapper

import com.vaani.data.database.entity.EntityEdgeWithEntity
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
import com.vaani.domain.model.Entity
import com.vaani.domain.model.EntityType
import com.vaani.domain.model.KeyPoint
import com.vaani.domain.model.Note
import com.vaani.domain.model.PipelineState
import com.vaani.domain.model.Priority
import com.vaani.domain.model.Recording
import com.vaani.domain.model.SyncState
import com.vaani.domain.model.Tag
import com.vaani.domain.model.Todo
import com.vaani.domain.model.TodoStatus
import com.vaani.domain.model.Transcript
import com.vaani.domain.model.TranscriptSegment
import kotlinx.datetime.Instant

/**
 * Pure entity <-> domain mappers. Kept free of Android/Room types so they are
 * unit-testable on the plain JVM. Instant is stored as epoch-millis Long;
 * enums as their [Enum.name]. Every domain field is round-trip covered
 * (see mapper tests).
 */

// --- Instant helpers ---------------------------------------------------------

internal fun Instant.toEpochMs(): Long = toEpochMilliseconds()

internal fun Long.toInstant(): Instant = Instant.fromEpochMilliseconds(this)

// --- Recording ---------------------------------------------------------------

fun RecordingEntity.toDomain(): Recording = Recording(
    id = id,
    deviceId = deviceId,
    sessionUlid = sessionUlid,
    startedAt = startedAtEpochMs.toInstant(),
    tzOffsetMinutes = tzOffsetMinutes,
    durationMs = durationMs,
    codec = codec,
    sampleRate = sampleRate,
    sha256 = sha256,
    bytes = bytes,
    storageUri = storageUri,
    syncState = SyncState.valueOf(syncState),
    pipelineState = PipelineState.valueOf(pipelineState),
)

fun Recording.toEntity(): RecordingEntity = RecordingEntity(
    id = id,
    deviceId = deviceId,
    sessionUlid = sessionUlid,
    startedAtEpochMs = startedAt.toEpochMs(),
    tzOffsetMinutes = tzOffsetMinutes,
    durationMs = durationMs,
    codec = codec,
    sampleRate = sampleRate,
    sha256 = sha256,
    bytes = bytes,
    storageUri = storageUri,
    syncState = syncState.name,
    pipelineState = pipelineState.name,
)

// --- Transcript + segments ---------------------------------------------------

fun TranscriptSegmentEntity.toDomain(): TranscriptSegment = TranscriptSegment(
    id = id,
    transcriptId = transcriptId,
    idx = idx,
    startMs = startMs,
    endMs = endMs,
    speakerId = speakerId,
    text = text,
    confidence = confidence,
)

fun TranscriptSegment.toEntity(): TranscriptSegmentEntity = TranscriptSegmentEntity(
    id = id,
    transcriptId = transcriptId,
    idx = idx,
    startMs = startMs,
    endMs = endMs,
    speakerId = speakerId,
    text = text,
    confidence = confidence,
)

fun TranscriptWithSegments.toDomain(): Transcript = Transcript(
    id = transcript.id,
    recordingId = transcript.recordingId,
    provider = transcript.provider,
    model = transcript.model,
    mode = transcript.mode,
    languageCode = transcript.languageCode,
    fullText = transcript.fullText,
    createdAt = transcript.createdAtEpochMs.toInstant(),
    segments = segments.sortedBy { it.idx }.map { it.toDomain() },
)

fun Transcript.toEntity(): TranscriptEntity = TranscriptEntity(
    id = id,
    recordingId = recordingId,
    provider = provider,
    model = model,
    mode = mode,
    languageCode = languageCode,
    fullText = fullText,
    createdAtEpochMs = createdAt.toEpochMs(),
)

// --- KeyPoint / Todo / Tag / Entity ------------------------------------------

fun KeyPointEntity.toDomain(): KeyPoint = KeyPoint(
    id = id,
    noteId = noteId,
    idx = idx,
    text = text,
    sourceSegmentId = sourceSegmentId,
    sourceStartMs = sourceStartMs,
)

fun KeyPoint.toEntity(): KeyPointEntity = KeyPointEntity(
    id = id,
    noteId = noteId,
    idx = idx,
    text = text,
    sourceSegmentId = sourceSegmentId,
    sourceStartMs = sourceStartMs,
)

fun TodoEntity.toDomain(): Todo = Todo(
    id = id,
    noteId = noteId,
    text = text,
    dueHint = dueHint,
    assignee = assignee,
    priority = Priority.valueOf(priority),
    status = TodoStatus.valueOf(status),
    sourceSegmentId = sourceSegmentId,
    sourceStartMs = sourceStartMs,
    completedAt = completedAtEpochMs?.toInstant(),
)

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    noteId = noteId,
    text = text,
    dueHint = dueHint,
    assignee = assignee,
    priority = priority.name,
    status = status.name,
    sourceSegmentId = sourceSegmentId,
    sourceStartMs = sourceStartMs,
    completedAtEpochMs = completedAt?.toEpochMs(),
)

fun TagEntity.toDomain(): Tag = Tag(id = id, name = name)

fun Tag.toEntity(): TagEntity = TagEntity(id = id, name = name)

/** Rebuilds the domain [Entity] from an edge (mentionCount) + the entity row. */
fun EntityEdgeWithEntity.toDomain(): Entity = Entity(
    id = entity.id,
    type = EntityType.valueOf(entity.type),
    name = entity.name,
    mentionCount = edge.mentionCount,
)

/** Splits a domain [Entity] into its shared row + a per-note edge. */
fun Entity.toEntityRow(): EntityEntity = EntityEntity(
    id = id,
    type = type.name,
    name = name,
)

fun Entity.toCrossRef(noteId: String): NoteEntityCrossRef = NoteEntityCrossRef(
    noteId = noteId,
    entityId = id,
    mentionCount = mentionCount,
)

// --- Note (aggregate) --------------------------------------------------------

fun NoteWithRelations.toDomain(): Note = Note(
    id = note.id,
    recordingId = note.recordingId,
    title = note.title,
    summaryShort = note.summaryShort,
    summaryLong = note.summaryLong,
    languageCode = note.languageCode,
    createdAt = note.createdAtEpochMs.toInstant(),
    updatedAt = note.updatedAtEpochMs.toInstant(),
    pinned = note.pinned,
    archived = note.archived,
    userEdited = note.userEdited,
    // Deterministic ordering so the UI is byte-for-byte stable regardless of
    // Room's row-fetch order: keyPoints by idx, the rest by id.
    tags = tags.sortedBy { it.id }.map { it.toDomain() },
    keyPoints = keyPoints.sortedBy { it.idx }.map { it.toDomain() },
    todos = todos.sortedBy { it.id }.map { it.toDomain() },
    entities = entityEdges.sortedBy { it.entity.id }.map { it.toDomain() },
    durationMs = note.durationMs,
    speakerCount = note.speakerCount,
    pipelineState = PipelineState.valueOf(note.pipelineState),
    pipelineProgress = note.pipelineProgress,
)

/** The core [NoteEntity] row (children/joins persisted separately by the DAO). */
fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    recordingId = recordingId,
    title = title,
    summaryShort = summaryShort,
    summaryLong = summaryLong,
    languageCode = languageCode,
    createdAtEpochMs = createdAt.toEpochMs(),
    updatedAtEpochMs = updatedAt.toEpochMs(),
    pinned = pinned,
    archived = archived,
    userEdited = userEdited,
    durationMs = durationMs,
    speakerCount = speakerCount,
    pipelineState = pipelineState.name,
    pipelineProgress = pipelineProgress,
)
