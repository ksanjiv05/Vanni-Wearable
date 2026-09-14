package com.vaani.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Settings — API key vault, budget cap + usage, processing toggles,
 * export/delete (screen 08). Milestone A shows a static snapshot; real
 * DataStore-backed prefs + key vault (§5.6) land later. Toggles are live.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setBatterySaver(on: Boolean) {
        _uiState.update { it.copy(batterySaver = on) }
    }

    fun setLocalOnly(on: Boolean) {
        _uiState.update { it.copy(localOnly = on) }
    }
}
