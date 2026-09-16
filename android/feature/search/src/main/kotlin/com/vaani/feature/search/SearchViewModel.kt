package com.vaani.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.core.common.formatClock
import com.vaani.domain.model.Note
import com.vaani.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Search over the user's REAL notes (title, summary, key points, to-dos, tags).
 * Two-stage over the Room-backed notes flow, no sample data:
 *  1. keyword/term match (precise, highlighted snippets), then
 *  2. VECTOR fusion — the query and each note are embedded ([Embedder]) and ranked
 *     by cosine similarity, surfacing related notes that keyword match missed
 *     (typos, morphology, partial words). Reactive to new notes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    repository: NotesRepository,
    private val embedder: com.vaani.domain.ai.Embedder,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Cache per-note field embeddings by note id so we don't re-embed every keystroke.
    private val noteVectors = HashMap<String, List<FloatArray>>()

    val uiState: StateFlow<SearchUiState> =
        combine(repository.observeNotes(), query) { notes, q ->
            buildState(notes, q)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SearchUiState(),
        )

    fun onQueryChange(q: String) { query.value = q }

    fun clearQuery() { query.value = "" }

    fun toggleFilter(label: String) {
        // Filters are derived from real content; toggling is a UI affordance only
        // until faceted search lands. No-op on data to avoid faking a filter.
    }

    private fun buildState(notes: List<Note>, q: String): SearchUiState {
        val trimmed = q.trim()
        val results = if (trimmed.isEmpty()) {
            // Empty query: show the most recent notes as browsable entries.
            notes.take(20).map { it.toSummaryResult() }
        } else {
            search(notes, trimmed)
        }
        return SearchUiState(
            query = q,
            offline = true,
            resultSummary = when {
                notes.isEmpty() -> "No notes yet"
                trimmed.isEmpty() -> "${notes.size} notes"
                else -> "${results.size} result${if (results.size == 1) "" else "s"}"
            },
            filters = emptyList(),
            results = results,
        )
    }

    private fun search(notes: List<Note>, q: String): List<SearchResult> {
        val terms = q.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        fun matches(text: String) = terms.all { text.lowercase().contains(it) }

        val out = mutableListOf<SearchResult>()
        for (note in notes) {
            if (matches(note.title) || matches(note.summaryShort) || matches(note.summaryLong)) {
                out += SearchResult(
                    id = "sum-${note.id}", kindLabel = "SUMMARY", kind = ResultKind.SUMMARY,
                    title = note.title, snippet = snippet(note.summaryLong.ifBlank { note.summaryShort }, terms),
                    score = "", timeLabel = formatClock(0), noteId = note.id,
                )
            }
            note.keyPoints.filter { matches(it.text) }.take(2).forEach { kp ->
                out += SearchResult(
                    id = "kp-${kp.id}", kindLabel = "KEY POINT", kind = ResultKind.KEY_POINT,
                    title = note.title, snippet = snippet(kp.text, terms),
                    score = "", timeLabel = formatClock(kp.sourceStartMs), noteId = note.id,
                )
            }
            note.todos.filter { matches(it.text) }.take(2).forEach { td ->
                out += SearchResult(
                    id = "td-${td.id}", kindLabel = "TO-DO", kind = ResultKind.TODO,
                    title = note.title, snippet = snippet(td.text, terms),
                    score = "", timeLabel = td.sourceStartMs?.let { formatClock(it) } ?: "--:--",
                    noteId = note.id,
                )
            }
            note.tags.filter { matches(it.name) }.take(1).forEach { tag ->
                out += SearchResult(
                    id = "tag-${note.id}-${tag.name}", kindLabel = "TAG", kind = ResultKind.SUMMARY,
                    title = note.title, snippet = "#${tag.name}",
                    score = "", timeLabel = "", noteId = note.id,
                )
            }
        }
        val keywordResults = out.distinctBy { it.id }

        // --- Vector fusion: cosine-similar notes keyword match didn't already surface ---
        val alreadyHit = keywordResults.map { it.noteId }.toSet()
        val qVec = embedder.embed(q)
        val related = notes
            .filterNot { it.id in alreadyHit }
            .map { it to bestSimilarity(qVec, it) }
            .filter { it.second >= VECTOR_MIN_SIMILARITY }
            .sortedByDescending { it.second }
            .take(5)
            .map { (note, sim) ->
                SearchResult(
                    id = "vec-${note.id}", kindLabel = "RELATED", kind = ResultKind.SUMMARY,
                    title = note.title, snippet = note.summaryShort.ifBlank { note.summaryLong }.take(120),
                    score = "${(sim * 100).toInt()}%", timeLabel = "", noteId = note.id,
                )
            }
        return keywordResults + related
    }

    /**
     * Highest cosine of the query against ANY single field of the note (title, each
     * key point, each to-do, tags, summary). Scoring per-field instead of one giant
     * concatenated vector keeps short queries from being diluted by a long note.
     */
    private fun bestSimilarity(qVec: FloatArray, note: Note): Float {
        val fields = fieldsFor(note)
        var best = 0f
        for (f in fields) {
            val sim = com.vaani.domain.ai.Embedder.cosine(qVec, f)
            if (sim > best) best = sim
        }
        return best
    }

    /** Per-field embeddings for a note, cached by id. */
    private fun fieldsFor(note: Note): List<FloatArray> = noteVectors.getOrPut(note.id) {
        buildList {
            add(note.title)
            add(note.summaryLong.ifBlank { note.summaryShort })
            note.keyPoints.forEach { add(it.text) }
            note.todos.forEach { add(it.text) }
            if (note.tags.isNotEmpty()) add(note.tags.joinToString(" ") { it.name })
        }.filter { it.isNotBlank() }.map { embedder.embed(it) }
    }

    private fun Note.toSummaryResult() = SearchResult(
        id = "sum-$id", kindLabel = "NOTE", kind = ResultKind.SUMMARY,
        title = title, snippet = summaryShort.ifBlank { summaryLong }.take(120),
        score = "", timeLabel = formatClock(durationMs), noteId = id,
    )

    /** A window of text around the first matched term, for a readable snippet. */
    private fun snippet(text: String, terms: List<String>): String {
        if (text.isBlank()) return ""
        val lower = text.lowercase()
        val idx = terms.map { lower.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: -1
        if (idx < 0) return text.take(120)
        val start = (idx - 40).coerceAtLeast(0)
        val end = (idx + 80).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return "$prefix${text.substring(start, end).trim()}$suffix"
    }

    private companion object {
        // Cosine threshold for the lexical-vector embedder — high enough to avoid
        // noise, low enough to catch typo/morphology/partial-word matches.
        const val VECTOR_MIN_SIMILARITY = 0.30f
    }
}
