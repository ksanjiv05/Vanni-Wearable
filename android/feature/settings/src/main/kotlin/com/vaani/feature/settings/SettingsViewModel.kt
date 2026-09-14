package com.vaani.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AiEngineSettings
import com.vaani.domain.ai.AiStage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings — API key vault, budget cap + usage, processing toggles, the
 * per-stage AI engine picker (ADR-001), export/delete (screen 08). Engine
 * choice is DataStore-backed via [AiEngineSettings]; other fields are a static
 * snapshot until the real vault/budget layer (§5.6) lands.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val engineSettings: AiEngineSettings,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            engineSettings.observe().collect { prefs ->
                _uiState.update { it.copy(asrBackend = prefs.asr, enrichBackend = prefs.enrich) }
            }
        }
    }

    fun setBatterySaver(on: Boolean) {
        _uiState.update { it.copy(batterySaver = on) }
    }

    fun setLocalOnly(on: Boolean) {
        _uiState.update { it.copy(localOnly = on) }
    }

    fun setAsrBackend(backend: AiBackend) {
        viewModelScope.launch { engineSettings.setBackend(AiStage.ASR, backend) }
    }

    fun setEnrichBackend(backend: AiBackend) {
        viewModelScope.launch { engineSettings.setBackend(AiStage.ENRICH, backend) }
    }
}
