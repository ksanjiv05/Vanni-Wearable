package com.vaani.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AiEngineSettings
import com.vaani.domain.ai.AsrEngine
import com.vaani.domain.ai.EngineRouter
import com.vaani.domain.ai.Enricher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(name = "vaani_ai")

/**
 * Wiring for the pluggable AI engines (ADR-001).
 *
 * Concrete engines are contributed into multibinding maps keyed by [AiBackend]
 * from their own data modules (:data:sarvam, :data:asr-local, :data:llm-local)
 * as they land. Until then the maps may be empty and [EngineRouter] resolves
 * with its safe fallback. Adding a backend is a one-line @IntoMap in that
 * module — no change here or in any feature.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.aiDataStore

    @Provides
    @Singleton
    fun provideEngineRouter(
        settings: AiEngineSettings,
        asrEngines: Map<AiBackend, @JvmSuppressWildcards AsrEngine>,
        enrichers: Map<AiBackend, @JvmSuppressWildcards Enricher>,
    ): EngineRouter = EngineRouter(settings, asrEngines, enrichers)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAiEngineSettings(impl: DataStoreAiEngineSettings): AiEngineSettings

    /** Allow empty engine maps until backend modules contribute @IntoMap entries. */
    @Multibinds
    abstract fun asrEngines(): Map<AiBackend, AsrEngine>

    @Multibinds
    abstract fun enrichers(): Map<AiBackend, Enricher>
}
