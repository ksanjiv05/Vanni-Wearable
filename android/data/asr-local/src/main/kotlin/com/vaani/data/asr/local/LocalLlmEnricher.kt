package com.vaani.data.asr.local

import android.content.Context
import android.util.Log
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.ChatDelta
import com.vaani.domain.ai.ChatRequest
import com.vaani.domain.ai.DiarizedTranscript
import com.vaani.domain.ai.EnrichOptions
import com.vaani.domain.ai.Enricher
import com.vaani.domain.ai.ExtractedKeyPoint
import com.vaani.domain.ai.ExtractedTodo
import com.vaani.domain.ai.NoteExtraction
import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import com.vaani.domain.model.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device LLM enrichment via the MediaPipe runtime ([MediaPipeLlm]) running a
 * downloaded `.task` model (e.g. Qwen2.5-1.5B). Produces a real, model-generated
 * [NoteExtraction] — proper summary / key points / to-dos, far better than the
 * extractive heuristic — entirely on-device, no network.
 *
 * Graceful by contract: if the model isn't installed or the runtime fails, it
 * delegates to [heuristic] ([LocalHeuristicEnricher]) so a note is NEVER blocked
 * on the LLM. This is the LOCAL enricher the router binds; the heuristic stays a
 * private collaborator, not a second @IntoMap entry.
 *
 * Never fabricates: on a malformed/empty completion it falls back to the
 * heuristic (grounded in the real transcript) rather than inventing fields.
 */
@Singleton
class LocalLlmEnricher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val heuristic: LocalHeuristicEnricher,
    private val settings: com.vaani.domain.ai.AiEngineSettings,
) : Enricher {

    override val id: AiBackend = AiBackend.LOCAL
    override val requiresNetwork: Boolean = false

    private val llm = MediaPipeLlm(context)

    /**
     * Installed .task model file: filesDir/models/<LLM id>/<file>.task. Honours the
     * user's chosen LLM (Settings) when downloaded; otherwise auto-picks the first
     * installed one in [LOCAL_LLM_IDS] quality order.
     */
    private suspend fun installedModel(): File? {
        val root = File(context.filesDir, "models")
        fun taskIn(id: String): File? =
            File(root, id).takeIf { it.isDirectory }
                ?.listFiles()?.firstOrNull { it.name.endsWith(".task") }
                ?.takeIf { llm.isModelPresent(it) }
        // 1) explicit user choice, if that model is downloaded
        settings.current().llmModelId?.let { chosen -> taskIn(chosen)?.let { return it } }
        // 2) auto: first installed in quality order
        return LOCAL_LLM_IDS.firstNotNullOfOrNull { taskIn(it) }
    }

    override suspend fun enrich(
        transcript: DiarizedTranscript,
        opts: EnrichOptions,
    ): Outcome<NoteExtraction> = withContext(Dispatchers.Default) {
        val model = installedModel()
        if (model == null || !llm.nativeLibPresent()) {
            // No local LLM installed → use the always-available extractive path.
            return@withContext heuristic.enrich(transcript, opts)
        }
        val text = transcript.fullText.trim()
        if (text.isEmpty()) {
            return@withContext Outcome.Err(AiError.InferenceFailed(AiBackend.LOCAL, "empty transcript"))
        }

        val prompt = buildPrompt(text, opts.languageCode, model.parentFile?.name.orEmpty())
        val raw = try {
            llm.runOrNull(model.absolutePath, prompt)
        } finally {
            // Release the ~hundreds-of-MB mmap'd engine right after the run instead of
            // holding it for the whole process life (OOM driver on 3-4 GB phones).
            llm.close()
        } ?: return@withContext heuristic.enrich(transcript, opts) // runtime failure → heuristic

        val parsed = parse(raw, transcript)
        // Empty/garbage completion → fall back rather than ship a blank note.
        if (parsed == null || (parsed.keyPoints.isEmpty() && parsed.summaryLong.isBlank())) {
            Log.w(TAG, "LLM output unusable; falling back to heuristic")
            return@withContext heuristic.enrich(transcript, opts)
        }
        Outcome.Ok(parsed)
    }

    override fun chatStream(req: ChatRequest): Flow<ChatDelta> = flow {
        val model = installedModel()
        if (model == null || !llm.nativeLibPresent()) {
            emit(ChatDelta("Install an on-device LLM (Settings → On-device models) or add a Sarvam API key to chat with your notes.", done = false))
            emit(ChatDelta("", done = true)); return@flow
        }
        val context = req.contextBlocks.joinToString("\n\n---\n\n")
        val base = "Answer ONLY from the context. Reply in ${req.languageCode}.\n\nContext:\n$context\n\nQuestion: ${req.question}\nAnswer:"
        // Gemma needs its chat turn template or it returns empty (see buildPrompt).
        val prompt = if (model.parentFile?.name.orEmpty().contains("gemma", ignoreCase = true)) {
            "<start_of_turn>user\n$base<end_of_turn>\n<start_of_turn>model\n"
        } else base
        val answer = withContext(Dispatchers.Default) {
            try { llm.runOrNull(model.absolutePath, prompt) } finally { llm.close() }
        }?.takeIf { it.isNotBlank() } ?: "Sorry, the on-device model could not answer that."
        emit(ChatDelta(answer, done = false))
        emit(ChatDelta("", done = true))
    }

    // --- prompt + parsing ----------------------------------------------------

    private fun buildPrompt(transcript: String, lang: String, modelId: String): String {
        val body = """
        You are a meeting-notes assistant. Read the transcript and return ONLY a
        JSON object (no prose, no markdown fences) with EXACTLY these keys:
        {
          "title": "<=8 word headline",
          "summary": "2-4 sentence summary",
          "key_points": ["point", ...],
          "todos": [{"text":"action item","assignee":null,"due":null,"priority":"LOW|MEDIUM|HIGH"}],
          "tags": ["lowercase-tag", ...]
        }
        Ground every field in the transcript. If a field has no content, use an
        empty array or empty string. Reply in language: $lang.

        TRANSCRIPT:
        ${transcript.take(3500)}

        JSON:
        """.trimIndent()
        // Gemma .task models emit an immediate end-of-turn (empty output) unless the
        // prompt uses Gemma's chat turn template. Other models (Qwen) take raw text.
        return if (modelId.contains("gemma", ignoreCase = true)) {
            "<start_of_turn>user\n$body<end_of_turn>\n<start_of_turn>model\n"
        } else {
            body
        }
    }

    private fun parse(raw: String, transcript: DiarizedTranscript): NoteExtraction? {
        val json = extractJsonObject(raw) ?: return null
        return try {
            val obj = JSONObject(json)
            val keyPoints = obj.optJSONArray("key_points")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    // Gemma sometimes returns objects {"point":"..."} instead of plain
                    // strings — accept both shapes.
                    val s = arr.optJSONObject(i)?.let { o ->
                        o.optString("point").ifBlank { o.optString("text") }
                    } ?: arr.optString(i)
                    s.takeIf { it.isNotBlank() && it != "null" }
                }.map { ExtractedKeyPoint(it, null) }
            }.orEmpty()
            val todos = obj.optJSONArray("todos")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val t = arr.optJSONObject(i) ?: return@mapNotNull null
                    val txt = t.optString("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    ExtractedTodo(
                        text = txt,
                        assignee = t.optString("assignee").takeIf { it.isNotBlank() && it != "null" },
                        dueHint = t.optString("due").takeIf { it.isNotBlank() && it != "null" },
                        priority = when (t.optString("priority").uppercase()) {
                            "HIGH" -> Priority.HIGH; "LOW" -> Priority.LOW; else -> Priority.MEDIUM
                        },
                        sourceStartMs = null,
                    )
                }
            }.orEmpty()
            val tags = obj.optJSONArray("tags")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
            }.orEmpty()
            val summary = obj.optString("summary").trim()
            val title = obj.optString("title").trim()
                .ifBlank { summary.split(Regex("[.!?]")).firstOrNull()?.take(60) ?: "Voice note" }
            NoteExtraction(
                title = title,
                summaryShort = summary.take(160),
                summaryLong = summary.ifBlank { transcript.fullText.take(200) },
                keyPoints = keyPoints,
                todos = todos,
                tags = tags,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "parse failed", t); null
        }
    }

    /** Pull the first balanced {...} object out of a possibly-noisy completion. */
    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        if (start < 0) return null
        var depth = 0
        for (i in start until raw.length) {
            when (raw[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return raw.substring(start, i + 1)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "LocalLlmEnricher"
        val LOCAL_LLM_IDS = listOf(
            "qwen2.5-1.5b-instruct-task",
            "qwen2.5-0.5b-instruct-task",
            "tinyllama-1.1b-chat-task",
            "gemma-3-1b-it-task",
        )
    }
}
