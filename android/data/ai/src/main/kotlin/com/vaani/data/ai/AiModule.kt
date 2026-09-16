package com.vaani.data.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AiEngineSettings
import com.vaani.domain.ai.AsrEngine
import com.vaani.domain.ai.EngineRouter
import com.vaani.domain.ai.Enricher
import com.vaani.domain.ai.ModelRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifies the AI-engine preferences DataStore so it never collides with the
 * other preference stores that land later (settings vault, budget cap — §5.6).
 * A bare `DataStore<Preferences>` binding would become a duplicate-binding error
 * the moment a second store is added; the qualifier keeps each addressable.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiPreferences

// A corrupt on-disk pref file must never brick the app: replace it with empty
// prefs (per-stage keys then fall back to the safe default in the settings impl).
private val Context.aiDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "vaani_ai",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/**
 * Wiring for the pluggable AI engines (ADR-001).
 *
 * Concrete engines are contributed into multibinding maps keyed by [AiBackend]
 * from their own data modules (:data:sarvam, :data:asr-local, :data:llm-local)
 * as they land. Until then the maps may be empty and [EngineRouter] resolves
 * to a typed error (never a silent cross-backend substitution). Adding a backend
 * is a one-line @IntoMap in that module — no change here or in any feature.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    @AiPreferences
    fun provideAiDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.aiDataStore

    @Provides
    @Singleton
    fun provideEngineRouter(
        settings: AiEngineSettings,
        asrEngines: Map<AiBackend, @JvmSuppressWildcards AsrEngine>,
        enrichers: Map<AiBackend, @JvmSuppressWildcards Enricher>,
    ): EngineRouter = EngineRouter(settings, asrEngines, enrichers)

    /**
     * OkHttp for model downloads. Long read timeout — model files are hundreds
     * of MB to >1 GB and stream over one connection.
     */
    @Provides
    @Singleton
    @ModelDownloads
    fun provideModelDownloadClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.MINUTES)
        .build()
}

/** Qualifies the OkHttp client tuned for large model downloads. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ModelDownloads

@Module
@InstallIn(SingletonComponent::class)
abstract class AiBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAiEngineSettings(impl: DataStoreAiEngineSettings): AiEngineSettings

    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: OkHttpModelRepository): ModelRepository

    @Binds
    @Singleton
    abstract fun bindDeviceSpecProvider(impl: AndroidDeviceSpecProvider): com.vaani.domain.ai.DeviceSpecProvider

    @Binds
    @Singleton
    abstract fun bindHfCredentials(impl: DataStoreHfCredentials): com.vaani.domain.ai.HfCredentials

    /** Allow empty engine maps until backend modules contribute @IntoMap entries. */
    @Multibinds
    abstract fun asrEngines(): Map<AiBackend, AsrEngine>

    @Multibinds
    abstract fun enrichers(): Map<AiBackend, Enricher>
}
