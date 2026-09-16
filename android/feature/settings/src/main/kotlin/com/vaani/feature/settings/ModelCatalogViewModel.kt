package com.vaani.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.domain.ai.DeviceClass
import com.vaani.domain.ai.DeviceSpecProvider
import com.vaani.domain.ai.ModelCatalog
import com.vaani.domain.ai.ModelInfo
import com.vaani.domain.ai.ModelInstallState
import com.vaani.domain.ai.ModelRecommender
import com.vaani.domain.ai.ModelRepository
import com.vaani.domain.ai.ModelRole
import com.vaani.domain.ai.Suitability
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One model row for the catalog UI: static info + live install state + fit for this device. */
data class ModelRowState(
    val model: ModelInfo,
    val install: ModelInstallState,
    val suitability: Suitability,
    val suitabilityReason: String,
    val isRecommended: Boolean,
    /** True when this is the model currently used for its role (explicit choice or auto-pick). */
    val isActive: Boolean = false,
    /** True when the role has >1 installed model, so a "Use this model" choice is meaningful. */
    val selectable: Boolean = false,
)

/** Grouped catalog view + a plain-language device summary line. */
data class ModelCatalogUiState(
    val deviceSummary: String = "",
    val asr: List<ModelRowState> = emptyList(),
    val llm: List<ModelRowState> = emptyList(),
    val embedding: List<ModelRowState> = emptyList(),
)

/** Drives the HF-token dialog; [pendingModelId] is the download to resume after a token is saved (null = manage only). */
data class TokenPrompt(val pendingModelId: String?)

/**
 * Drives the on-device model catalog (ADR-001 §4-5): shows every model, badges
 * the best pick for THIS phone, explains why others are heavier/unsupported,
 * and downloads/deletes with live progress. Recommendation logic is the pure
 * [ModelRecommender]; the device profile comes from [DeviceSpecProvider].
 */
@HiltViewModel
class ModelCatalogViewModel @Inject constructor(
    private val repository: ModelRepository,
    private val hfCredentials: com.vaani.domain.ai.HfCredentials,
    private val engineSettings: com.vaani.domain.ai.AiEngineSettings,
    deviceSpecProvider: DeviceSpecProvider,
) : ViewModel() {

    private val spec = deviceSpecProvider.current()

    /** Whether a Hugging Face token is stored (unlocks gated models like Gemma). */
    val hasHfToken: StateFlow<Boolean> =
        hfCredentials.hasToken()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** True if the catalog has any license-gated models (drives the token banner). */
    val hasGatedModels: Boolean = ModelCatalog.all.any { it.gated }

    /** When non-null, the UI shows the HF-token dialog; carries the model to resume after save (or null for plain "manage"). */
    private val _tokenPrompt = kotlinx.coroutines.flow.MutableStateFlow<TokenPrompt?>(null)
    val tokenPrompt: StateFlow<TokenPrompt?> = _tokenPrompt

    /** Open the token dialog from the banner (no pending download). */
    fun openTokenPrompt() { _tokenPrompt.value = TokenPrompt(pendingModelId = null) }

    fun dismissTokenPrompt() { _tokenPrompt.value = null }

    /** Save the token securely, close the dialog, and resume any download that was waiting on it. */
    fun saveHfToken(token: String) = viewModelScope.launch {
        hfCredentials.setToken(token)
        val pending = _tokenPrompt.value?.pendingModelId
        _tokenPrompt.value = null
        if (!token.isBlank() && pending != null) repository.download(pending)
    }

    private val recommendedIds: Set<String> = setOfNotNull(
        ModelRecommender.recommend(ModelRole.ASR, spec)?.id,
        ModelRecommender.recommend(ModelRole.LLM, spec)?.id,
        ModelRecommender.recommend(ModelRole.EMBEDDING, spec)?.id,
    )

    val uiState: StateFlow<ModelCatalogUiState> =
        kotlinx.coroutines.flow.combine(
            repository.observeAll(),
            engineSettings.observe(),
        ) { installStates, prefs -> buildState(installStates, prefs) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ModelCatalogUiState(deviceSummary = deviceSummary()))

    fun download(modelId: String) = viewModelScope.launch {
        val model = ModelCatalog.byId(modelId)
        // Gated model + no token yet → prompt for the HF token first, then resume.
        if (model?.gated == true && hfCredentials.token().isNullOrBlank()) {
            _tokenPrompt.value = TokenPrompt(pendingModelId = modelId)
        } else {
            repository.download(modelId)
        }
    }
    fun pause(modelId: String) = repository.pause(modelId)
    fun cancel(modelId: String) = repository.cancel(modelId)
    fun delete(modelId: String) = viewModelScope.launch { repository.delete(modelId) }

    /** Make [model] the active on-device model for its role (persists the choice). */
    fun useModel(model: ModelInfo) = viewModelScope.launch {
        engineSettings.setPreferredModel(model.role, model.id)
    }

    private fun buildState(
        installStates: Map<String, ModelInstallState>,
        prefs: com.vaani.domain.ai.AiEnginePrefs,
    ): ModelCatalogUiState {
        fun rows(role: ModelRole): List<ModelRowState> {
            val models = ModelCatalog.byRole(role)
            fun installState(m: ModelInfo) =
                installStates[m.id] ?: ModelInstallState(m.id, com.vaani.domain.ai.InstallStatus.NOT_INSTALLED)
            fun isInstalled(m: ModelInfo) = installState(m).status == com.vaani.domain.ai.InstallStatus.INSTALLED

            val installed = models.filter { isInstalled(it) }
            val selectable = installed.size > 1
            // Which model is actually used for this role: the explicit choice if it's
            // installed, else the first installed in catalog order (mirrors engine auto-pick).
            val chosenId = when (role) {
                ModelRole.ASR -> prefs.asrModelId
                ModelRole.LLM -> prefs.llmModelId
                else -> null
            }
            val activeId = installed.firstOrNull { it.id == chosenId }?.id
                ?: installed.firstOrNull()?.id

            return models.map { model ->
                val (fit, reason) = ModelRecommender.suitability(model, spec)
                ModelRowState(
                    model = model,
                    install = installState(model),
                    suitability = fit,
                    suitabilityReason = reason,
                    isRecommended = model.id in recommendedIds,
                    isActive = isInstalled(model) && model.id == activeId,
                    selectable = selectable,
                )
            }.sortedByDescending { it.isRecommended }
        }

        return ModelCatalogUiState(
            deviceSummary = deviceSummary(),
            asr = rows(ModelRole.ASR),
            llm = rows(ModelRole.LLM),
            embedding = rows(ModelRole.EMBEDDING),
        )
    }

    private fun deviceSummary(): String {
        val gb = spec.totalRamBytes.toDouble() / (1024 * 1024 * 1024)
        val cls = when (ModelRecommender.deviceClass(spec)) {
            DeviceClass.HIGH -> "high-end"
            DeviceClass.MID -> "mid-range"
            DeviceClass.LOW -> "entry-level"
        }
        val abi = if (spec.isArm64) "64-bit" else "32-bit"
        return "Your phone: %.1f GB RAM · %s · %s — tuned picks are marked below.".format(gb, abi, cls)
    }
}
