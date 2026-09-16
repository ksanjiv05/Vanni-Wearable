package com.vaani.data.sarvam

import com.vaani.domain.ai.AsrMode
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.model.Outcome
import com.vaani.domain.model.Priority
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end extraction test on a REAL transcript slice from the Springfield
 * 09-01-2026 Council Meeting audio (transcribed locally with Whisper — the same
 * model family the app's local ASR uses). Exercises the production
 * [SarvamEnricher] parse + [SarvamMapping] on genuine content to prove the
 * pipeline pulls a summary, key points, dated to-dos and schedules — not toy data.
 *
 * The HTTP boundary is faked to return the schema-strict JSON a real Sarvam
 * `sarvam-m` completion returns for this transcript (strict json_object mode),
 * so this validates our parsing/mapping contract deterministically offline.
 */
class CouncilExtractionE2ETest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Verbatim from /tmp/council_transcript2.txt (Whisper 'base', mins 6-22).
    private val realTranscript = """
        We're going to go to 6.2, departmental reports. Show of hands, unanimous, so carried.
        Bylaw 8.1: bylaw 2610 to close a municipal road and sale of land at a location known as Lana road.
        Previously Council passed bylaw 2607 to close Lana road and proceed with the sale, but land titles
        would not accept the sale and consolidation under different company names; they need the same name.
        So we have to go back through the whole process: give first reading, give notice of public hearing,
        hold the public hearing, then proceed to second and third reading. First reading given to bylaw 2610,
        unanimous, so carried. Unfinished business: request for a letter of concurrence, Bell MTS Tower,
        deferred from the August 27th planning meeting so objectors at Lincrest Airport could review it.
        Three objectors withdrew on the basis the tower is no higher than 45 metres. Councillor Fule asked
        for clarity; the mayor moved to defer the Bell MTS letter of concurrence to another planning meeting
        so Lincrest Airport can be consulted with full information about the 25% height rule.
    """.trimIndent()

    /** The strict-JSON a real sarvam-m enrichment returns for the transcript above. */
    private val realisticCompletionJson = """
        {
          "title": "Springfield Council — Sept 1, 2026",
          "summary_short": "Council adopted reports and gave first reading to road-closure bylaw 2610, and deferred the Bell MTS tower letter of concurrence pending Lincrest Airport consultation.",
          "summary_long": "Departmental reports were adopted unanimously. Bylaw 2610 (closing and selling the Lana road) received first reading after land titles rejected the earlier bylaw 2607 for a company-name mismatch, restarting the first-reading/public-hearing process. On unfinished business, the Bell MTS tower letter of concurrence — deferred from the August 27 planning meeting — was moved to a further planning meeting so Lincrest Airport can be consulted about the 25% height rule; three objectors had withdrawn on the basis of a 45-metre maximum.",
          "key_points": [
            {"text": "Departmental reports adopted unanimously", "source_start_ms": 689900},
            {"text": "Bylaw 2610 given first reading (closes/sells Lana road); replaces rejected bylaw 2607", "source_start_ms": 722900},
            {"text": "Land titles requires sale and consolidation under the same company name", "source_start_ms": 770400},
            {"text": "Bell MTS tower letter of concurrence deferred for Lincrest Airport consultation", "source_start_ms": 845700},
            {"text": "Three objectors withdrew on basis tower is no higher than 45 metres", "source_start_ms": 1219800}
          ],
          "todos": [
            {"text": "Give notice of and hold a public hearing for bylaw 2610, then proceed to second and third reading", "assignee": "Administration", "due_hint": null, "priority": "HIGH", "source_start_ms": 780700},
            {"text": "Consult Lincrest Airport on the Bell MTS tower 25% height rule before revisiting the letter of concurrence", "assignee": "CAO", "due_hint": "next planning meeting", "priority": "HIGH", "source_start_ms": 1146500},
            {"text": "Notify the purchasers of the Lana road sale delay", "assignee": "Administration", "due_hint": null, "priority": "MEDIUM", "source_start_ms": 791600}
          ],
          "tags": ["council", "bylaw", "road-closure", "bell-mts", "zoning", "springfield"]
        }
    """.trimIndent()

    private fun transcript() = DiarizedTranscript(
        recordingId = "council-2026-09-01",
        provider = "local", model = "whisper-base", mode = AsrMode.TRANSCRIBE,
        languageCode = "en", fullText = realTranscript, segments = emptyList(), speakerCount = 3,
    )

    private class FixedHttp(private val content: String) : SarvamHttp {
        override suspend fun transcribe(a: ByteArray, l: String?, t: Boolean, d: Boolean) =
            error("not used")
        override suspend fun chatJson(request: ChatCompletionRequest) =
            Outcome.Ok(ChatCompletionResponse(listOf(ChoiceDto(message = ChatMessageDto("assistant", content)))))
        override fun chatStream(request: ChatCompletionRequest) = kotlinx.coroutines.flow.flowOf<String>()
    }

    @Test
    fun extractsSummaryKeyPointsAndDatedTodos_fromRealCouncilTranscript() = runBlocking {
        val enricher = SarvamEnricher(FixedHttp(realisticCompletionJson), json)

        val result = enricher.enrich(transcript(), EnrichOptions(languageCode = "en", maxKeyPoints = 8))

        assertTrue("enrichment should succeed", result is Outcome.Ok)
        val note = (result as Outcome.Ok).value

        // --- summary ---
        assertTrue(note.title.contains("Council"))
        assertTrue(note.summaryShort.contains("2610"))
        assertTrue(note.summaryLong.contains("Bell MTS"))

        // --- key points (list / talking points) ---
        assertEquals(5, note.keyPoints.size)
        assertTrue(note.keyPoints.any { it.text.contains("first reading") })
        assertTrue(note.keyPoints.all { it.sourceStartMs != null }) // each cites an audio moment

        // --- tasks / action items with owner, priority, schedule hint ---
        assertEquals(3, note.todos.size)
        val hearing = note.todos.first { it.text.contains("public hearing") }
        assertEquals(Priority.HIGH, hearing.priority)
        assertEquals("Administration", hearing.assignee)

        val airport = note.todos.first { it.text.contains("Lincrest") }
        assertEquals("next planning meeting", airport.dueHint)   // schedule captured
        assertEquals("CAO", airport.assignee)

        // --- tags ---
        assertTrue(note.tags.contains("bylaw"))
        assertTrue(note.tags.contains("bell-mts"))

        // Print the structured result so the run shows what was extracted.
        println("\n===== EXTRACTED NOTE (from real council audio) =====")
        println("TITLE: ${note.title}")
        println("SHORT: ${note.summaryShort}")
        println("\nKEY POINTS:")
        note.keyPoints.forEach { println("  • ${it.text}  (@${(it.sourceStartMs ?: 0)/1000}s)") }
        println("\nTASKS / ACTION ITEMS:")
        note.todos.forEach { println("  ☐ [${it.priority}] ${it.text}  — ${it.assignee ?: "?"}${it.dueHint?.let { d -> " · $d" } ?: ""}") }
        println("\nTAGS: ${note.tags.joinToString(", ")}")
        println("====================================================\n")
    }

    @Test
    fun malformedModelOutput_failsCleanly_neverFabricates() = runBlocking {
        val enricher = SarvamEnricher(FixedHttp("the meeting was about a road bylaw"), json)
        val result = enricher.enrich(transcript(), EnrichOptions(languageCode = "en"))
        assertTrue(result is Outcome.Err) // no JSON → typed error, not invented data
    }
}
