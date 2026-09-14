package com.vaani.app

import android.app.Application
import com.vaani.data.notes.NotesSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VaaniApp : Application() {

    /** Seeds the Room DB with fixture notes on first run (no-op when non-empty). */
    @Inject lateinit var notesSeeder: NotesSeeder

    override fun onCreate() {
        super.onCreate()
        notesSeeder.seedInBackground()
    }
}
