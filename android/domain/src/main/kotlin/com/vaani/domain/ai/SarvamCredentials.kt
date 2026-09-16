package com.vaani.domain.ai

/**
 * Supplies the Sarvam API credentials for the API backends (ADR-001 §5.5.1).
 * Kept as a :domain interface so engines depend on the contract, not on the
 * (later) secure key-vault implementation. Returns null when no key is
 * configured — engines then fail with [com.vaani.domain.model.AiError.KeyMissing]
 * rather than attempting an unauthenticated call.
 */
interface SarvamCredentials {
    /** The configured API key, or null if the user hasn't set one. */
    suspend fun apiKey(): String?

    /** Observe whether a (non-blank) key is currently stored. */
    fun hasKey(): kotlinx.coroutines.flow.Flow<Boolean>

    /** Store (or clear, when blank) the API key. */
    suspend fun setApiKey(key: String)
}
