package com.sreecharan.stressrelief

import com.sreecharan.stressrelief.data.model.StressSignals
import com.sreecharan.stressrelief.data.repository.StressScoreCalculator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure-JVM tests for the stress score blending math. No Android framework needed. */
class StressScoreCalculatorTest {
    private val noSignals = StressSignals(avgRestingHr = null, sleepHours = null)

    @Test
    fun `best possible mood scores calm at zero`() {
        val score = StressScoreCalculator.compute(avgMood = 5.0, signals = noSignals, checkInCount = 7)

        assertEquals(0, score.score)
        assertEquals("Calm", score.label)
    }

    @Test
    fun `worst possible mood scores high at one hundred`() {
        val score = StressScoreCalculator.compute(avgMood = 1.0, signals = noSignals, checkInCount = 7)

        assertEquals(100, score.score)
        assertEquals("High", score.label)
    }

    @Test
    fun `neutral mood lands elevated at fifty`() {
        val score = StressScoreCalculator.compute(avgMood = 3.0, signals = noSignals, checkInCount = 3)

        assertEquals(50, score.score)
        assertEquals("Elevated", score.label)
    }

    @Test
    fun `no check-ins falls back to neutral estimate`() {
        val score = StressScoreCalculator.compute(avgMood = null, signals = noSignals, checkInCount = 0)

        assertEquals(50, score.score)
        assertEquals("Moderate", score.label)
        assertTrue(score.factors.any { it.contains("No check-ins") })
    }

    @Test
    fun `missing health signals degrade to mood only`() {
        val moodOnly = StressScoreCalculator.compute(avgMood = 2.0, signals = noSignals, checkInCount = 5)

        // (5 - 2) / 4 * 100 = 75
        assertEquals(75, moodOnly.score)
        assertEquals("High", moodOnly.label)
        assertTrue(moodOnly.factors.any { it.contains("mood-only") })
    }

    @Test
    fun `health signals blend at sixty forty`() {
        // moodStress = (5 - 3) / 4 * 100 = 50
        // hrStress = (95 - 55) / 45 * 100 = 88.9, sleepStress = 0 (8h is ideal)
        // health = 44.4, final = 50 * 0.6 + 44.4 * 0.4 = 47.8 -> 48
        val signals = StressSignals(avgRestingHr = 95, sleepHours = 8f)
        val score = StressScoreCalculator.compute(avgMood = 3.0, signals = signals, checkInCount = 5)

        assertEquals(48, score.score)
        assertEquals("Moderate", score.label)
        assertTrue(score.factors.any { it.contains("Blended") })
    }

    @Test
    fun `short sleep adds a sleep factor`() {
        val signals = StressSignals(avgRestingHr = null, sleepHours = 5f)
        val score = StressScoreCalculator.compute(avgMood = 4.0, signals = signals, checkInCount = 4)

        assertTrue(score.factors.any { it.contains("sleep") })
    }

    @Test
    fun `elevated resting heart rate adds an hr factor`() {
        val signals = StressSignals(avgRestingHr = 90, sleepHours = null)
        val score = StressScoreCalculator.compute(avgMood = 4.0, signals = signals, checkInCount = 4)

        assertTrue(score.factors.any { it.contains("90 bpm") })
    }

    @Test
    fun `score never escapes zero to one hundred`() {
        val low = StressScoreCalculator.compute(avgMood = 5.0, signals = StressSignals(120, 1f), checkInCount = 1)
        val high = StressScoreCalculator.compute(avgMood = 1.0, signals = StressSignals(200, 0f), checkInCount = 1)

        assertTrue(low.score in 0..100)
        assertTrue(high.score in 0..100)
    }

    @Test
    fun `label thresholds hold`() {
        assertEquals("Calm", StressScoreCalculator.compute(4.5, noSignals, 2).label)
        assertEquals("Moderate", StressScoreCalculator.compute(4.0, noSignals, 2).label)
        assertEquals("Elevated", StressScoreCalculator.compute(3.0, noSignals, 2).label)
        assertEquals("High", StressScoreCalculator.compute(1.5, noSignals, 2).label)
    }
}
