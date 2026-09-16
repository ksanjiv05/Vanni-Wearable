package com.vaani.data.sarvam

import com.vaani.data.ai.AiBackendKey
import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AsrEngine
import com.vaani.domain.ai.Enricher
import com.vaani.domain.ai.SarvamCredentials
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Wires the Sarvam API backends into the AI-engine multibindings (ADR-001).
 * Merely having this module on the classpath makes "API (Sarvam)" a live choice
 * in the router — no change to :data:ai or any feature.
 */
@Module
@InstallIn(SingletonComponent::class)
object SarvamModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // batch STT / long completions
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRateLimiter(): RateLimiter =
        // Conservative account-wide default; tune once real limits are measured.
        RateLimiter(capacity = 5.0, refillPerSec = 2.0)

    @Provides
    @Singleton
    internal fun provideSarvamHttp(
        client: OkHttpClient,
        credentials: SarvamCredentials,
        limiter: RateLimiter,
        json: Json,
    ): SarvamHttp = ResilientSarvamClient(client, credentials, limiter, json)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SarvamBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCredentials(impl: DataStoreSarvamCredentials): SarvamCredentials

    @Binds
    @IntoMap
    @AiBackendKey(AiBackend.SARVAM)
    abstract fun bindSarvamAsr(impl: SarvamAsrEngine): AsrEngine

    @Binds
    @IntoMap
    @AiBackendKey(AiBackend.SARVAM)
    abstract fun bindSarvamEnricher(impl: SarvamEnricher): Enricher
}
