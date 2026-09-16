package com.vaani.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * Supplies a Hugging Face access token for downloading LICENSE-GATED models
 * (e.g. Google Gemma). Gated repos return HTTP 401/403 unless the request
 * carries `Authorization: Bearer <token>` from an account that has ACCEPTED the
 * model's license on huggingface.co. Kept as a :domain interface so the model
 * downloader depends on the contract, not the storage impl.
 *
 * Stored on-device (never leaves except as the Authorization header to
 * huggingface.co). Returns null when the user hasn't set one — gated downloads
 * then surface an "add token + accept license" prompt instead of failing opaquely.
 */
interface HfCredentials {
    /** The configured HF token, or null if unset. */
    suspend fun token(): String?

    /** Observe whether a (non-blank) token is currently stored. */
    fun hasToken(): Flow<Boolean>

    /** Store (or clear, when blank) the HF token. */
    suspend fun setToken(token: String)
}
