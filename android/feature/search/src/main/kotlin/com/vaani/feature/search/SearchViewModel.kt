package com.vaani.feature.search

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Search — hybrid semantic + keyword results with offline badge and filter
 * chips (screen 05). Milestone A serves a static ranked result set; the real
 * FTS5 + vector fusion lands in :data:vector later. Query edits and chip
 * toggles are live so the screen is interactive.
 */
@HiltViewModel
class SearchViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(seed())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "") }
    }

    fun toggleFilter(label: String) {
        _uiState.update { state ->
            state.copy(
                filters = state.filters.map {
                    if (it.label == label) it.copy(active = !it.active) else it
                },
            )
        }
    }

    private companion object {
        fun seed() = SearchUiState(
            query = "budget decision",
            offline = true,
            resultSummary = "142 results · 38 ms",
            filters = listOf(
                FilterChipState("Last week", active = true),
                FilterChipState("#atlas", active = true),
                FilterChipState("Ravi", active = false),
                FilterChipState("Has to-do", active = false),
                FilterChipState("Summaries", active = false),
            ),
            results = listOf(
                SearchResult(
                    "r1", "SUMMARY", ResultKind.SUMMARY, "Design review",
                    "…budget signoff still pending, will revisit next sprint…", "0.91", "08:14",
                ),
                SearchResult(
                    "r2", "TRANSCRIPT", ResultKind.TRANSCRIPT, "Standup with Ravi",
                    "…the marketing budget decision we deferred to Friday…", "0.88", "11:20",
                ),
                SearchResult(
                    "r3", "KEY POINT", ResultKind.KEY_POINT, "Q3 planning",
                    "Budget cap raised to ₹2L for the pilot phase", "0.83", "03:02",
                ),
                SearchResult(
                    "r4", "TO-DO", ResultKind.TODO, "Vendor call",
                    "Confirm final budget with finance by Monday", "0.79", "--:--",
                ),
            ),
        )
    }
}
