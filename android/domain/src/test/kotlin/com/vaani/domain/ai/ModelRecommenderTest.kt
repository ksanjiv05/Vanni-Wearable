package com.vaani.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRecommenderTest {

    private val gb = 1024L * 1024 * 1024
    private fun arm64(ramGb: Long) = DeviceSpec(listOf("arm64-v8a", "armeabi-v7a"), ramGb * gb)
    private fun old32(ramGb: Long) = DeviceSpec(listOf("armeabi-v7a"), ramGb * gb)

    @Test
    fun classifiesByRamAndAbi() {
        assertEquals(DeviceClass.HIGH, ModelRecommender.deviceClass(arm64(8)))
        assertEquals(DeviceClass.MID, ModelRecommender.deviceClass(arm64(4)))
        assertEquals(DeviceClass.LOW, ModelRecommender.deviceClass(arm64(2)))
        assertEquals(DeviceClass.LOW, ModelRecommender.deviceClass(old32(8))) // no arm64
    }

    @Test
    fun highEndPhone_getsBestDownloadableModels_asRecommended() {
        val spec = arm64(8)
        // LLM: Sarvam-2B is the top quality tier but has no .task build (unpinned),
        // so the recommender picks the best DOWNLOADABLE comfortable model —
        // Qwen 1.5B. ASR likewise prefers pinned Whisper Small over unpinned
        // IndicConformer. Recommending a model you can't download is useless.
        assertEquals(ModelCatalog.QWEN25_15B.id, ModelRecommender.recommend(ModelRole.LLM, spec)?.id)
        assertEquals(ModelCatalog.WHISPER_SMALL.id, ModelRecommender.recommend(ModelRole.ASR, spec)?.id)
    }

    @Test
    fun midPhone_getsTinyLlama_asBestComfortablePinnedLlm() {
        val spec = arm64(4)
        // 4 GB: Qwen-1.5B wants 6 GB to be comfortable (only *runs* here). Among
        // pinned models comfortable at 4 GB, TinyLlama-1.1B ranks above Qwen-0.5B
        // in the quality order → it's the pick (prefer comfortable over bigger-but-hot).
        assertEquals(ModelCatalog.TINYLLAMA_11B.id, ModelRecommender.recommend(ModelRole.LLM, spec)?.id)
    }

    @Test
    fun entryPhone_getsTinyLlama_asBestRunnablePinnedLlm() {
        val spec = arm64(3)
        // 3 GB: nothing is comfortable (all rec 4 GB+); best pinned model that at
        // least RUNS (min 3 GB) and ranks highest is TinyLlama-1.1B.
        assertEquals(ModelCatalog.TINYLLAMA_11B.id, ModelRecommender.recommend(ModelRole.LLM, spec)?.id)
    }

    @Test
    fun qwen_modelsAreApache2_andPresentInCatalog() {
        val llm = ModelCatalog.byRole(ModelRole.LLM).map { it.id }
        assertTrue(llm.contains(ModelCatalog.QWEN25_05B.id))
        assertTrue(llm.contains(ModelCatalog.QWEN25_15B.id))
        assertEquals("Apache-2.0", ModelCatalog.QWEN25_05B.license)
        assertEquals("Apache-2.0", ModelCatalog.QWEN25_15B.license)
    }

    @Test
    fun recommendedBadge_matchesRecommendPick_exactly() {
        // The RECOMMENDED suitability must be the model recommend() returns —
        // never two different models both looking "best".
        val spec = arm64(7)
        val pick = ModelRecommender.recommend(ModelRole.LLM, spec)!!
        for (m in ModelCatalog.byRole(ModelRole.LLM)) {
            val (fit, _) = ModelRecommender.suitability(m, spec)
            assertEquals(m.id == pick.id, fit == Suitability.RECOMMENDED)
        }
    }

    @Test
    fun sarvam2b_isUnsupported_on4gb() {
        val (s, reason) = ModelRecommender.suitability(ModelCatalog.SARVAM_1_2B, arm64(4))
        assertEquals(Suitability.UNSUPPORTED, s)
        assertTrue(reason.contains("6 GB"))
    }

    @Test
    fun sarvam2b_isSupported_notRecommended_on8gb_sinceUnpinned() {
        // Sarvam-2B fits an 8 GB phone comfortably, but it has no downloadable
        // .task build (unpinned) so the pick is Qwen-1.5B — Sarvam is SUPPORTED,
        // not the RECOMMENDED badge. (Badge always tracks recommend()'s pick.)
        val (s, _) = ModelRecommender.suitability(ModelCatalog.SARVAM_1_2B, arm64(8))
        assertEquals(Suitability.SUPPORTED, s)
    }

    @Test
    fun heavierLlm_isNotRecommended_whenALighterOneIsThePick() {
        // On 4 GB, Gemma runs + is comfortable but Qwen-1.5B is the pick, so
        // Gemma is SUPPORTED (comfortable) — not RECOMMENDED, not "heavy".
        val (s, _) = ModelRecommender.suitability(ModelCatalog.GEMMA_3_1B, arm64(4))
        assertEquals(Suitability.SUPPORTED, s)
    }

    @Test
    fun whisperTiny_isRecommended_on3gb_asBestComfortableAsr() {
        // 3 GB: Whisper Tiny (rec 3 GB) is comfortable; Small/Conformer want 4+ GB.
        val (s, _) = ModelRecommender.suitability(ModelCatalog.WHISPER_TINY, arm64(3))
        assertEquals(Suitability.RECOMMENDED, s)
        assertEquals(ModelCatalog.WHISPER_TINY.id, ModelRecommender.recommend(ModelRole.ASR, arm64(3))?.id)
    }

    @Test
    fun everything_unsupported_on32bitDevice_forArm64Models() {
        val (s, reason) = ModelRecommender.suitability(ModelCatalog.WHISPER_SMALL, old32(8))
        assertEquals(Suitability.UNSUPPORTED, s)
        assertTrue(reason.contains("arm64"))
        assertEquals(null, ModelRecommender.recommend(ModelRole.ASR, old32(8)))
    }

    @Test
    fun embeddingModel_runsEvenWithoutArm64() {
        // E5 doesn't require arm64; a 3 GB 32-bit device can still embed.
        assertEquals(ModelCatalog.E5_SMALL.id, ModelRecommender.recommend(ModelRole.EMBEDDING, old32(3))?.id)
    }
}
