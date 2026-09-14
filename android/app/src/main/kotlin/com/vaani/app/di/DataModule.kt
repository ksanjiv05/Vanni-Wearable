package com.vaani.app.di

import com.vaani.data.notes.FakeNotesRepository
import com.vaani.domain.repository.NotesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level data wiring. Binds the in-memory [FakeNotesRepository] from
 * :data:notes to the domain [NotesRepository] interface. Swapping in the
 * Room-backed implementation later is a one-line change here — no feature or
 * ViewModel is touched.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: FakeNotesRepository): NotesRepository
}
