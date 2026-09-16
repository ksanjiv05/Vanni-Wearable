package com.vaani.feature.search

/** Immutable UI state for the Search screen. */
data class SearchUiState(
    val query: String = "",
    val offline: Boolean = true,
    val resultSummary: String = "",
    val filters: List<FilterChipState> = emptyList(),
    val results: List<SearchResult> = emptyList(),
)

data class FilterChipState(
    val label: String,
    val active: Boolean,
)

data class SearchResult(
    val id: String,
    val kindLabel: String,
    val kind: ResultKind,
    val title: String,
    val snippet: String,
    val score: String,
    val timeLabel: String,
    val noteId: String = "",
)

enum class ResultKind { SUMMARY, TRANSCRIPT, KEY_POINT, TODO }
