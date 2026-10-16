package com.sreecharan.stressrelief.ui.sleep.audio

import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SynthMathTest {
    @Test
    fun `empty mix renders silence`() {
        val state = SynthMath.SynthState(seed = 1L)
        assertEquals(0f, SynthMath.mixSample(state, emptyMap()))
    }

    @Test
    fun `unknown layer ids are ignored`() {
        val state = SynthMath.SynthState(seed = 1L)
        // Unknown id contributes nothing; white at 0 volume contributes nothing.
        val sample = SynthMath.mixSample(state, mapOf("nope" to 1f, SynthMath.Layer.WHITE to 0f))
        assertEquals(0f, sample)
    }

    @Test
    fun `every layer renders bounded finite audio`() {
        val ids =
            listOf(
                SynthMath.Layer.RAIN,
                SynthMath.Layer.OCEAN,
                SynthMath.Layer.WHITE,
                SynthMath.Layer.BROWN,
                SynthMath.Layer.CHIME,
                SynthMath.Layer.FOREST,
                SynthMath.Layer.THUNDERSTORM,
                SynthMath.Layer.FIREPLACE,
            )
        ids.forEach { id ->
            val state = SynthMath.SynthState(seed = 42L)
            var energy = 0f
            repeat(SynthMath.SAMPLE_RATE) {
                // one full second
                val s = SynthMath.mixSample(state, mapOf(id to 1f))
                assertTrue(s.isFinite(), "layer $id produced non-finite sample")
                assertTrue(abs(s) <= 1f, "layer $id exceeded [-1, 1]: $s")
                energy += abs(s)
            }
            assertTrue(energy > 1f, "layer $id is effectively silent")
        }
    }

    @Test
    fun `full mix stays bounded and finite`() {
        val state = SynthMath.SynthState(seed = 7L)
        val mix =
            mapOf(
                SynthMath.Layer.RAIN to 0.7f,
                SynthMath.Layer.OCEAN to 0.5f,
                SynthMath.Layer.CHIME to 0.4f,
                SynthMath.Layer.FIREPLACE to 0.6f,
            )
        repeat(SynthMath.SAMPLE_RATE / 2) {
            val s = SynthMath.mixSample(state, mix)
            assertTrue(s.isFinite())
            assertTrue(abs(s) <= 1f)
        }
    }

    @Test
    fun `same seed produces identical output`() {
        val first = render(1000, seed = 99L)
        val second = render(1000, seed = 99L)
        assertEquals(first, second)
    }

    @Test
    fun `different seeds diverge`() {
        val first = render(1000, seed = 1L)
        val second = render(1000, seed = 2L)
        assertTrue(first != second, "different seeds should not produce identical noise")
    }

    private fun render(
        samples: Int,
        seed: Long,
    ): List<Float> {
        val state = SynthMath.SynthState(seed = seed)
        val mix = mapOf(SynthMath.Layer.RAIN to 0.8f, SynthMath.Layer.BROWN to 0.5f)
        return List(samples) { SynthMath.mixSample(state, mix) }
    }
}
