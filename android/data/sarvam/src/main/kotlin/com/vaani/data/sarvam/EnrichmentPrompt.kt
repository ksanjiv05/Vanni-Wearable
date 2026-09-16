package com.vaani.data.sarvam

/**
 * Builds the enrichment prompt (ADR-001 §5.5.4). The model must return a strict
 * JSON object matching [ExtractionDto]; the schema is described in-prompt and
 * enforced by the API's json_object response format. Kept pure/testable.
 */
internal object EnrichmentPrompt {

    const val MODEL = "sarvam-m"

    fun system(languageCode: String): String = """
        You extract structured notes from a meeting/voice transcript.
        Reply with ONE JSON object, no prose, matching exactly:
        {
          "title": string,
          "summary_short": string,
          "summary_long": string,
          "key_points": [{"text": string, "source_start_ms": number|null}],
          "todos": [{"text": string, "assignee": string|null, "due_hint": string|null,
                     "priority": "LOW"|"MEDIUM"|"HIGH", "source_start_ms": number|null}],
          "tags": [string]
        }
        Write summaries and key points in the transcript's language ($languageCode).
        Extract only to-dos that are genuinely actionable. Never invent facts.
    """.trimIndent()

    fun user(transcript: String): String = "Transcript:\n$transcript"
}
