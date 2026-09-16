package com.vaani.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.core.designsystem.component.ChipVariant
import com.vaani.domain.ai.ChatRequest
import com.vaani.domain.ai.EngineRouter
import com.vaani.domain.model.Note
import com.vaani.domain.model.Outcome
import com.vaani.domain.repository.NotesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Chat — real RAG over the user's notes (§6). Retrieves the most relevant notes
 * by keyword match (the same honest matcher Search uses until FTS5+vector lands),
 * builds grounded context blocks, and streams an answer from the on-device LLM
 * enricher via [EngineRouter.enricher] → [chatStream]. Citations point at the
 * real notes that fed the context. No canned/seeded answers.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val notes: NotesRepository,
    private val router: EngineRouter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChange(text: String) = _uiState.update { it.copy(input = text) }

    /** Puts a follow-up suggestion into the composer so the user can send it. */
    fun onFollowUp(text: String) = _uiState.update { it.copy(input = text) }

    fun onSend() {
        val question = _uiState.value.input.trim()
        if (question.isEmpty() || _uiState.value.busy) return
        val n = _uiState.value.messages.size
        val answerId = "a$n"
        _uiState.update {
            it.copy(
                messages = it.messages +
                    ChatMessage.User("u$n", question) +
                    ChatMessage.Answer(answerId, lead = "…", points = emptyList(), citations = emptyList()),
                input = "",
                busy = true,
            )
        }

        viewModelScope.launch {
            val all = runCatching { notes.observeNotes().first() }.getOrDefault(emptyList())
            val ready = all.filter { it.pipelineState == com.vaani.domain.model.PipelineState.READY }
            if (ready.isEmpty()) {
                setAnswer(answerId, "You don't have any notes yet. Record or import audio first, then ask about it.", emptyList())
                return@launch
            }

            val hits = rank(ready, question).take(MAX_CONTEXT_NOTES)
            if (hits.isEmpty()) {
                setAnswer(answerId, "I couldn't find anything about that in your notes.", emptyList())
                return@launch
            }

            val enricher = when (val out = router.enricher()) {
                is Outcome.Ok -> out.value
                is Outcome.Err -> {
                    setAnswer(answerId, "No answering engine is available. Install an on-device LLM (Settings → On-device models) or add a Sarvam API key.", citationsFor(hits))
                    return@launch
                }
            }

            val req = ChatRequest(
                question = question,
                contextBlocks = hits.map { blockFor(it) },
                languageCode = "en",
            )
            val sb = StringBuilder()
            runCatching {
                enricher.chatStream(req).collect { delta ->
                    if (delta.text.isNotEmpty()) {
                        sb.append(delta.text)
                        setAnswer(answerId, sb.toString().trim().ifEmpty { "…" }, citationsFor(hits), keepBusy = !delta.done)
                    }
                    if (delta.done) setAnswer(answerId, sb.toString().trim().ifEmpty { "(no answer)" }, citationsFor(hits))
                }
            }.onFailure {
                setAnswer(answerId, "The on-device model could not answer that. ${it.message ?: ""}".trim(), citationsFor(hits))
            }
        }
    }

    private fun setAnswer(id: String, text: String, citations: List<Citation>, keepBusy: Boolean = false) {
        _uiState.update { state ->
            state.copy(
                busy = keepBusy,
                messages = state.messages.map { m ->
                    if (m is ChatMessage.Answer && m.id == id) m.copy(lead = text, citations = citations) else m
                },
            )
        }
    }

    /** Keyword rank: notes matching the most query terms across title/summary/points/tags. */
    private fun rank(notes: List<Note>, query: String): List<Note> {
        val terms = query.lowercase().split(Regex("\\W+")).filter { it.length > 2 }
        if (terms.isEmpty()) return notes.sortedByDescending { it.updatedAt }
        return notes
            .map { it to score(it, terms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    private fun score(note: Note, terms: List<String>): Int {
        val hay = buildString {
            append(note.title).append(' ').append(note.summaryLong).append(' ')
            note.keyPoints.forEach { append(it.text).append(' ') }
            note.todos.forEach { append(it.text).append(' ') }
            note.tags.forEach { append(it.name).append(' ') }
        }.lowercase()
        return terms.count { hay.contains(it) }
    }

    private fun blockFor(note: Note): String = buildString {
        append("NOTE: ").append(note.title).append('\n')
        append(note.summaryLong.ifBlank { note.summaryShort }).append('\n')
        if (note.keyPoints.isNotEmpty()) {
            append("Key points:\n")
            note.keyPoints.forEach { append("- ").append(it.text).append('\n') }
        }
    }.take(2000)

    private fun citationsFor(notes: List<Note>): List<Citation> =
        notes.mapIndexed { i, note ->
            Citation(
                badge = "C${i + 1}",
                note = note.title.take(40),
                seekLabel = "Open",
                variant = if (i % 2 == 0) ChipVariant.Coffee else ChipVariant.Slate,
                noteId = note.id,
            )
        }

    private companion object {
        const val MAX_CONTEXT_NOTES = 4
    }
}
