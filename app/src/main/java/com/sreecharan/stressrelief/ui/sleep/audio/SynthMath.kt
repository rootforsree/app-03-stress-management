package com.sreecharan.stressrelief.ui.sleep.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Pure PCM synthesis for Aura's generated sleep sounds. No binary assets:
 * every layer is computed sample-by-sample from filtered noise and sine
 * stacks at 44.1 kHz mono.
 *
 * All functions are deterministic for a given [SynthState] seed, which makes
 * them unit-testable without Android. The [SleepSoundEngine] owns one
 * [SynthState] per playback run on its render thread.
 */
object SynthMath {
    const val SAMPLE_RATE = 44100

    /** Per-layer filter/LFO state. One instance per playback run. */
    class SynthState(seed: Long = 0L) {
        val random = Random(seed)
        var brown = 0f
        var rainLowpass = 0f
        var rainPatterPhase = 0f
        var oceanPhase = 0f
        var oceanLowpass = 0f
        var padPhase = 0f
        var chirpTimer = 0
        var chirpPhase = 0f
        var chirpFreq = 3000f
        var chirpLength = 0
        var crackleTimer = 0
        var crackleAmp = 0f
        var rumblePhase = 0f
        var rumbleLowpass = 0f
    }

    /** Layer ids shared with [com.sreecharan.stressrelief.ui.sleep.SleepSoundLayers]. */
    object Layer {
        const val RAIN = "rain"
        const val OCEAN = "ocean"
        const val WHITE = "white-noise"
        const val BROWN = "brown-noise"
        const val CHIME = "chime-pads"
        const val FOREST = "forest"
        const val THUNDERSTORM = "thunderstorm"
        const val FIREPLACE = "fireplace"
    }

    private fun white(state: SynthState): Float = state.random.nextFloat() * 2f - 1f

    private fun brown(state: SynthState): Float {
        // Leaky integrator: classic brown-noise approximation.
        state.brown = (state.brown + 0.02f * white(state)) / 1.02f
        return state.brown * 3.5f
    }

    fun rain(state: SynthState): Float {
        // White noise through a one-pole lowpass (steady patter) plus a slow
        // amplitude wander so it never sounds like static.
        val w = white(state)
        state.rainLowpass += 0.18f * (w - state.rainLowpass)
        state.rainPatterPhase += (2f * PI.toFloat() * 0.13f) / SAMPLE_RATE
        val wander = 0.72f + 0.28f * sin(state.rainPatterPhase)
        return state.rainLowpass * 2.2f * wander
    }

    fun ocean(state: SynthState): Float {
        // Slow 0.08 Hz swell LFO over smoothed noise: waves rolling in.
        val w = white(state)
        state.oceanLowpass += 0.06f * (w - state.oceanLowpass)
        state.oceanPhase += (2f * PI.toFloat() * 0.08f) / SAMPLE_RATE
        val swell = 0.35f + 0.65f * (0.5f + 0.5f * sin(state.oceanPhase))
        return state.oceanLowpass * 3.2f * swell
    }

    fun chime(state: SynthState): Float {
        // Gentle pentatonic-ish pad: stacked sines with a 12 s swell.
        // Phase runs at 220 Hz; partials at musical ratios of the phase.
        state.padPhase += (2f * PI.toFloat() * 220f) / SAMPLE_RATE
        val swellPhase = state.padPhase / (220f * 12f)
        val swell = 0.55f + 0.45f * sin(swellPhase)
        val p = state.padPhase
        val sample =
            sin(p) * 0.30f +
                sin(p * 1.1892f) * 0.18f + // minor third-ish shimmer
                sin(p * 1.4983f) * 0.22f + // fifth
                sin(p * 2f) * 0.10f
        return sample * 0.35f * swell
    }

    fun forest(state: SynthState): Float {
        // Soft brown bed plus sparse bird-like chirps.
        val bed = brown(state) * 0.35f
        state.chirpTimer--
        if (state.chirpTimer <= 0) {
            state.chirpFreq = 2200f + state.random.nextFloat() * 1800f
            state.chirpLength = (SAMPLE_RATE * 0.12f).toInt()
            state.chirpPhase = 0f
            state.chirpTimer = SAMPLE_RATE / 2 + state.random.nextInt(SAMPLE_RATE * 2)
        }
        var chirp = 0f
        if (state.chirpLength > 0) {
            state.chirpLength--
            state.chirpPhase += (2f * PI.toFloat() * state.chirpFreq) / SAMPLE_RATE
            // Pitch glides down slightly; amplitude envelope is triangular.
            state.chirpFreq *= 0.99997f
            val progress = 1f - state.chirpLength / (SAMPLE_RATE * 0.12f)
            val env = sin(PI.toFloat() * progress.coerceIn(0f, 1f))
            chirp = sin(state.chirpPhase) * env * 0.25f
        }
        return bed + chirp
    }

    fun thunderstorm(state: SynthState): Float {
        // Deep rumble: heavily smoothed brown noise under a very slow LFO.
        val w = white(state)
        state.rumbleLowpass += 0.02f * (w - state.rumbleLowpass)
        state.rumblePhase += (2f * PI.toFloat() * 0.05f) / SAMPLE_RATE
        val swell = 0.5f + 0.5f * (0.5f + 0.5f * sin(state.rumblePhase))
        return state.rumbleLowpass * 5f * (0.4f + 0.6f * swell)
    }

    fun fireplace(state: SynthState): Float {
        // Warm low bed plus random crackle pops with fast decay.
        val bed = brown(state) * 0.22f
        state.crackleTimer--
        if (state.crackleTimer <= 0) {
            state.crackleAmp = 0.15f + state.random.nextFloat() * 0.5f
            state.crackleTimer = 200 + state.random.nextInt(6000)
        }
        state.crackleAmp *= 0.985f
        val pop = if (state.crackleAmp > 0.001f) (white(state) * state.crackleAmp) else 0f
        return bed + pop
    }

    /**
     * Renders one mixed sample. [mix] maps layer id to volume 0..1; layers
     * absent or at 0 are skipped. Output is soft-clipped to [-1, 1].
     */
    fun mixSample(state: SynthState, mix: Map<String, Float>): Float {
        if (mix.isEmpty()) return 0f
        var sum = 0f
        for ((id, volume) in mix) {
            if (volume <= 0f) continue
            val sample =
                when (id) {
                    Layer.RAIN -> rain(state)
                    Layer.OCEAN -> ocean(state)
                    Layer.WHITE -> white(state) * 0.5f
                    Layer.BROWN -> brown(state) * 0.8f
                    Layer.CHIME -> chime(state)
                    Layer.FOREST -> forest(state)
                    Layer.THUNDERSTORM -> thunderstorm(state)
                    Layer.FIREPLACE -> fireplace(state)
                    else -> 0f
                }
            sum += sample * volume
        }
        // Gentle soft clip so stacked layers never hard-clip.
        return sum / (1f + 0.4f * kotlin.math.abs(sum))
    }
}
