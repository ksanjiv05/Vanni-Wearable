package com.vaani.feature.library.di

import com.vaani.domain.repository.FakeNotesRepository
import com.vaani.domain.repository.NotesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Milestone A wiring: binds the in-memory [FakeNotesRepository]. Replaced by
 * the Room-backed repository in :data:notes in a later milestone.
 */
@Module
@InstallIn(SingletonComponent::class)
object FakeDataModule {
    @Provides
    @Singleton
    fun provideNotesRepository(): NotesRepository = FakeNotesRepository()
}
