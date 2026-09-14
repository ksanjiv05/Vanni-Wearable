package com.vaani.data.database.mapper

import com.vaani.data.database.entity.EntityEdgeWithEntity
import com.vaani.data.database.entity.NoteWithRelations
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
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure-JVM round-trip tests for entity<->domain mappers. No Room/Robolectric:
 * mappers operate on plain data classes, so they run on the JVM directly.
 */
class MapperTest {

    private val t0 = Instant.parse("2026-09-14T09:32:00Z")
    private val t1 = Instant.parse("2026-09-13T15:10:00Z")

    /** A rich note exercising every child collection + every scalar field. */
    private fun richNote() = Note(
        id = "note-standup",
        recordingId = "rec-1",
        title = "Standup with Ravi & Priya",
        summaryShort = "short",
        summaryLong = "long summary text",
        languageCode = "hi-IN",
        createdAt = t0,
        updatedAt = t1,
        pinned = true,
        archived = false,
        userEdited = true,
        tags = listOf(Tag("tag-atlas", "atlas"), Tag("tag-standup", "standup")),
        keyPoints = listOf(
            KeyPoint("kp1", "note-standup", 0, "Atlas moved", "seg1", 252_000),
            KeyPoint("kp2", "note-standup", 1, "Ravi owns doc", null, 468_000),
        ),
        todos = listOf(
            Todo("td1", "note-standup", "Send doc", "by Fri", "Ravi",
                Priority.HIGH, TodoStatus.OPEN, "seg2", 468_000, null),
            Todo("td2", "note-standup", "Spike fix", null, null,
                Priority.LOW, TodoStatus.DONE, null, null, t1),
        ),
        entities = listOf(
            Entity("e1", EntityType.PERSON, "Ravi", 3),
            Entity("e3", EntityType.PRODUCT, "Atlas", 4),
        ),
        durationMs = 860_000,
        speakerCount = 3,
        pipelineState = PipelineState.READY,
        pipelineProgress = null,
    )

    /** Reassembles the aggregate read POJO from the split entity rows. */
    private fun Note.toWithRelations(): NoteWithRelations {
        val row = this.toEntity()
        return NoteWithRelations(
            note = row,
            keyPoints = keyPoints.map { it.toEntity() },
            todos = todos.map { it.toEntity() },
            tags = tags.map { it.toEntity() },
            entityEdges = entities.map {
                EntityEdgeWithEntity(edge = it.toCrossRef(id), entity = it.toEntityRow())
            },
        )
    }

    @Test
    fun note_roundTrips_allFields() {
        val original = richNote()
        val restored = original.toWithRelations().toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun note_withEmptyChildren_roundTrips() {
        val original = richNote().copy(
            tags = emptyList(),
            keyPoints = emptyList(),
            todos = emptyList(),
            entities = emptyList(),
            pipelineState = PipelineState.TRANSCRIBING,
            pipelineProgress = 0.62f,
        )
        val restored = original.toWithRelations().toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun recording_roundTrips_allFields() {
        val original = Recording(
            id = "rec-1", deviceId = "dev-1", sessionUlid = "01J",
            startedAt = t0, tzOffsetMinutes = 330, durationMs = 860_000,
            codec = "opus", sampleRate = 16_000, sha256 = "abc123", bytes = 9_400_000,
            storageUri = null, syncState = SyncState.ACKED, pipelineState = PipelineState.READY,
        )
        assertEquals(original, original.toEntity().toDomain())
    }

    @Test
    fun transcript_roundTrips_withSegments() {
        val segments = listOf(
            TranscriptSegment("seg1", "t1", 0, 248_000, 250_000, "S1", "a", 0.94f),
            TranscriptSegment("seg2", "t1", 1, 260_000, 264_000, "S2", "b", null),
        )
        val original = Transcript(
            id = "t1", recordingId = "rec-1", provider = "sarvam", model = "saaras:v3",
            mode = "codemix", languageCode = "hi-IN", fullText = "a b", createdAt = t0,
            segments = segments,
        )
        val with = TranscriptWithSegments(
            transcript = original.toEntity(),
            segments = original.segments.map { it.toEntity() },
        )
        assertEquals(original, with.toDomain())
    }

    @Test
    fun instant_survives_epochMillis_conversion() {
        assertEquals(t0, t0.toEpochMs().toInstant())
        assertEquals(t0.toEpochMilliseconds(), t0.toEpochMs())
    }

    @Test
    fun enums_survive_name_conversion() {
        for (p in Priority.entries) assertEquals(p, Priority.valueOf(p.name))
        for (s in TodoStatus.entries) assertEquals(s, TodoStatus.valueOf(s.name))
        for (e in EntityType.entries) assertEquals(e, EntityType.valueOf(e.name))
        for (ps in PipelineState.entries) assertEquals(ps, PipelineState.valueOf(ps.name))
        for (ss in SyncState.entries) assertEquals(ss, SyncState.valueOf(ss.name))
    }

    @Test
    fun mentionCount_livesOnTheEdge_notTheSharedRow() {
        val entity = Entity("e1", EntityType.PERSON, "Ravi", 7)
        val edge = EntityEdgeWithEntity(
            edge = entity.toCrossRef("note-x"),
            entity = entity.toEntityRow(),
        )
        assertEquals(7, edge.toDomain().mentionCount)
        assertEquals(entity, edge.toDomain())
    }
}
