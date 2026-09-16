package com.vaani.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AiEngineSettings
import com.vaani.domain.ai.AiStage
import com.vaani.domain.ai.InstallStatus
import com.vaani.domain.ai.ModelCatalog
import com.vaani.domain.ai.ModelRepository
import com.vaani.domain.ai.ModelRole
import com.vaani.domain.ai.SarvamCredentials
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings — real per-stage AI engine picker (ADR-001, DataStore-backed) and
 * the real Sarvam API-key state (present/absent from [SarvamCredentials]). No
 * fabricated key/budget values: what the screen shows is the actual stored
 * state. Processing toggles are local until their backends land.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val engineSettings: AiEngineSettings,
    private val credentials: SarvamCredentials,
    private val modelRepository: ModelRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Combine prefs + install states so the Active-engines panel names the
            // REAL on-device model in use per role (chosen if installed, else the
            // first installed in catalog order — mirrors the engines' own pick).
            combine(engineSettings.observe(), modelRepository.observeAll()) { prefs, installs ->
                prefs to installs
            }.collect { (prefs, installs) ->
                _uiState.update {
                    it.copy(
                        asrBackend = prefs.asr,
                        enrichBackend = prefs.enrich,
                        asrModelName = activeModelName(ModelRole.ASR, prefs.asrModelId, installs),
                        enrichModelName = activeModelName(ModelRole.LLM, prefs.llmModelId, installs),
                    )
                }
            }
        }
        viewModelScope.launch {
            credentials.hasKey().collect { present ->
                _uiState.update {
                    it.copy(
                        apiKeyPresent = present,
                        apiKeyMasked = if (present) "•••• configured" else "Not set — tap to add",
                    )
                }
            }
        }
    }

    /** Name of the model actually used for [role]: the chosen one if installed, else first installed. */
    private fun activeModelName(
        role: ModelRole,
        chosenId: String?,
        installs: Map<String, com.vaani.domain.ai.ModelInstallState>,
    ): String? {
        fun installed(id: String) = installs[id]?.status == InstallStatus.INSTALLED
        val models = ModelCatalog.byRole(role)
        val pick = models.firstOrNull { it.id == chosenId && installed(it.id) }
            ?: models.firstOrNull { installed(it.id) }
        return pick?.displayName
    }

    fun setBatterySaver(on: Boolean) = _uiState.update { it.copy(batterySaver = on) }

    fun setLocalOnly(on: Boolean) = _uiState.update { it.copy(localOnly = on) }

    fun setAsrBackend(backend: AiBackend) {
        viewModelScope.launch { engineSettings.setBackend(AiStage.ASR, backend) }
    }

    fun setEnrichBackend(backend: AiBackend) {
        viewModelScope.launch { engineSettings.setBackend(AiStage.ENRICH, backend) }
    }

    /** Persist (or clear, when blank) the Sarvam API key entered in Settings. */
    fun setApiKey(key: String) {
        viewModelScope.launch { credentials.setApiKey(key) }
    }
}
