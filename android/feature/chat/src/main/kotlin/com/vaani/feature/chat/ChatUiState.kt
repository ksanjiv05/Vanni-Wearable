package com.vaani.feature.chat

import com.vaani.core.designsystem.component.ChipVariant

/** Immutable UI state for the Chat screen. */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val followUps: List<String> = emptyList(),
    val input: String = "",
)

sealed interface ChatMessage {
    val id: String

    data class User(override val id: String, val text: String) : ChatMessage

    data class Answer(
        override val id: String,
        val lead: String,
        val points: List<AnswerPoint>,
        val citations: List<Citation>,
    ) : ChatMessage
}

data class AnswerPoint(val text: String, val citation: String)

data class Citation(
    val badge: String,
    val note: String,
    val seekLabel: String,
    val variant: ChipVariant,
    val noteId: String = "",
)
