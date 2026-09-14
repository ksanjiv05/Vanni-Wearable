package com.vaani.domain.model

import kotlinx.datetime.Instant

/**
 * Pure-Kotlin domain models mirroring the Room schema in ARCHITECTURE.md §4.3.
 * Time fields use kotlinx-datetime [Instant] — java.time is banned in :domain
 * so the module stays portable (KMP / iOS path open).
 */

data class Recording(
    val id: String,
    val deviceId: String,
    val sessionUlid: String,
    val startedAt: Instant,
    val tzOffsetMinutes: Int,
    val durationMs: Long,
    val codec: String,
    val sampleRate: Int,
    val sha256: String,
    val bytes: Long,
    val storageUri: String?,
    val syncState: SyncState,
    val pipelineState: PipelineState,
)

data class Transcript(
    val id: String,
    val recordingId: String,
    val provider: String,
    val model: String,
    val mode: String,
    val languageCode: String,
    val fullText: String,
    val createdAt: Instant,
    val segments: List<TranscriptSegment>,
)

data class TranscriptSegment(
    val id: String,
    val transcriptId: String,
    val idx: Int,
    val startMs: Long,
    val endMs: Long,
    val speakerId: String,
    val text: String,
    val confidence: Float?,
)

data class Note(
    val id: String,
    val recordingId: String,
    val title: String,
    val summaryShort: String,
    val summaryLong: String,
    val languageCode: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val pinned: Boolean,
    val archived: Boolean,
    val userEdited: Boolean,
    val tags: List<Tag>,
    val keyPoints: List<KeyPoint>,
    val todos: List<Todo>,
    val entities: List<Entity>,
    val durationMs: Long,
    val speakerCount: Int,
)

data class KeyPoint(
    val id: String,
    val noteId: String,
    val idx: Int,
    val text: String,
    val sourceSegmentId: String?,
    val sourceStartMs: Long,
)

data class Todo(
    val id: String,
    val noteId: String,
    val text: String,
    val dueHint: String?,
    val assignee: String?,
    val priority: Priority,
    val status: TodoStatus,
    val sourceSegmentId: String?,
    val completedAt: Instant?,
)

data class Tag(
    val id: String,
    val name: String,
)

data class Entity(
    val id: String,
    val type: EntityType,
    val name: String,
    val mentionCount: Int,
)

data class Chunk(
    val id: String,
    val noteId: String,
    val kind: ChunkKind,
    val idx: Int,
    val text: String,
    val lang: String,
    val startMs: Long?,
    val endMs: Long?,
    val tokenCount: Int,
    val embedModelId: String?,
)
