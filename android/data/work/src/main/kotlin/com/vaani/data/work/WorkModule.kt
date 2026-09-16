package com.vaani.data.work

import com.vaani.domain.pipeline.PipelineEnqueuer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the WorkManager-backed durable [PipelineEnqueuer]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class WorkModule {
    @Binds
    @Singleton
    abstract fun bindEnqueuer(impl: WorkManagerPipelineEnqueuer): PipelineEnqueuer
}
