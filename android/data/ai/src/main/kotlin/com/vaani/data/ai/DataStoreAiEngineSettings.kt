package com.vaani.data.ai

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AiEnginePrefs
import com.vaani.domain.ai.AiEngineSettings
import com.vaani.domain.ai.AiStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    @AiPreferences private val dataStore: DataStore<Preferences>,
) : AiEngineSettings {

    override fun observe(): Flow<AiEnginePrefs> =
        dataStore.data
            // A read error (I/O, migration) must not crash the collector — emit
            // safe defaults so transcription keeps working.
            .catch { emit(emptyPreferences()) }
            .map { it.toPrefs() }

    override suspend fun current(): AiEnginePrefs =
        runCatching { dataStore.data.first().toPrefs() }.getOrDefault(AiEnginePrefs())

    override suspend fun setBackend(stage: AiStage, backend: AiBackend) {
        dataStore.edit { prefs ->
            prefs[stage.key] = backend.name
        }
    }

    override suspend fun setPreferredModel(role: com.vaani.domain.ai.ModelRole, modelId: String?) {
        dataStore.edit { prefs ->
            val key = role.modelKey ?: return@edit
            if (modelId.isNullOrBlank()) prefs.remove(key) else prefs[key] = modelId
        }
    }

    private fun Preferences.toPrefs() = AiEnginePrefs(
        asr = backendOf(this[AiStage.ASR.key]),
        enrich = backendOf(this[AiStage.ENRICH.key]),
        asrModelId = this[KEY_ASR_MODEL]?.takeIf { it.isNotBlank() },
        llmModelId = this[KEY_LLM_MODEL]?.takeIf { it.isNotBlank() },
    )

    private fun backendOf(raw: String?): AiBackend =
        raw?.let { runCatching { AiBackend.valueOf(it) }.getOrNull() } ?: DEFAULT_BACKEND

    private val AiStage.key
        get() = when (this) {
            AiStage.ASR -> KEY_ASR
            AiStage.ENRICH -> KEY_ENRICH
        }

    private val com.vaani.domain.ai.ModelRole.modelKey
        get() = when (this) {
            com.vaani.domain.ai.ModelRole.ASR -> KEY_ASR_MODEL
            com.vaani.domain.ai.ModelRole.LLM -> KEY_LLM_MODEL
            else -> null
        }

    private companion object {
        // Default to on-device: privacy-first, works with the downloaded Whisper
        // model and no API key. The user can switch to Sarvam explicitly.
        val DEFAULT_BACKEND = AiBackend.LOCAL
        val KEY_ASR = stringPreferencesKey("ai_backend_asr")
        val KEY_ENRICH = stringPreferencesKey("ai_backend_enrich")
        val KEY_ASR_MODEL = stringPreferencesKey("ai_model_asr")
        val KEY_LLM_MODEL = stringPreferencesKey("ai_model_llm")
    }
}
