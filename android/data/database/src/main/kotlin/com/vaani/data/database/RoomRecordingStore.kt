package com.vaani.data.database

import com.vaani.data.database.dao.RecordingDao
import com.vaani.data.database.mapper.toDomain
import com.vaani.data.database.mapper.toEntity
import com.vaani.domain.model.Recording
import com.vaani.domain.repository.RecordingStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed [RecordingStore]: resolves a recording row to its domain model. */
@Singleton
class RoomRecordingStore @Inject constructor(
    private val dao: RecordingDao,
) : RecordingStore, com.vaani.domain.repository.RecordingWriter {
    override suspend fun get(id: String): Recording? = dao.findById(id)?.toDomain()
    override suspend fun upsert(recording: Recording) = dao.upsert(recording.toEntity())
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingStoreModule {
    @Binds
    @Singleton
    abstract fun bindRecordingStore(impl: RoomRecordingStore): RecordingStore

    @Binds
    @Singleton
    abstract fun bindRecordingWriter(impl: RoomRecordingStore): com.vaani.domain.repository.RecordingWriter
}
