package com.vaani.domain.ai

import kotlinx.coroutines.flow.Flow

/**
 * The user's chosen backend per AI stage (ADR-001 §2). Persisted (DataStore)
 * and observable so a change in Settings re-routes the pipeline without a
 * DI-graph rebuild.
 */
data class AiEnginePrefs(
    val asr: AiBackend = AiBackend.SARVAM,
    val enrich: AiBackend = AiBackend.SARVAM,
)

/** Read/observe/update the engine preferences. Implemented over DataStore in :data. */
interface AiEngineSettings {
    fun observe(): Flow<AiEnginePrefs>
    suspend fun current(): AiEnginePrefs
    suspend fun setBackend(stage: AiStage, backend: AiBackend)
}

/**
 * Resolves the concrete engine for a stage from the user's preference.
 *
 * Impls of [AsrEngine]/[Enricher] are provided by the data layer (one LOCAL,
 * one SARVAM each) and registered here by their [AiBackend] id; the router
 * just picks. Keeping this in :domain means the pipeline depends on an
 * interface, never on a specific backend module.
 */
class EngineRouter(
    private val settings: AiEngineSettings,
    private val asrEngines: Map<AiBackend, AsrEngine>,
    private val enrichers: Map<AiBackend, Enricher>,
) {
    suspend fun asr(): AsrEngine =
        asrEngines.resolve(settings.current().asr, AiStage.ASR)

    suspend fun enricher(): Enricher =
        enrichers.resolve(settings.current().enrich, AiStage.ENRICH)

    private fun <T> Map<AiBackend, T>.resolve(want: AiBackend, stage: AiStage): T =
        this[want]                                          // the chosen backend
            ?: this[AiBackend.SARVAM]                       // graceful fallback to API
            ?: values.firstOrNull()                         // any registered engine
            ?: error("No engine registered for stage $stage")
}
