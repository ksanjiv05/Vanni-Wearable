package com.vaani.data.notes

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
import com.vaani.domain.repository.NotesRepository
import com.vaani.domain.repository.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory [NotesRepository] for pre-data-layer milestones. Lives in
 * :data:notes (NOT :domain) and is bound in :app, so features depend only on
 * the interface. The real Room + SQLCipher repository replaces this class with
 * no change to any ViewModel.
 */
@Singleton
class FakeNotesRepository @Inject constructor() : NotesRepository {

    private val notesFlow = MutableStateFlow(FakeNotesData.notes)
    private val syncFlow = MutableStateFlow(FakeNotesData.syncStatus)

    override fun observeNotes(): Flow<List<Note>> = notesFlow

    override fun observeNote(id: String): Flow<Note?> =
        notesFlow.map { list -> list.firstOrNull { it.id == id } }

    override fun observeTranscript(noteId: String): Flow<Transcript?> =
        notesFlow.map { FakeNotesData.transcripts[noteId] }

    override fun syncStatus(): Flow<SyncStatus> = syncFlow
}

/** Static fixtures matching the mockups in docs/screens; internal to :data:notes. */
internal object FakeNotesData {

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

    private val transcriptStandup: List<TranscriptSegment> = listOf(
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
                Priority.HIGH, TodoStatus.OPEN, "seg2", 468_000, null),
            Todo("td2", "note-standup", "Spike rate-limit fix", null, "Priya",
                Priority.MEDIUM, TodoStatus.OPEN, "seg3", 663_000, null),
        ),
        entities = listOf(
            Entity("e1", EntityType.PERSON, "Ravi", 3),
            Entity("e2", EntityType.PERSON, "Priya", 2),
            Entity("e3", EntityType.PRODUCT, "Atlas", 4),
        ),
        durationMs = 860_000, // 00:14:20
        speakerCount = 3,
        pipelineState = PipelineState.READY,
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
        todos = listOf(
            Todo("tdv1", "note-vendor", "Confirm budget with finance", "Mon", null,
                Priority.MEDIUM, TodoStatus.OPEN, null, null, null),
        ),
        entities = emptyList(),
        durationMs = 483_000, // 00:08:03
        speakerCount = 1,
        pipelineState = PipelineState.TRANSCRIBING,
        pipelineProgress = 0.62f,
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
            Todo("td3", "note-design", "Export tokens", null, null, Priority.LOW, TodoStatus.OPEN, null, null, null),
            Todo("td4", "note-design", "Ship dark theme", null, null, Priority.MEDIUM, TodoStatus.OPEN, null, null, null),
            Todo("td5", "note-design", "Icon audit", null, null, Priority.LOW, TodoStatus.DONE, null, null, yesterday),
            Todo("td6", "note-design", "Type scale review", null, null, Priority.LOW, TodoStatus.OPEN, null, null, null),
        ),
        entities = emptyList(),
        durationMs = 1_361_000, // 00:22:41
        speakerCount = 2,
        pipelineState = PipelineState.READY,
    )

    val notes: List<Note> = listOf(standup, vendor, designReview)

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

    @Suppress("unused")
    val recordings: Map<String, Recording> = mapOf(
        standup.id to Recording(
            id = "rec-1", deviceId = "dev-1", sessionUlid = "01J-STANDUP",
            startedAt = base, tzOffsetMinutes = 330, durationMs = 860_000,
            codec = "opus", sampleRate = 16_000, sha256 = "abc123", bytes = 9_400_000,
            storageUri = null, syncState = SyncState.ACKED, pipelineState = PipelineState.READY,
        ),
    )
}
