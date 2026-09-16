package com.vaani.data.audio

import com.vaani.domain.audio.AudioPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt wiring for the content-addressed audio store and the Media3 player.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AudioModule {

    @Binds
    @Singleton
    abstract fun bindAudioBlobStore(impl: DefaultAudioBlobStore): AudioBlobStore

    @Binds
    @Singleton
    abstract fun bindAudioPlayer(impl: Media3AudioPlayer): AudioPlayer
}
