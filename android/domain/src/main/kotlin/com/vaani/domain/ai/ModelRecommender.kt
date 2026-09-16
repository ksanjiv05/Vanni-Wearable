package com.vaani.domain.ai

private const val GB = 1024L * 1024 * 1024

/**
 * Device-aware model recommendation (ADR-001 §5.3, §6). Pure functions over a
 * [DeviceSpec] so the picker UI can show a "Recommended for your phone" badge,
 * gray out models that won't run, and explain why — without guessing.
 *
 * A single source of truth: [recommend] picks the best model for a role on a
 * device, and [suitability] marks a model RECOMMENDED iff it IS that pick — so
 * the "Recommended" badge and the per-model fit chip can never disagree.
 */
object ModelRecommender {

    /**
     * Quality order per role, best-first. The recommender walks this list and
     * takes the highest-quality model the device can run comfortably (falling
     * back to merely-runnable). Explicit so ties are deterministic and the
     * ranking is a product decision, not an accident of RAM numbers.
     */
    private val QUALITY_ORDER: Map<ModelRole, List<String>> = mapOf(
        ModelRole.ASR to listOf(
            ModelCatalog.INDIC_CONFORMER.id,   // best Indic accuracy
            ModelCatalog.WHISPER_SMALL.id,     // proven, mid weight
            ModelCatalog.WHISPER_TINY.id,      // light, runnable+downloadable today
        ),
        ModelRole.LLM to listOf(
            ModelCatalog.SARVAM_1_2B.id,       // best Indic notes (no .task yet)
            ModelCatalog.QWEN25_15B.id,        // best DOWNLOADABLE: strong multilingual
            ModelCatalog.GEMMA_3_1B.id,        // gated but high quality
            ModelCatalog.TINYLLAMA_11B.id,     // open English, ungated
            ModelCatalog.QWEN25_05B.id,        // ultra-light for entry-level
        ),
        ModelRole.EMBEDDING to listOf(
            ModelCatalog.E5_SMALL.id,
        ),
    )

    /** Classify the device by RAM (arm64 assumed for the HIGH/MID tiers). */
    fun deviceClass(spec: DeviceSpec): DeviceClass = when {
        !spec.isArm64 -> DeviceClass.LOW
        spec.totalRamBytes >= 6 * GB -> DeviceClass.HIGH
        spec.totalRamBytes >= 4 * GB -> DeviceClass.MID
        else -> DeviceClass.LOW
    }

    /**
     * The single best model to download for [role] on this device, or null if
     * none can run. Prefers, in order: a downloadable (pinned) model the device
     * runs comfortably → any pinned model that runs → the highest-quality
     * comfortable model → any runnable model. Recommending an unpinned "coming
     * soon" model would badge something the user can't actually download, so
     * pinned always wins when one is runnable. Deterministic via [QUALITY_ORDER].
     */
    fun recommend(role: ModelRole, spec: DeviceSpec): ModelInfo? {
        val ordered = (QUALITY_ORDER[role] ?: emptyList()).mapNotNull { ModelCatalog.byId(it) }
        return ordered.firstOrNull { it.pinned && comfortable(it, spec) }
            ?: ordered.firstOrNull { it.pinned && canRun(it, spec) }
            ?: ordered.firstOrNull { comfortable(it, spec) }
            ?: ordered.firstOrNull { canRun(it, spec) }
    }

    /** How well [model] fits [spec], with a short human reason. */
    fun suitability(model: ModelInfo, spec: DeviceSpec): Pair<Suitability, String> {
        if (model.requiresArm64 && !spec.isArm64) {
            return Suitability.UNSUPPORTED to "Needs a 64-bit (arm64) device."
        }
        if (spec.totalRamBytes < model.minRamBytes) {
            return Suitability.UNSUPPORTED to
                "Needs ${model.minRamBytes / GB} GB RAM; this phone has ${ramLabel(spec)}."
        }
        // The recommended pick for this role IS the RECOMMENDED badge — one truth.
        if (recommend(model.role, spec)?.id == model.id) {
            return Suitability.RECOMMENDED to "Best on-device fit for your phone."
        }
        if (spec.totalRamBytes < model.recommendedRamBytes) {
            return Suitability.NOT_RECOMMENDED to
                "Runs, but ${model.recommendedRamBytes / GB} GB is recommended for smooth use."
        }
        return Suitability.SUPPORTED to "Fully supported on your phone."
    }

    private fun canRun(model: ModelInfo, spec: DeviceSpec): Boolean =
        (!model.requiresArm64 || spec.isArm64) && spec.totalRamBytes >= model.minRamBytes

    private fun comfortable(model: ModelInfo, spec: DeviceSpec): Boolean =
        canRun(model, spec) && spec.totalRamBytes >= model.recommendedRamBytes

    private fun ramLabel(spec: DeviceSpec): String {
        val gb = spec.totalRamBytes.toDouble() / GB
        return if (gb >= 1) "%.1f GB".format(gb) else "${spec.totalRamBytes / (1024 * 1024)} MB"
    }
}
