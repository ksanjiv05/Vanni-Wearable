package com.vaani.feature.settings

import com.vaani.domain.ai.AiBackend

/** Immutable UI state for the Settings screen. */
data class SettingsUiState(
    /** True once a real Sarvam API key is stored; drives the key card copy. */
    val apiKeyPresent: Boolean = false,
    val apiKeyMasked: String = "",
    val transcriptionQuality: String = "Standard (on-device)",
    val defaultMode: String = "Auto-detect language",
    val batterySaver: Boolean = true,
    val localOnly: Boolean = false,
    /** Per-stage AI backend choice (ADR-001). Defaults on-device (privacy-first). */
    val asrBackend: AiBackend = AiBackend.LOCAL,
    val enrichBackend: AiBackend = AiBackend.LOCAL,
    /** Human names of the on-device models actually in use (null when none installed / not LOCAL). */
    val asrModelName: String? = null,
    val enrichModelName: String? = null,
)
