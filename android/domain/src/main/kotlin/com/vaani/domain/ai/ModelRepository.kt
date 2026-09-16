package com.vaani.domain.ai

import kotlinx.coroutines.flow.Flow

/** Lifecycle of a model on the device (ADR-001 §4). */
enum class InstallStatus { NOT_INSTALLED, QUEUED, DOWNLOADING, PAUSED, EXTRACTING, VERIFYING, INSTALLED, FAILED, UNAVAILABLE }

/**
 * Install state for one catalogue model, observed by the picker UI.
 * [progress] is 0f..1f while downloading; [detail] carries a human error/status.
 */
data class ModelInstallState(
    val modelId: String,
    val status: InstallStatus,
    val progress: Float = 0f,
    val detail: String? = null,
)

/**
 * Manages downloading / verifying / deleting on-device models (ADR-001 §4).
 * Kept in :domain so the picker UI and engines depend on the contract, not the
 * OkHttp installer. Downloads are resumable + SHA-256-verified in the impl;
 * an unpinned model reports [InstallStatus.UNAVAILABLE] (fail-closed).
 */
interface ModelRepository {
    /** Observe install state for every catalogue model. */
    fun observeAll(): Flow<Map<String, ModelInstallState>>

    /** Observe one model's state. */
    fun observe(modelId: String): Flow<ModelInstallState>

    /** Start (or resume) downloading [modelId]. Idempotent while in flight. */
    suspend fun download(modelId: String)

    /** Pause an in-flight download for [modelId], keeping the partial file to resume later. */
    fun pause(modelId: String)

    /** Cancel an in-flight download for [modelId]. */
    fun cancel(modelId: String)

    /** Delete the installed weights for [modelId], freeing storage. */
    suspend fun delete(modelId: String)
}
