package com.vaani.feature.chat

import com.vaani.core.designsystem.component.ChipVariant
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Chat — RAG answer with citation chips + follow-up chips (screen 04).
 * Milestone A shows one pre-canned Q&A exchange; real streaming retrieval
 * (§6) lands later. Input edits are live; send echoes the question and a
 * placeholder answer so the flow is exercisable end-to-end.
 */
@HiltViewModel
class ChatViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(seed())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /** Puts a follow-up suggestion into the composer so the user can send it. */
    fun onFollowUp(text: String) {
        _uiState.update { it.copy(input = text) }
    }

    /**
     * Appends the typed question and a placeholder answer. Real retrieval +
     * streaming (§6) replaces the canned answer in a later milestone.
     */
    fun onSend() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return
        _uiState.update { state ->
            val n = state.messages.size
            state.copy(
                messages = state.messages + listOf(
                    ChatMessage.User("u$n", text),
                    ChatMessage.Answer(
                        id = "a$n",
                        lead = "Retrieval over your notes lands in a later milestone — here's a placeholder answer.",
                        points = emptyList(),
                        citations = emptyList(),
                    ),
                ),
                input = "",
            )
        }
    }

    private companion object {
        fun seed() = ChatUiState(
            messages = listOf(
                ChatMessage.User("u1", "What did I commit to in Tuesday's standup?"),
                ChatMessage.Answer(
                    id = "a1",
                    lead = "You committed to two things in Tuesday's standup with Ravi and Priya:",
                    points = listOf(
                        AnswerPoint("Share the Atlas migration doc by Friday", "C1"),
                        AnswerPoint("Spike a fix for the API rate-limit risk", "C2"),
                    ),
                    citations = listOf(
                        Citation("C1", "Standup · Ravi", "▶ 07:48", ChipVariant.Coffee, noteId = "note-standup"),
                        Citation("C2", "Standup · Priya", "▶ 11:03", ChipVariant.Slate, noteId = "note-standup"),
                    ),
                ),
            ),
            followUps = listOf("Who owns the doc?", "What did Priya flag?"),
            input = "",
        )
    }
}
