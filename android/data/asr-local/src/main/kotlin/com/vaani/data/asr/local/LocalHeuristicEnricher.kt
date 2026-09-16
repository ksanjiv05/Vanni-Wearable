package com.vaani.data.asr.local

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
import com.vaani.domain.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * On-device, dependency-free enrichment (ADR-001 fallback path). No LLM, no
 * network: it derives Note fields from the ACTUAL transcript using classic
 * extractive NLP — TF-scored sentence ranking for the summary/key-points, and
 * pattern matching for action items. Every field is grounded in real recognized
 * speech (with real timestamps from the transcript segments); nothing is
 * invented. Registered as [AiBackend.LOCAL] so a fully-offline install still
 * produces summaries, key points and to-dos before a note goes READY.
 *
 * This is deliberately conservative: when the transcript is too short/sparse to
 * support a field, it returns fewer items rather than fabricating plausible ones.
 */
@Singleton
class LocalHeuristicEnricher @Inject constructor() : Enricher {

    override val id: AiBackend = AiBackend.LOCAL
    override val requiresNetwork: Boolean = false

    override suspend fun enrich(
        transcript: DiarizedTranscript,
        opts: EnrichOptions,
    ): Outcome<NoteExtraction> {
        val sentences = splitSentences(transcript)
        if (sentences.isEmpty()) {
            return Outcome.Err(AiError.InferenceFailed(AiBackend.LOCAL, "empty transcript"))
        }

        val tf = termFrequencies(sentences)
        val scored = sentences.map { it to score(it, tf) }

        // Summary: top ~30% of sentences by score (min 1, max 5), restored to
        // chronological order so it reads naturally.
        val summaryCount = min(5, max(1, sentences.size * 3 / 10))
        val summarySentences = scored.sortedByDescending { it.second }
            .take(summaryCount)
            .map { it.first }
            .sortedBy { it.startMs }
        val summaryLong = summarySentences.joinToString(" ") { it.text }.trim()
        val summaryShort = summaryLong.take(160).let { if (summaryLong.length > 160) "$it…" else it }

        // Key points: distinct high-scoring sentences (excluding ones already
        // dominating the summary is unnecessary — overlap is fine and expected).
        val keyPoints = scored.sortedByDescending { it.second }
            .asSequence()
            .map { it.first }
            .distinctBy { it.text.lowercase().take(40) }
            .take(6)
            .sortedBy { it.startMs }
            .map { ExtractedKeyPoint(text = it.text, sourceStartMs = it.startMs) }
            .toList()

        val todos = extractTodos(sentences)
        val tags = extractTags(tf)
        val title = titleFrom(sentences, keyPoints)

        return Outcome.Ok(
            NoteExtraction(
                title = title,
                summaryShort = summaryShort.ifBlank { title },
                summaryLong = summaryLong.ifBlank { summaryShort },
                keyPoints = keyPoints,
                todos = todos,
                tags = tags,
            ),
        )
    }

    /** On-device grounded chat isn't available without an LLM runtime — say so honestly. */
    override fun chatStream(req: ChatRequest): Flow<ChatDelta> = flow {
        emit(
            ChatDelta(
                "On-device chat needs a local LLM model or a Sarvam API key. " +
                    "Add one in Settings to ask questions about your notes.",
                done = false,
            ),
        )
        emit(ChatDelta("", done = true))
    }

    // --- sentence model -----------------------------------------------------

    private data class Sentence(val text: String, val startMs: Long)

    /** Split the transcript into sentences, carrying each one's start timestamp. */
    private fun splitSentences(t: DiarizedTranscript): List<Sentence> {
        // Prefer per-segment splitting so timestamps stay accurate.
        val out = mutableListOf<Sentence>()
        val segs = t.segments.ifEmpty {
            listOf(TranscriptSegment("s", "t", 0, 0, 0, "S1", t.fullText, null))
        }
        for (seg in segs) {
            val parts = seg.text.split(Regex("(?<=[.!?।])\\s+"))
            for (p in parts) {
                val clean = p.trim()
                if (clean.length >= 12 && clean.split(Regex("\\s+")).size >= 3) {
                    out += Sentence(clean, seg.startMs)
                }
            }
        }
        return dedupeAdjacent(out)
    }

    /** Collapse immediately-repeated sentences (small-model looping artefacts). */
    private fun dedupeAdjacent(list: List<Sentence>): List<Sentence> {
        val out = mutableListOf<Sentence>()
        var last = ""
        for (s in list) {
            val cleaned = s.copy(text = collapseRepeats(s.text))
            val key = cleaned.text.lowercase().replace(Regex("[^a-z0-9 ]"), "").trim()
            if (key != last && key.isNotBlank()) out += cleaned
            last = key
        }
        return out
    }

    /**
     * Collapse consecutive repeated word runs that small ASR models emit when the
     * decoder loops (e.g. "the nation of the country the nation of the country" →
     * "the nation of the country"). Scans phrase lengths 2..6; keeps the first
     * occurrence, drops immediate repeats. Purely cleans real output — adds nothing.
     */
    private fun collapseRepeats(text: String): String {
        var words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        for (n in 6 downTo 2) {
            val out = mutableListOf<String>()
            var i = 0
            while (i < words.size) {
                val phrase = if (i + n <= words.size) words.subList(i, i + n) else null
                val next = if (i + 2 * n <= words.size) words.subList(i + n, i + 2 * n) else null
                if (phrase != null && phrase.map { it.lowercase() } == next?.map { it.lowercase() }) {
                    // Emit the phrase once, then skip every immediate repeat of it.
                    out += phrase
                    i += n
                    while (i + n <= words.size &&
                        words.subList(i, i + n).map { it.lowercase() } == phrase.map { it.lowercase() }
                    ) i += n
                } else {
                    out += words[i]; i++
                }
            }
            words = out
        }
        // Collapse single-word stutters ("the the the" → "the").
        val final = mutableListOf<String>()
        for (w in words) if (final.lastOrNull()?.lowercase() != w.lowercase()) final += w
        return final.joinToString(" ")
    }

    // --- scoring ------------------------------------------------------------

    private fun termFrequencies(sentences: List<Sentence>): Map<String, Int> {
        val freq = HashMap<String, Int>()
        for (s in sentences) {
            for (w in words(s.text)) {
                if (w.length >= 3 && w !in STOPWORDS) freq[w] = (freq[w] ?: 0) + 1
            }
        }
        return freq
    }

    private fun score(s: Sentence, tf: Map<String, Int>): Double {
        val ws = words(s.text).filter { it.length >= 3 && it !in STOPWORDS }
        if (ws.isEmpty()) return 0.0
        val tfScore = ws.sumOf { (tf[it] ?: 0).toDouble() } / ws.size
        // Mild length preference (10–30 words is the sweet spot for a key line).
        val n = s.text.split(Regex("\\s+")).size
        val lengthFactor = when { n < 5 -> 0.6; n > 40 -> 0.7; else -> 1.0 }
        return tfScore * lengthFactor
    }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }

    // --- action-item extraction --------------------------------------------

    private fun extractTodos(sentences: List<Sentence>): List<ExtractedTodo> {
        val todos = mutableListOf<ExtractedTodo>()
        for (s in sentences) {
            val lower = s.text.lowercase()
            val isAction = ACTION_CUES.any { lower.contains(it) }
            if (!isAction) continue
            val due = DUE_PATTERNS.firstNotNullOfOrNull { rx ->
                rx.find(s.text)?.value?.trim()
            }
            val assignee = ASSIGNEE_RX.find(s.text)?.groupValues?.getOrNull(1)
                ?.takeIf { it.isNotBlank() && it !in COMMON_CAPS }
            val priority = when {
                URGENT_CUES.any { lower.contains(it) } -> Priority.HIGH
                lower.contains("should") || lower.contains("could") -> Priority.LOW
                else -> Priority.MEDIUM
            }
            todos += ExtractedTodo(
                text = s.text.take(160),
                assignee = assignee,
                dueHint = due,
                priority = priority,
                sourceStartMs = s.startMs,
            )
            if (todos.size >= 8) break
        }
        return todos.distinctBy { it.text.lowercase().take(50) }
    }

    private fun extractTags(tf: Map<String, Int>): List<String> =
        tf.entries.asSequence()
            .filter { it.value >= 2 && it.key.length >= 4 }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(6)
            .toList()

    private fun titleFrom(sentences: List<Sentence>, keyPoints: List<ExtractedKeyPoint>): String {
        val first = sentences.firstOrNull()?.text.orEmpty()
        // Use the earliest substantive sentence, trimmed to a headline length.
        val base = keyPoints.minByOrNull { it.sourceStartMs ?: 0 }?.text ?: first
        return base.split(Regex("[.!?।]")).firstOrNull()?.trim()?.take(60)
            ?.ifBlank { "Voice note" } ?: "Voice note"
    }

    private companion object {
        val ACTION_CUES = listOf(
            "need to", "needs to", "have to", "has to", "will ", "we'll", "i'll",
            "should ", "must ", "let's", "let us", "action item", "follow up",
            "follow-up", "make sure", "please ", "to-do", "todo", "assign",
            "responsible for", "by next", "deadline", "due ", "schedule",
        )
        val URGENT_CUES = listOf("urgent", "asap", "immediately", "critical", "today", "right away")
        val DUE_PATTERNS = listOf(
            Regex("\\bby\\s+(?:next\\s+)?(?:mon|tues|wednes|thurs|fri|satur|sun)day\\b", RegexOption.IGNORE_CASE),
            Regex("\\bby\\s+(?:tomorrow|today|tonight|end of (?:day|week|month)|next (?:week|month|meeting))\\b", RegexOption.IGNORE_CASE),
            Regex("\\bnext\\s+(?:week|month|meeting|sprint)\\b", RegexOption.IGNORE_CASE),
            Regex("\\b(?:tomorrow|tonight)\\b", RegexOption.IGNORE_CASE),
        )
        // "Ravi will…", "Priya to…", "ask Amit to…"
        val ASSIGNEE_RX = Regex("\\b([A-Z][a-z]{2,})\\s+(?:will|to|should|needs|is going to)\\b")
        val COMMON_CAPS = setOf("The", "This", "That", "We", "They", "It", "There", "Yes", "No", "So", "And", "But", "Now", "Then", "Thank")
        val STOPWORDS = setOf(
            "the", "and", "for", "are", "but", "not", "you", "your", "with", "this", "that",
            "have", "has", "had", "was", "were", "will", "would", "should", "could", "can",
            "they", "them", "there", "here", "what", "when", "where", "which", "who", "how",
            "about", "into", "than", "then", "some", "such", "also", "just", "like", "get",
            "got", "our", "out", "all", "any", "been", "being", "from", "very", "much",
            "yeah", "okay", "well", "going", "gonna", "know", "think", "right", "kind",
            "thing", "things", "actually", "really", "one", "two", "now", "yes",
        )
    }
}
