package com.vaani.data.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Media3/ExoPlayer-backed [AudioPlayer].
 *
 * ExoPlayer requires all construction and interaction on the main thread, so the
 * player is created and driven on [Dispatchers.Main]. A polling coroutine updates
 * [state] every [POLL_INTERVAL_MS] while playing so consumers see live position.
 *
 * Not unit-testable on the JVM (ExoPlayer needs the Android runtime); this class
 * is covered by manual / on-device testing. The content-addressed blob store,
 * which is pure JVM logic, carries the unit tests.
 */
@Singleton
internal class Media3AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AudioPlayer {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(AudioPlaybackState())
    override val state: StateFlow<AudioPlaybackState> = _state.asStateFlow()

    private var pollJob: Job? = null

    // Constructed lazily on the main thread on first use.
    private val player: ExoPlayer by lazy {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateState()
                    if (isPlaying) startPolling() else stopPolling()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateState()
                }
            })
        }
    }

    override fun load(uri: String) = onMain {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        updateState()
    }

    override fun play() = onMain {
        player.play()
        updateState()
    }

    override fun pause() = onMain {
        player.pause()
        updateState()
    }

    override fun seekTo(ms: Long) = onMain {
        player.seekTo(ms)
        updateState()
    }

    override fun release() {
        stopPolling()
        onMain { player.release() }
        scope.cancel()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (true) {
                updateState()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun updateState() {
        _state.value = AudioPlaybackState(
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.let { if (it < 0) 0L else it },
        )
    }

    /** Run [block] on the main thread. */
    private inline fun onMain(crossinline block: () -> Unit) {
        scope.launch { block() }
        return
    }

    private companion object {
        const val POLL_INTERVAL_MS = 250L
    }
}
