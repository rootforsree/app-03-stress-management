package com.sreecharan.stressrelief.ui.sleep

import kotlinx.coroutines.flow.StateFlow

/**
 * Plays generated sleep audio. The production implementation
 * ([SleepSoundEngine]) streams [audio.SynthMath] layers through AudioTrack;
 * tests substitute a fake.
 */
interface SleepAudioPlayer {
    val isPlaying: StateFlow<Boolean>

    /** Replace the active mix; layer id -> volume 0..1. */
    fun setMix(layers: Map<String, Float>)

    fun start()

    /** Fades out, then stops and releases. Safe to call when not playing. */
    fun stop(fadeOutMs: Long = 1200L)
}
