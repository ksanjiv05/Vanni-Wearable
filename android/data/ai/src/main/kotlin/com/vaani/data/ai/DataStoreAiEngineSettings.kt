package com.vaani.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AiEnginePrefs
import com.vaani.domain.ai.AiEngineSettings
import com.vaani.domain.ai.AiStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [AiEngineSettings] (ADR-001). Persists the per-stage AI
 * backend choice so a change in Settings re-routes the pipeline on next use.
 * Unknown/absent values fall back to the safe default (SARVAM) rather than
 * crashing — a corrupted pref must never brick transcription.
 */
@Singleton
class DataStoreAiEngineSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AiEngineSettings {

    override fun observe(): Flow<AiEnginePrefs> =
        dataStore.data.map { it.toPrefs() }

    override suspend fun current(): AiEnginePrefs =
        dataStore.data.first().toPrefs()

    override suspend fun setBackend(stage: AiStage, backend: AiBackend) {
        dataStore.edit { prefs ->
            prefs[stage.key] = backend.name
        }
    }

    private fun Preferences.toPrefs() = AiEnginePrefs(
        asr = backendOf(this[AiStage.ASR.key]),
        enrich = backendOf(this[AiStage.ENRICH.key]),
    )

    private fun backendOf(raw: String?): AiBackend =
        raw?.let { runCatching { AiBackend.valueOf(it) }.getOrNull() } ?: DEFAULT_BACKEND

    private val AiStage.key
        get() = when (this) {
            AiStage.ASR -> KEY_ASR
            AiStage.ENRICH -> KEY_ENRICH
        }

    private companion object {
        val DEFAULT_BACKEND = AiBackend.SARVAM
        val KEY_ASR = stringPreferencesKey("ai_backend_asr")
        val KEY_ENRICH = stringPreferencesKey("ai_backend_enrich")
    }
}
