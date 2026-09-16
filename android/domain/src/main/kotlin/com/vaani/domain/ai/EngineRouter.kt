package com.vaani.domain.ai

import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * The user's chosen backend per AI stage (ADR-001 §2). Persisted (DataStore)
 * and observable so a change in Settings re-routes the pipeline without a
 * DI-graph rebuild.
 */
data class AiEnginePrefs(
    // Default on-device (privacy-first; runs with the downloaded model, no key).
    val asr: AiBackend = AiBackend.LOCAL,
    val enrich: AiBackend = AiBackend.LOCAL,
    // Chosen on-device model per role (null = auto-pick best installed). Lets the
    // user say "use Whisper Small" or "use Gemma" when several are downloaded.
    val asrModelId: String? = null,
    val llmModelId: String? = null,
)

/** Read/observe/update the engine preferences. Implemented over DataStore in :data. */
interface AiEngineSettings {
    fun observe(): Flow<AiEnginePrefs>
    suspend fun current(): AiEnginePrefs
    suspend fun setBackend(stage: AiStage, backend: AiBackend)
    /** Pick which downloaded on-device model to use for a role (null = auto). */
    suspend fun setPreferredModel(role: ModelRole, modelId: String?)
}

/**
 * Resolves the concrete engine for a stage from the user's preference.
 *
 * Impls of [AsrEngine]/[Enricher] are provided by the data layer (one LOCAL,
 * one SARVAM each) and registered here by their [AiBackend] id; the router
 * just picks. Keeping this in :domain means the pipeline depends on an
 * interface, never on a specific backend module.
 *
 * Resolution returns an [Outcome] and NEVER throws or silently substitutes a
 * different backend. Quietly using the paid cloud API when the user explicitly
 * chose on-device would break the offline/private promise and spend money
 * without consent (ADR-001 §6). A missing backend is a typed error the
 * pipeline surfaces and the UI can act on (e.g. "download the model, or switch
 * to API"). The previous silent `?: SARVAM ?: values.firstOrNull()` fallback
 * (which also picked a nondeterministic engine from an unordered map) is gone.
 */
class EngineRouter(
    private val settings: AiEngineSettings,
    private val asrEngines: Map<AiBackend, AsrEngine>,
    private val enrichers: Map<AiBackend, Enricher>,
) {
    suspend fun asr(): Outcome<AsrEngine> =
        // ASR is STRICT: honour the exact choice, never auto-cross to another
        // backend. Silently sending mic audio to the paid cloud when the user
        // chose on-device would break the privacy promise (ADR-001 §6).
        asrEngines.resolveStrict(settings.current().asr, AiStage.ASR)

    suspend fun enricher(): Outcome<Enricher> =
        // ENRICH auto-selects: backends are task-specialised (Whisper does ASR,
        // not enrichment), so if the chosen backend has no enricher we fall back
        // along an on-device-first preference rather than failing the note.
        // Enrichment operates on already-transcribed TEXT, so on-device fallback
        // ships nothing new to the network.
        enrichers.resolveAuto(settings.current().enrich, AiStage.ENRICH, ENRICH_PREFERENCE)

    /** Strict resolve: the exact backend or a typed error. No substitution. */
    private fun <T> Map<AiBackend, T>.resolveStrict(want: AiBackend, stage: AiStage): Outcome<T> =
        this[want]?.let { Outcome.Ok(it) } ?: Outcome.Err(errorFor(want, stage))

    /**
     * Auto-selecting resolve (enrichment only): honour the explicit choice when
     * that backend can do the task, else fall back along [preference] of
     * *on-device-first* engines. SARVAM is only ever used when it's already
     * registered AND reached as a fallback target — its own KeyMissing handles
     * the no-key case downstream, so we never make an unauthorised paid call.
     */
    private fun <T> Map<AiBackend, T>.resolveAuto(
        want: AiBackend,
        stage: AiStage,
        preference: List<AiBackend>,
    ): Outcome<T> {
        this[want]?.let { return Outcome.Ok(it) }
        for (backend in preference) this[backend]?.let { return Outcome.Ok(it) }
        return Outcome.Err(errorFor(want, stage))
    }

    private fun errorFor(want: AiBackend, stage: AiStage): AiError = when (want) {
        AiBackend.LOCAL -> AiError.ModelUnavailable(want, "stage:${stage.name}")
        AiBackend.SARVAM -> AiError.Unsupported(want, "no engine registered for stage ${stage.name}")
    }

    private companion object {
        // Enrichment fallback order: on-device first (private, free), API last.
        val ENRICH_PREFERENCE = listOf(AiBackend.LOCAL, AiBackend.SARVAM)
    }
}
