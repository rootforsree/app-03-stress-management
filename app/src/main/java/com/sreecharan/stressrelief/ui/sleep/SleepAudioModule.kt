package com.sreecharan.stressrelief.ui.sleep

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the AudioTrack-backed sleep sound engine. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SleepAudioModule {
    @Binds
    abstract fun bindSleepAudioPlayer(impl: SleepSoundEngine): SleepAudioPlayer
}
