package com.vaani.data.asr.local

import com.vaani.domain.ai.AiBackend
import com.vaani.domain.ai.AsrEngine
import com.vaani.data.ai.AiBackendKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

/**
 * Contributes [LocalAsrEngine] into the AI-engine multibinding keyed by
 * [AiBackend.LOCAL] (ADR-001). Merely putting this module on the app classpath
 * makes "On-device" a live choice in the router — no change to :data:ai or any
 * feature. The Sarvam engine module contributes AiBackend.SARVAM the same way.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocalAsrModule {

    @Binds
    @IntoMap
    @AiBackendKey(AiBackend.LOCAL)
    abstract fun bindLocalAsr(impl: LocalAsrEngine): AsrEngine
}
