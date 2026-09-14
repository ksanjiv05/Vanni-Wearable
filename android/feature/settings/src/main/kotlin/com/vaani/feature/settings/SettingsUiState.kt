package com.vaani.feature.settings

import com.vaani.domain.ai.AiBackend

/** Immutable UI state for the Settings screen. */
data class SettingsUiState(
    val apiKeyMasked: String = "sk_live ···· 4c9a · validated",
    val budgetSpentLabel: String = "₹1,204",
    val budgetCapLabel: String = "of ₹1,800 cap",
    val budgetProgress: Float = 0.67f,
    val budgetDetail: String = "67% used · ASR is 86% of spend",
    val transcriptionQuality: String = "Best (batch + diarization)",
    val defaultMode: String = "Codemix · Hinglish",
    val batterySaver: Boolean = true,
    val localOnly: Boolean = false,
    /** Per-stage AI backend choice (ADR-001). */
    val asrBackend: AiBackend = AiBackend.SARVAM,
    val enrichBackend: AiBackend = AiBackend.SARVAM,
)

