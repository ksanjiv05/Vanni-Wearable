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
    fun seeds_exactlyThreeNotes_inOrder() {
        assertEquals(listOf("note-standup", "note-vendor", "note-design"),
            NotesSeedData.notes.map { it.id })
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
    fun syncStatus_matchesFixture() {
        val s = NotesSeedData.syncStatus
        assertEquals(true, s.isSyncing)
        assertEquals(3, s.pendingRecordings)
        assertEquals(47L * 1024 * 1024, s.pendingBytes)
        assertEquals(0.34f, s.progress)
        assertEquals("Wi-Fi", s.transport)
        assertEquals("Synced 2m", s.lastSyncedLabel)
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
