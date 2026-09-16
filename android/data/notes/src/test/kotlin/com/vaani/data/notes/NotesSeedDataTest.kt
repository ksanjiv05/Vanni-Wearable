package com.vaani.data.notes

import com.vaani.domain.model.PipelineState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards that the seed fixtures still match the exact values the UI was built
 * against (the old FakeNotesData). Pure JVM — no Room needed.
 */
class NotesSeedDataTest {

    @Test
    fun seedFixtures_stillDefined_forOptionalFirstRunSample() {
        // Seeder is now a no-op (app starts empty), but the fixtures remain
        // available for an optional future "load sample" action.
        assertTrue(NotesSeedData.notes.isNotEmpty())
        assertTrue(NotesSeedData.recordings.isNotEmpty())
    }

    @Test
    fun vendor_isTranscribing_atExpectedProgress() {
        val vendor = NotesSeedData.notes.first { it.id == "note-vendor" }
        assertEquals(PipelineState.TRANSCRIBING, vendor.pipelineState)
        assertEquals(0.62f, vendor.pipelineProgress)
    }

    @Test
    fun standup_and_design_areReady() {
        val ready = NotesSeedData.notes.filter { it.id in setOf("note-standup", "note-design") }
        assertTrue(ready.all { it.pipelineState == PipelineState.READY })
        assertTrue(ready.all { it.pipelineProgress == null })
    }

    @Test
    fun syncStatus_isIdle_noFakeBanner() {
        // Sync layer isn't built; status must be idle so no fabricated "Syncing…"
        // banner shows on an empty install.
        val s = NotesSeedData.syncStatus
        assertEquals(false, s.isSyncing)
        assertEquals(0, s.pendingRecordings)
        assertEquals(0L, s.pendingBytes)
        assertEquals(0f, s.progress)
    }

    @Test
    fun standup_hasTranscript_keyedByRecordingId() {
        val standup = NotesSeedData.notes.first { it.id == "note-standup" }
        val t = NotesSeedData.transcripts[standup.recordingId]
        assertEquals("t1", t?.id)
        assertEquals(3, t?.segments?.size)
    }

    @Test
    fun everyNote_hasAParentRecording() {
        val recIds = NotesSeedData.recordings.map { it.id }.toSet()
        assertTrue(NotesSeedData.notes.all { it.recordingId in recIds })
    }
}
