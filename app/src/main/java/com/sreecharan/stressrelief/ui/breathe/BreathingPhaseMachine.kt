package com.sreecharan.stressrelief.ui.breathe

import kotlin.math.ceil

private const val MS_PER_SEC = 1_000L

/**
 * The four breath phases. HOLD_AFTER is the rest beat after the exhale
 * (used by patterns like box breathing).
 */
enum class BreathPhase {
    INHALE,
    HOLD,
    EXHALE,
    HOLD_AFTER,
}

/** One phase slice inside a round, with its duration in milliseconds. */
data class PhaseSlice(
    val phase: BreathPhase,
    val durationMs: Long,
)

/** Where the session is at a given elapsed time. Null means finished. */
data class SlicePosition(
    val phase: BreathPhase,
    val round: Int,
    val totalRounds: Int,
    val phaseElapsedMs: Long,
    val phaseDurationMs: Long,
    val phaseProgress: Float,
    val totalProgress: Float,
    val secondsRemainingInPhase: Int,
)

/** Timing spec for one pattern. Mirrors data.catalog.BreathingPattern. */
data class BreathingPatternSpec(
    val id: String,
    val name: String,
    val inhaleSec: Int,
    val holdSec: Int,
    val exhaleSec: Int,
    val holdAfterSec: Int,
    val rounds: Int,
) {
    val totalSeconds: Int
        get() = (inhaleSec + holdSec + exhaleSec + holdAfterSec) * rounds
}

/**
 * Pure, Android-free model of a breathing session timeline.
 *
 * Zero-duration phases are skipped, so "4-4-6" is simply
 * INHALE(4s) -> EXHALE(6s) per round. All math is integer milliseconds;
 * [positionAt] returns null once the final round ends.
 */
class BreathingPhaseMachine(val spec: BreathingPatternSpec) {

    val slices: List<PhaseSlice> = buildList {
        if (spec.inhaleSec > 0) add(PhaseSlice(BreathPhase.INHALE, spec.inhaleSec * MS_PER_SEC))
        if (spec.holdSec > 0) add(PhaseSlice(BreathPhase.HOLD, spec.holdSec * MS_PER_SEC))
        if (spec.exhaleSec > 0) add(PhaseSlice(BreathPhase.EXHALE, spec.exhaleSec * MS_PER_SEC))
        if (spec.holdAfterSec > 0) add(PhaseSlice(BreathPhase.HOLD_AFTER, spec.holdAfterSec * MS_PER_SEC))
    }

    val roundDurationMs: Long = slices.sumOf { it.durationMs }

    val totalDurationMs: Long = roundDurationMs * spec.rounds.coerceAtLeast(0)

    fun positionAt(elapsedMs: Long): SlicePosition? {
        if (elapsedMs < 0 || slices.isEmpty() || spec.rounds <= 0) return null
        if (elapsedMs >= totalDurationMs) return null
        val round = (elapsedMs / roundDurationMs).toInt()
        var remaining = elapsedMs - round * roundDurationMs
        for (slice in slices) {
            if (remaining < slice.durationMs) {
                val progress = (remaining.toFloat() / slice.durationMs).coerceIn(0f, 1f)
                return SlicePosition(
                    phase = slice.phase,
                    round = round + 1,
                    totalRounds = spec.rounds,
                    phaseElapsedMs = remaining,
                    phaseDurationMs = slice.durationMs,
                    phaseProgress = progress,
                    totalProgress = (elapsedMs.toFloat() / totalDurationMs).coerceIn(0f, 1f),
                    secondsRemainingInPhase =
                        ceil((slice.durationMs - remaining) / MS_PER_SEC.toDouble()).toInt().coerceAtLeast(1),
                )
            }
            remaining -= slice.durationMs
        }
        return null
    }
}
