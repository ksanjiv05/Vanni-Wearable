package com.vaani.domain.ai

import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineRouterTest {

    private class FakeSettings(private var prefs: AiEnginePrefs) : AiEngineSettings {
        override fun observe(): Flow<AiEnginePrefs> = flowOf(prefs)
        override suspend fun current(): AiEnginePrefs = prefs
        override suspend fun setBackend(stage: AiStage, backend: AiBackend) {
            prefs = when (stage) {
                AiStage.ASR -> prefs.copy(asr = backend)
                AiStage.ENRICH -> prefs.copy(enrich = backend)
            }
        }
        override suspend fun setPreferredModel(role: com.vaani.domain.ai.ModelRole, modelId: String?) {
            prefs = when (role) {
                com.vaani.domain.ai.ModelRole.ASR -> prefs.copy(asrModelId = modelId)
                com.vaani.domain.ai.ModelRole.LLM -> prefs.copy(llmModelId = modelId)
                else -> prefs
            }
        }
    }

    private class StubAsr(override val id: AiBackend) : AsrEngine {
        override fun capabilities() = AsrCapabilities(
            backend = id, supportsDiarization = false, supportsCodemix = false,
            supportsTranslate = false, maxDurationMs = 0, languages = emptySet(),
            requiresNetwork = id == AiBackend.SARVAM,
        )
        override suspend fun transcribe(
            audio: AudioRef, opts: AsrOptions, onProgress: (AsrProgress) -> Unit,
        ): Outcome<DiarizedTranscript> = Outcome.Err(AiError.Unsupported(id, "stub"))
    }

    private class StubEnricher(override val id: AiBackend) : Enricher {
        override val requiresNetwork = id == AiBackend.SARVAM
        override suspend fun enrich(transcript: DiarizedTranscript, opts: EnrichOptions) =
            Outcome.Err(AiError.Unsupported(id, "stub"))
        override fun chatStream(req: ChatRequest): Flow<ChatDelta> = flowOf()
    }

    private val bothAsr = mapOf(
        AiBackend.LOCAL to StubAsr(AiBackend.LOCAL),
        AiBackend.SARVAM to StubAsr(AiBackend.SARVAM),
    )
    private val bothEnrich = mapOf(
        AiBackend.LOCAL to StubEnricher(AiBackend.LOCAL),
        AiBackend.SARVAM to StubEnricher(AiBackend.SARVAM),
    )

    @Test
    fun routesToChosenBackendPerStage() = runBlocking {
        val router = EngineRouter(
            FakeSettings(AiEnginePrefs(asr = AiBackend.LOCAL, enrich = AiBackend.SARVAM)),
            bothAsr, bothEnrich,
        )
        assertEquals(AiBackend.LOCAL, (router.asr() as Outcome.Ok).value.id)
        assertEquals(AiBackend.SARVAM, (router.enricher() as Outcome.Ok).value.id)
    }

    @Test
    fun localChosenButNotRegistered_returnsModelUnavailable_neverSilentlyApi() = runBlocking {
        // User explicitly wants LOCAL but only the SARVAM engine is registered
        // (model not installed yet). The router must NOT quietly hand back the
        // paid cloud engine — that would ship audio to the network without
        // consent. It returns a typed ModelUnavailable the UI can act on.
        val router = EngineRouter(
            FakeSettings(AiEnginePrefs(asr = AiBackend.LOCAL)),
            asrEngines = mapOf(AiBackend.SARVAM to StubAsr(AiBackend.SARVAM)),
            enrichers = bothEnrich,
        )
        val result = router.asr()
        assertTrue(result is Outcome.Err)
        val err = (result as Outcome.Err).error
        assertTrue(err is AiError.ModelUnavailable)
        assertEquals(AiBackend.LOCAL, (err as AiError.ModelUnavailable).backend)
    }

    @Test
    fun apiChosenButNoAdapterRegistered_returnsUnsupported() = runBlocking {
        // SARVAM chosen but its adapter module isn't on the classpath yet.
        val router = EngineRouter(
            FakeSettings(AiEnginePrefs(asr = AiBackend.SARVAM)),
            asrEngines = emptyMap(),
            enrichers = bothEnrich,
        )
        val result = router.asr()
        assertTrue(result is Outcome.Err)
        assertTrue((result as Outcome.Err).error is AiError.Unsupported)
    }

    @Test
    fun enrichAutoSelects_whenChosenBackendHasNoEnricher() = runBlocking {
        // User's ENRICH choice is LOCAL but only a SARVAM enricher happens to be
        // registered (or vice-versa): enrichment is task-specialised, so the
        // router auto-selects an available enricher rather than failing the note.
        // (Enrichment works on already-transcribed text — no new audio leaves.)
        val router = EngineRouter(
            FakeSettings(AiEnginePrefs(enrich = AiBackend.LOCAL)),
            asrEngines = bothAsr,
            enrichers = mapOf(AiBackend.SARVAM to StubEnricher(AiBackend.SARVAM)),
        )
        val result = router.enricher()
        assertTrue(result is Outcome.Ok)
        assertEquals(AiBackend.SARVAM, (result as Outcome.Ok).value.id)
    }

    @Test
    fun returnsErrWhenNoEngineRegisteredAtAll() = runBlocking {
        val router = EngineRouter(
            FakeSettings(AiEnginePrefs()),
            asrEngines = emptyMap(),
            enrichers = bothEnrich,
        )
        assertTrue(router.asr() is Outcome.Err)
    }
}
