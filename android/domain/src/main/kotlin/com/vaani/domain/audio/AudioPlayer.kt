package com.vaani.domain.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Snapshot of audio playback state.
 *
 * @property isPlaying whether playback is currently active.
 * @property positionMs current playback position in milliseconds.
 * @property durationMs total media duration in milliseconds, or 0 if unknown.
 */
data class AudioPlaybackState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Playback port (domain seam). Presentation depends on THIS interface; the
 * concrete Media3/ExoPlayer implementation lives in :data:audio and is bound in
 * the app graph — features never touch the vendor player directly.
 *
 * The underlying player must be used on the main thread; implementations marshal
 * calls accordingly. Callers must invoke [release] to free resources.
 */
interface AudioPlayer {

    /** Set the media source from [uri] (file path or content/file uri) and prepare it. */
    fun load(uri: String)

    fun play()

    fun pause()

    fun seekTo(ms: Long)

    /** Release the underlying player. The instance must not be used afterwards. */
    fun release()

    /** Reactive playback state (isPlaying, position, duration). */
    val state: StateFlow<AudioPlaybackState>
}
