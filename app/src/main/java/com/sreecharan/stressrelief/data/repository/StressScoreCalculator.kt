package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.model.StressSignals
import kotlin.math.roundToInt

/**
 * Pure stress-score math, kept separate from the repository so it is unit
 * testable without Room or Health Connect.
 *
 * Blend: recent mood scores carry 60%, Health Connect signals carry 40%
 * (resting heart rate and sleep, split evenly). When Health Connect signals
 * are missing, the score degrades to mood-only.
 */
object StressScoreCalculator {
    fun compute(
        avgMood: Double?,
        signals: StressSignals,
        checkInCount: Int,
    ): StressScore {
        if (avgMood == null || checkInCount == 0) {
            return StressScore(
                score = 50,
                label = "Moderate",
                factors = listOf("No check-ins this week; showing a neutral estimate"),
            )
        }

        val moodStress = (5.0 - avgMood.coerceIn(1.0, 5.0)) / 4.0 * 100.0
        val factors = mutableListOf("Mood this week: ${"%.1f".format(avgMood)}/5")

        val hrStress =
            signals.avgRestingHr?.let { hr ->
                val stress = ((hr - 55).toDouble() / 45.0 * 100.0).coerceIn(0.0, 100.0)
                if (stress >= 60.0) factors.add("Resting heart rate $hr bpm is elevated")
                stress
            }

        val sleepStress =
            signals.sleepHours?.let { hours ->
                val stress =
                    when {
                        hours in 7f..9f -> 0.0
                        hours < 7f -> ((7f - hours) / 7f * 100f).toDouble()
                        else -> (((hours - 9f) / 3f * 100f).coerceAtMost(50f)).toDouble()
                    }
                if (hours < 7f) factors.add("Averaging ${"%.1f".format(hours)}h of sleep a night")
                stress
            }

        val healthScore =
            when {
                hrStress != null && sleepStress != null -> (hrStress + sleepStress) / 2.0
                hrStress != null -> hrStress
                sleepStress != null -> sleepStress
                else -> null
            }

        val finalScore =
            if (healthScore != null) {
                factors.add("Blended from mood (60%) and Health Connect (40%)")
                moodStress * 0.6 + healthScore * 0.4
            } else {
                factors.add("Health Connect unavailable; mood-only estimate")
                moodStress
            }

        val score = finalScore.roundToInt().coerceIn(0, 100)
        // v2 label bands (contract section 4): 0-25 Low, 26-50 Moderate,
        // 51-75 High, 76-100 Very high. Higher = more stressed.
        val label =
            when {
                score >= 76 -> "Very high"
                score >= 51 -> "High"
                score >= 26 -> "Moderate"
                else -> "Low"
            }
        return StressScore(score = score, label = label, factors = factors.toList())
    }
}
