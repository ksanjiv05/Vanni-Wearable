package com.vaani.data.vector.di

import com.vaani.data.vector.HashedNgramEmbedder
import com.vaani.domain.ai.Embedder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VectorModule {

    @Binds
    @Singleton
    abstract fun bindEmbedder(impl: HashedNgramEmbedder): Embedder
}
