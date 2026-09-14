package com.vaani.data.database.di

import android.content.Context
import androidx.room.Room
import com.vaani.data.database.VaaniDatabase
import com.vaani.data.database.dao.NoteDao
import com.vaani.data.database.dao.RecordingDao
import com.vaani.data.database.dao.TranscriptDao
import com.vaani.data.database.writer.RoomNotesWriter
import com.vaani.domain.repository.NotesWriter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the single Room [VaaniDatabase] and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaaniDatabase =
        Room.databaseBuilder(context, VaaniDatabase::class.java, "vaani.db")
            .build()

    @Provides
    fun provideNoteDao(db: VaaniDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideRecordingDao(db: VaaniDatabase): RecordingDao = db.recordingDao()

    @Provides
    fun provideTranscriptDao(db: VaaniDatabase): TranscriptDao = db.transcriptDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseBindingsModule {
    @Binds
    abstract fun bindNotesWriter(impl: RoomNotesWriter): NotesWriter
}
