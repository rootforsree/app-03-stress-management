package com.sreecharan.stressrelief.ui.breathe

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tiny soft-tone player for eyes-closed breathing sessions: one short sine
 * beep per phase change, with a raised-cosine envelope so it fades in and
 * out instead of clicking. The AudioTrack is created lazily and must be
 * released via [release] (the session screen does this on dispose).
 *
 * [play] blocks for the beep duration, so callers must invoke it off the
 * main thread.
 */
internal class SoftTonePlayer {
    private var track: AudioTrack? = null

    /**
     * Plays one soft beep. [frequencyHz] picks the pitch, [durationMs] the
     * length, [volume] the peak amplitude (0..1, keep it gentle).
     */
    fun play(
        frequencyHz: Int,
        durationMs: Int = TONE_MS,
        volume: Float = TONE_VOLUME,
    ) {
        runCatching {
            val sampleCount = SAMPLE_RATE * durationMs / MS_PER_SEC
            val samples =
                ShortArray(sampleCount) { i ->
                    val t = i.toFloat() / SAMPLE_RATE
                    // Raised-cosine envelope: no click at either end.
                    val envelope = (0.5f * (1f - cos(2f * PI.toFloat() * i / sampleCount))).toFloat()
                    val tone = sin(2f * PI.toFloat() * frequencyHz * t)
                    (envelope * volume * tone * Short.MAX_VALUE).toInt().toShort()
                }
            val audioTrack = track ?: createTrack().also { track = it }
            audioTrack.write(samples, 0, sampleCount)
            audioTrack.play()
        }
    }

    fun release() {
        runCatching {
            track?.stop()
            track?.release()
        }
        track = null
    }

    private fun createTrack(): AudioTrack {
        val bufferSize =
            AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            ).setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            ).setBufferSizeInBytes(bufferSize.coerceAtLeast(SAMPLE_RATE * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
        const val MS_PER_SEC = 1_000
        const val TONE_MS = 320
        const val TONE_VOLUME = 0.28f
    }
}

/** Gentle pitch per phase: inhale rises, exhale settles. */
internal fun BreathPhase.toneHz(): Int =
    when (this) {
        BreathPhase.INHALE -> 523
        BreathPhase.HOLD -> 440
        BreathPhase.EXHALE -> 349
        BreathPhase.HOLD_AFTER -> 392
    }
