package com.vaani.data.pipeline

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.datetime.Clock
import javax.inject.Singleton

/**
 * Provides the wall clock for the ingest pipeline. Bound here (not a Kotlin
 * default arg, which Dagger can't see) so [IngestPipeline] is injectable. Tests
 * pass a fixed clock directly to the constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.System
}
