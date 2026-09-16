package com.vaani.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.vaani.data.notes.NotesSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Provides a WorkManager [Configuration] so Hilt-injected workers
 * ([com.vaani.data.work.TranscriptionWorker]) can be constructed with their
 * app-scoped dependencies. The default WorkManager initializer is disabled in
 * the manifest so this on-demand configuration is used instead.
 */
@HiltAndroidApp
class VaaniApp : Application(), Configuration.Provider {

    /** Seeds the Room DB with fixture notes on first run (no-op when non-empty). */
    @Inject lateinit var notesSeeder: NotesSeeder

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notesSeeder.seedInBackground()
    }
}
