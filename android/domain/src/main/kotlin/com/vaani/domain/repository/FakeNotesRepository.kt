package com.vaani.domain.repository

import com.vaani.domain.model.ChunkKind
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * In-memory sample data for Milestone A. Matches the mockups in
 * docs/screens/02-library.png and docs/screens/03-note-detail.png so the UI
 * can be built and reviewed before any real data layer exists.
 */
class FakeNotesRepository : NotesRepository {

    private val notesFlow = MutableStateFlow(SampleData.notes)

    override fun observeNotes(): Flow<List<Note>> = notesFlow

    override fun observeNote(id: String): Flow<Note?> =
        notesFlow.map { list -> list.firstOrNull { it.id == id } }

    override fun syncStatus(): Flow<SyncStatus> = MutableStateFlow(SampleData.syncStatus)
}

/** Static fixtures shared across features and previews. */
object SampleData {

    private val base: Instant = Instant.parse("2026-09-14T09:32:00Z")
    private val yesterday: Instant = Instant.parse("2026-09-13T15:10:00Z")

    val syncStatus = SyncStatus(
        isSyncing = true,
        pendingRecordings = 3,
        pendingBytes = 47L * 1024 * 1024,
        progress = 0.34f,
        transport = "Wi-Fi",
        lastSyncedLabel = "Synced 2m",
    )

    val transcriptStandup: List<TranscriptSegment> = listOf(
        TranscriptSegment("seg1", "t1", 0, 248_000, 250_000, "S1",
            "So Atlas ka migration next sprint mein le lete hain.", 0.94f),
        TranscriptSegment("seg2", "t1", 1, 260_000, 264_000, "S2",
            "Theek hai, main doc bana ke Friday tak share karta hoon.", 0.91f),
        TranscriptSegment("seg3", "t1", 2, 276_000, 280_000, "S3",
            "Rate limit ka ek risk hai, I'll spike a fix.", 0.89f),
    )

    private val standup = Note(
        id = "note-standup",
        recordingId = "rec-1",
        title = "Standup with Ravi & Priya",
        summaryShort = "Pushed Atlas to next sprint; Ravi owns migration...",
        summaryLong = "The team agreed to move project Atlas to the next sprint. " +
            "Ravi will own the migration doc and share it by Friday. Priya flagged " +
            "the API rate-limit risk and will spike a fix. Budget signoff still pending.",
        languageCode = "hi-IN",
        createdAt = base,
        updatedAt = base,
        pinned = false,
        archived = false,
        userEdited = false,
        tags = listOf(Tag("tag-atlas", "atlas"), Tag("tag-standup", "standup")),
        keyPoints = listOf(
            KeyPoint("kp1", "note-standup", 0, "Atlas moved to next sprint", "seg1", 252_000),
            KeyPoint("kp2", "note-standup", 1, "Ravi owns migration doc", "seg2", 468_000),
            KeyPoint("kp3", "note-standup", 2, "API rate-limit risk raised", "seg3", 663_000),
        ),
        todos = listOf(
            Todo("td1", "note-standup", "Send migration doc", "by Fri", "Ravi",
                Priority.HIGH, TodoStatus.DONE, "seg2", base),
            Todo("td2", "note-standup", "Spike rate-limit fix", null, "Priya",
                Priority.MEDIUM, TodoStatus.OPEN, "seg3", null),
        ),
        entities = listOf(
            Entity("e1", EntityType.PERSON, "Ravi", 3),
            Entity("e2", EntityType.PERSON, "Priya", 2),
            Entity("e3", EntityType.PRODUCT, "Atlas", 4),
        ),
        durationMs = 860_000, // 00:14:20
        speakerCount = 3,
    )

    private val vendor = Note(
        id = "note-vendor",
        recordingId = "rec-2",
        title = "Call with vendor",
        summaryShort = "Transcribing 8 min of audio...",
        summaryLong = "",
        languageCode = "en-IN",
        createdAt = base,
        updatedAt = base,
        pinned = false,
        archived = false,
        userEdited = false,
        tags = emptyList(),
        keyPoints = emptyList(),
        todos = emptyList(),
        entities = emptyList(),
        durationMs = 483_000, // 00:08:03
        speakerCount = 1,
    )

    private val designReview = Note(
        id = "note-design",
        recordingId = "rec-3",
        title = "Design review",
        summaryShort = "Sharp-corner system approved; cream stays default...",
        summaryLong = "Design system reviewed and approved. Sharp corners locked as the " +
            "defining rule. Cream light theme remains the default.",
        languageCode = "en-IN",
        createdAt = yesterday,
        updatedAt = yesterday,
        pinned = false,
        archived = false,
        userEdited = false,
        tags = listOf(Tag("tag-ui", "ui")),
        keyPoints = listOf(
            KeyPoint("kp4", "note-design", 0, "Sharp corners approved", null, 120_000),
        ),
        todos = listOf(
            Todo("td3", "note-design", "Export tokens", null, null, Priority.LOW, TodoStatus.OPEN, null, null),
            Todo("td4", "note-design", "Ship dark theme", null, null, Priority.MEDIUM, TodoStatus.OPEN, null, null),
            Todo("td5", "note-design", "Icon audit", null, null, Priority.LOW, TodoStatus.DONE, null, yesterday),
            Todo("td6", "note-design", "Type scale review", null, null, Priority.LOW, TodoStatus.OPEN, null, null),
        ),
        entities = emptyList(),
        durationMs = 1_361_000, // 00:22:41
        speakerCount = 2,
    )

    val notes: List<Note> = listOf(standup, vendor, designReview)

    val pipelineByNote: Map<String, PipelineState> = mapOf(
        standup.id to PipelineState.READY,
        vendor.id to PipelineState.TRANSCRIBING,
        designReview.id to PipelineState.READY,
    )

    val transcripts: Map<String, Transcript> = mapOf(
        standup.id to Transcript(
            id = "t1",
            recordingId = "rec-1",
            provider = "sarvam",
            model = "saaras:v3",
            mode = "codemix",
            languageCode = "hi-IN",
            fullText = transcriptStandup.joinToString(" ") { it.text },
            createdAt = base,
            segments = transcriptStandup,
        ),
    )

    val recordings: Map<String, Recording> = mapOf(
        standup.id to Recording(
            id = "rec-1", deviceId = "dev-1", sessionUlid = "01J-STANDUP",
            startedAt = base, tzOffsetMinutes = 330, durationMs = 860_000,
            codec = "opus", sampleRate = 16_000, sha256 = "abc123", bytes = 9_400_000,
            storageUri = null, syncState = SyncState.ACKED, pipelineState = PipelineState.READY,
        ),
    )

    // Silence "unused" warnings for symbols kept for later milestones.
    val chunkKinds = ChunkKind.entries
}
