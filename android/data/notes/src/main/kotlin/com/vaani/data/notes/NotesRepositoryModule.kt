package com.vaani.data.notes

import com.vaani.domain.repository.NotesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Room-backed [RoomNotesRepository] to the domain [NotesRepository].
 * Lives in :data:notes so :app no longer needs to know the concrete type; the
 * old app/di/DataModule binding is removed.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NotesRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindNotesRepository(impl: RoomNotesRepository): NotesRepository
}
