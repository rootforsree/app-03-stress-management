package com.sreecharan.stressrelief.ui.sleep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.sreecharan.stressrelief.ui.sleep.audio.SynthMath
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Streams generated sleep sounds through [AudioTrack]. No binary assets: the
 * render thread mixes the enabled [SynthMath] layers at 44.1 kHz mono and
 * writes PCM16 chunks.
 *
 * Fades in on start and fades out on stop so playback never clicks. The mix
 * can be changed live with [setMix]; volumes are 0..1 per layer id.
 */
@Singleton
class SleepSoundEngine
    @Inject
    constructor() : SleepAudioPlayer {
        companion object {
            private const val SAMPLE_RATE = SynthMath.SAMPLE_RATE
            private const val CHUNK_FRAMES = 1024
            private const val FADE_IN_MS = 2000L
        }

        private val _isPlaying = MutableStateFlow(false)
        override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

        @Volatile private var mix: Map<String, Float> = emptyMap()

        @Volatile private var targetGain = 0f

        @Volatile private var gain = 0f

        private var renderThread: Thread? = null

        @Volatile private var running = false

        /** Replace the active mix; takes effect on the next rendered chunk. */
        override fun setMix(layers: Map<String, Float>) {
            mix = layers.filterValues { it > 0f }
        }

        @Synchronized
        override fun start() {
            if (running) {
                targetGain = 1f
                return
            }
            val minFrames =
                AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            if (minFrames <= 0) return
            val track =
                try {
                    AudioTrack.Builder()
                        .setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build(),
                        )
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setSampleRate(SAMPLE_RATE)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build(),
                        )
                        .setBufferSizeInFrames(minFrames * 2)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .build()
                } catch (_: Exception) {
                    return
                }
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return
            }
            running = true
            gain = 0f
            targetGain = 1f
            val state = SynthMath.SynthState(seed = System.currentTimeMillis())
            val thread =
                Thread({
                    renderLoop(track, state)
                }, "SleepSoundEngine")
            renderThread = thread
            track.play()
            thread.start()
            _isPlaying.value = true
        }

        /**
         * Fades out over [fadeOutMs], then stops and releases the track.
         * Safe to call when not playing.
         */
        @Synchronized
        override fun stop(fadeOutMs: Long) {
            if (!running) return
            targetGain = 0f
            val thread = renderThread
            // Let the render loop finish the fade; do not block the caller
            // longer than the fade plus a small margin.
            try {
                thread?.join(fadeOutMs + 1500L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            running = false
            renderThread = null
            _isPlaying.value = false
        }

        fun isRunning(): Boolean = running

        private fun renderLoop(track: AudioTrack, state: SynthMath.SynthState) {
            val buffer = ShortArray(CHUNK_FRAMES)
            val fadeStepUp = 1f / (SAMPLE_RATE * FADE_IN_MS / 1000f / CHUNK_FRAMES)
            // Fade-out step is recomputed per chunk from the remaining gain so
            // stop() always lands near silence even for short fades.
            try {
                while (running) {
                    val currentMix = mix
                    val currentTarget = targetGain
                    val step =
                        if (currentTarget > gain) {
                            fadeStepUp
                        } else {
                            // Reach the target in roughly 60 chunks (~1.4 s).
                            (gain - currentTarget) / 60f
                        }
                    gain =
                        when {
                            gain < currentTarget -> (gain + step).coerceAtMost(currentTarget)
                            gain > currentTarget -> (gain - step).coerceAtLeast(currentTarget)
                            else -> gain
                        }
                    val g = gain
                    for (i in 0 until CHUNK_FRAMES) {
                        val sample = SynthMath.mixSample(state, currentMix) * g
                        buffer[i] = (sample.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                    }
                    track.write(buffer, 0, CHUNK_FRAMES)
                    if (currentTarget == 0f && gain <= 0.001f) {
                        break
                    }
                }
            } catch (_: Exception) {
                // Audio device gone; exit the loop and release below.
            } finally {
                running = false
                try {
                    track.stop()
                } catch (_: Exception) {
                    // Already stopped; nothing to do.
                }
                track.release()
            }
        }
    }
