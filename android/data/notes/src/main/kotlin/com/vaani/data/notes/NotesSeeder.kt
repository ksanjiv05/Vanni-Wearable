package com.vaani.data.notes

import com.vaani.data.database.dao.RecordingDao
import com.vaani.data.database.mapper.toEntity
import com.vaani.domain.repository.NotesWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * First-run seeder. Inserts the three fixture notes (note-standup, note-vendor,
 * note-design) + their recordings + the standup transcript, verbatim from
 * [NotesSeedData], IFF the database is empty. Idempotent and safe to call
 * repeatedly.
 *
 * How it runs: [VaaniApp] calls [seedIfEmpty] (fire-and-forget on a background
 * scope) at process start. The empty-check keeps it deterministic — real
 * persisted/edited notes are never overwritten.
 */
@Singleton
class NotesSeeder @Inject constructor(
    private val recordingDao: RecordingDao,
    private val notesWriter: NotesWriter,
) {
    private val mutex = Mutex()

    /**
     * Suspending seed: previously inserted demo fixtures. Now a no-op — the app
     * starts EMPTY so the user's imported recordings are the only content. Kept
     * as a seam (and the fixtures remain in NotesSeedData) so we can re-enable a
     * first-run sample from Settings later if desired.
     */
    suspend fun seedIfEmpty() {
        // Intentionally empty: no demo data. Import audio to create real notes.
    }

    /** Fire-and-forget helper for Application.onCreate. */
    fun seedInBackground(scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
        scope.launch { seedIfEmpty() }
    }
}
