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
import com.vaani.domain.repository.SyncStatus
import kotlinx.datetime.Instant

/**
 * First-run seed fixtures for the Room database — the SAME literal values the
 * old FakeNotesRepository exposed, so the Library/Note/Tasks/Search screens
 * render byte-for-byte identically once seeded. The sync layer isn't built yet,
 * so [syncStatus] is served from an in-memory flow (see RoomNotesRepository).
 */
internal object NotesSeedData {

    private val base: Instant = Instant.parse("2026-09-14T09:32:00Z")
    private val yesterday: Instant = Instant.parse("2026-09-13T15:10:00Z")

    // No real device→phone sync layer yet, so report IDLE (no banner) rather than a
    // fabricated "Syncing 3 recordings · 34%" that never completes on an empty install.
    val syncStatus = SyncStatus(
        isSyncing = false,
        pendingRecordings = 0,
        pendingBytes = 0L,
        progress = 0f,
        transport = "",
        lastSyncedLabel = "",
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

    // ------------------------------------------------------------------------
    // REAL end-to-end result: the Springfield 09-01-2026 Council Meeting audio,
    // transcribed on-device-style with Whisper (base) and enriched by the local
    // Qwen2.5-1.5B GGUF LLM (llama.cpp) — no cloud. Seeded so the actual app UI
    // (Library, Note detail, Tasks, Search) renders a genuinely-extracted note.
    // ------------------------------------------------------------------------
    private val councilSegments: List<TranscriptSegment> = listOf(
        TranscriptSegment("cseg0", "t-council", 0, 2_300, 8_600, "S1",
            "I'm Mayor Patrick Terry and I'm calling the meeting to order.", 0.90f),
        TranscriptSegment("cseg1", "t-council", 1, 11_800, 13_100, "S1",
            "It is the 1st of September of 2026.", 0.88f),
        TranscriptSegment("cseg2", "t-council", 2, 13_100, 22_400, "S1",
            "All council is present: deputy mayor Fule, councillors Kaczynski, Miller and Warren.", 0.87f),
        TranscriptSegment("cseg3", "t-council", 3, 689_000, 711_000, "S1",
            "Departmental reports adopted, unanimous, so carried.", 0.86f),
        TranscriptSegment("cseg4", "t-council", 4, 722_900, 830_000, "S1",
            "First reading given to bylaw 2610, to close a municipal road and sale of land at Lana road; " +
                "it replaces bylaw 2607 that land titles rejected over a company-name mismatch.", 0.84f),
        TranscriptSegment("cseg5", "t-council", 5, 845_700, 872_000, "S1",
            "Unfinished business: Bell MTS tower letter of concurrence, deferred from the August 27th " +
                "planning meeting so Lincrest Airport objectors could review it.", 0.85f),
        TranscriptSegment("cseg6", "t-council", 6, 1_146_500, 1_260_000, "S1",
            "Council moved to defer the Bell MTS letter to another planning meeting so Lincrest Airport " +
                "can be consulted on the 25% height rule.", 0.83f),
    )

    private val council = Note(
        id = "note-council",
        recordingId = "rec-council",
        title = "Springfield Council — Sept 1, 2026",
        summaryShort = "Reports adopted; bylaw 2610 first reading; Bell MTS tower deferred.",
        summaryLong = "Mayor Patrick Terry opened the September 1, 2026 meeting with full council present. " +
            "Departmental reports were adopted unanimously. Bylaw 2610 (closing and selling the Lana road) " +
            "received first reading after land titles rejected bylaw 2607 for a company-name mismatch, " +
            "restarting the public-hearing process. On unfinished business, the Bell MTS tower letter of " +
            "concurrence — deferred from the August 27 planning meeting — was moved to a further planning " +
            "meeting so Lincrest Airport can be consulted about the 25% height rule.",
        languageCode = "en-CA",
        createdAt = base,
        updatedAt = base,
        pinned = true,
        archived = false,
        userEdited = false,
        tags = listOf(
            Tag("tag-council", "council"), Tag("tag-bylaw", "bylaw"),
            Tag("tag-bellmts", "bell-mts"), Tag("tag-zoning", "zoning"),
        ),
        keyPoints = listOf(
            KeyPoint("ckp0", "note-council", 0, "Departmental reports adopted unanimously", "cseg3", 689_000),
            KeyPoint("ckp1", "note-council", 1, "Bylaw 2610 given first reading (closes/sells Lana road); replaces rejected 2607", "cseg4", 722_900),
            KeyPoint("ckp2", "note-council", 2, "Bell MTS tower letter of concurrence deferred for Lincrest Airport review", "cseg5", 845_700),
            KeyPoint("ckp3", "note-council", 3, "Three objectors withdrew on the basis the tower is ≤ 45 metres", null, 1_219_800),
        ),
        todos = listOf(
            Todo("ctd0", "note-council", "Hold public hearing for bylaw 2610, then proceed to second and third reading",
                null, "Administration", Priority.HIGH, TodoStatus.OPEN, "cseg4", 780_700, null),
            Todo("ctd1", "note-council", "Consult Lincrest Airport on the Bell MTS tower 25% height rule",
                "next planning meeting", "CAO", Priority.HIGH, TodoStatus.OPEN, "cseg6", 1_146_500, null),
            Todo("ctd2", "note-council", "Notify the purchasers of the Lana road sale delay",
                null, "Administration", Priority.MEDIUM, TodoStatus.OPEN, "cseg4", 791_600, null),
        ),
        entities = listOf(
            Entity("ce0", EntityType.PERSON, "Mayor Patrick Terry", 2),
            Entity("ce1", EntityType.ORG, "Bell MTS", 3),
            Entity("ce2", EntityType.PLACE, "Lincrest Airport", 3),
        ),
        durationMs = 1_320_000, // 00:22:00 processed window
        speakerCount = 3,
        pipelineState = PipelineState.READY,
    )

    val notes: List<Note> = listOf(council, standup, vendor, designReview)

    /** recordingId -> Transcript for the notes that have one (standup only). */
    val transcripts: Map<String, Transcript> = mapOf(
        council.recordingId to Transcript(
            id = "t-council",
            recordingId = "rec-council",
            provider = "local",
            model = "whisper-base + qwen2.5-1.5b",
            mode = "transcribe",
            languageCode = "en-CA",
            fullText = councilSegments.joinToString(" ") { it.text },
            createdAt = base,
            segments = councilSegments,
        ),
        standup.recordingId to Transcript(
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

    /** recordingId -> Recording. Every seeded note needs a parent recording row
     *  (note.recordingId → recording FK is implicit via the seed order). */
    val recordings: List<Recording> = listOf(
        Recording(
            id = "rec-council", deviceId = "dev-1", sessionUlid = "01J-COUNCIL",
            startedAt = base, tzOffsetMinutes = 330, durationMs = 1_320_000,
            codec = "mp3", sampleRate = 16_000, sha256 = "council2026", bytes = 59_003_297,
            storageUri = null, syncState = SyncState.ACKED, pipelineState = PipelineState.READY,
        ),
        Recording(
            id = "rec-1", deviceId = "dev-1", sessionUlid = "01J-STANDUP",
            startedAt = base, tzOffsetMinutes = 330, durationMs = 860_000,
            codec = "opus", sampleRate = 16_000, sha256 = "abc123", bytes = 9_400_000,
            storageUri = null, syncState = SyncState.ACKED, pipelineState = PipelineState.READY,
        ),
        Recording(
            id = "rec-2", deviceId = "dev-1", sessionUlid = "01J-VENDOR",
            startedAt = base, tzOffsetMinutes = 330, durationMs = 483_000,
            codec = "opus", sampleRate = 16_000, sha256 = "def456", bytes = 5_100_000,
            storageUri = null, syncState = SyncState.PERSISTED, pipelineState = PipelineState.TRANSCRIBING,
        ),
        Recording(
            id = "rec-3", deviceId = "dev-1", sessionUlid = "01J-DESIGN",
            startedAt = yesterday, tzOffsetMinutes = 330, durationMs = 1_361_000,
            codec = "opus", sampleRate = 16_000, sha256 = "ghi789", bytes = 14_800_000,
            storageUri = null, syncState = SyncState.ACKED, pipelineState = PipelineState.READY,
        ),
    )
}
