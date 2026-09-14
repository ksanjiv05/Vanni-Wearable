package com.vaani.domain.ai

import com.vaani.domain.model.AiError
import com.vaani.domain.model.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
        assertEquals(AiBackend.LOCAL, router.asr().id)
        assertEquals(AiBackend.SARVAM, router.enricher().id)
    }

    @Test
    fun fallsBackToSarvamWhenChosenBackendNotRegistered() = runBlocking {
        // User wants LOCAL but only the SARVAM engine is registered (model not installed yet).
        val router = EngineRouter(
            FakeSettings(AiEnginePrefs(asr = AiBackend.LOCAL)),
            asrEngines = mapOf(AiBackend.SARVAM to StubAsr(AiBackend.SARVAM)),
            enrichers = bothEnrich,
        )
        assertEquals(AiBackend.SARVAM, router.asr().id)
    }

    @Test
    fun throwsWhenNoEngineRegisteredAtAll() {
        val router = EngineRouter(
            FakeSettings(AiEnginePrefs()),
            asrEngines = emptyMap(),
            enrichers = bothEnrich,
        )
        assertThrows(IllegalStateException::class.java) { runBlocking { router.asr() } }
    }
}
