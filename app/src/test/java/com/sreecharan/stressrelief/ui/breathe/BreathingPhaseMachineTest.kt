package com.sreecharan.stressrelief.ui.breathe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BreathingPhaseMachineTest {

    private val calm = BreathingPatternSpec(
        id = "4-4-6",
        name = "4-4-6 Calm",
        inhaleSec = 4,
        holdSec = 0,
        exhaleSec = 6,
        holdAfterSec = 0,
        rounds = 6,
    )
    private val box = BreathingPatternSpec(
        id = "box",
        name = "Box",
        inhaleSec = 4,
        holdSec = 4,
        exhaleSec = 4,
        holdAfterSec = 4,
        rounds = 6,
    )

    @Test
    fun `zero duration phases are skipped`() {
        val machine = BreathingPhaseMachine(calm)
        assertEquals(
            listOf(
                PhaseSlice(BreathPhase.INHALE, 4_000L),
                PhaseSlice(BreathPhase.EXHALE, 6_000L),
            ),
            machine.slices,
        )
        assertEquals(10_000L, machine.roundDurationMs)
        assertEquals(60_000L, machine.totalDurationMs)
    }

    @Test
    fun `starts in inhale of round one`() {
        val pos = BreathingPhaseMachine(calm).positionAt(0)!!
        assertEquals(BreathPhase.INHALE, pos.phase)
        assertEquals(1, pos.round)
        assertEquals(6, pos.totalRounds)
        assertEquals(0f, pos.phaseProgress)
        assertEquals(4, pos.secondsRemainingInPhase)
    }

    @Test
    fun `phase boundary moves to the next slice`() {
        val machine = BreathingPhaseMachine(calm)
        val pos = machine.positionAt(4_000)!!
        assertEquals(BreathPhase.EXHALE, pos.phase)
        assertEquals(0f, pos.phaseProgress)
        assertEquals(6, pos.secondsRemainingInPhase)
    }

    @Test
    fun `round increments after the round duration`() {
        val pos = BreathingPhaseMachine(calm).positionAt(10_000)!!
        assertEquals(BreathPhase.INHALE, pos.phase)
        assertEquals(2, pos.round)
    }

    @Test
    fun `returns null at and past the total duration`() {
        val machine = BreathingPhaseMachine(calm)
        assertNull(machine.positionAt(60_000))
        assertNull(machine.positionAt(61_000))
    }

    @Test
    fun `box breathing has four slices per round`() {
        val machine = BreathingPhaseMachine(box)
        assertEquals(4, machine.slices.size)
        assertEquals(BreathPhase.HOLD_AFTER, machine.slices[3].phase)
        assertEquals(16_000L, machine.roundDurationMs)
        assertEquals(BreathPhase.HOLD, machine.positionAt(4_000)!!.phase)
        assertEquals(BreathPhase.EXHALE, machine.positionAt(8_000)!!.phase)
        assertEquals(BreathPhase.HOLD_AFTER, machine.positionAt(12_000)!!.phase)
    }

    @Test
    fun `phase progress is fractional`() {
        val pos = BreathingPhaseMachine(calm).positionAt(2_000)!!
        assertEquals(0.5f, pos.phaseProgress, 0.001f)
        assertEquals(2, pos.secondsRemainingInPhase)
    }

    @Test
    fun `total progress spans the whole session`() {
        val machine = BreathingPhaseMachine(calm)
        assertEquals(0.5f, machine.positionAt(30_000)!!.totalProgress, 0.001f)
    }

    @Test
    fun `negative elapsed is treated as finished`() {
        assertNull(BreathingPhaseMachine(calm).positionAt(-1))
    }
}
