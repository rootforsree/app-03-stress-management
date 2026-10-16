package com.sreecharan.stressrelief.data.model

/**
 * A 0..100 stress estimate. Higher means more stressed.
 */
data class StressScore(
    val score: Int,
    val label: String,
    val factors: List<String>,
)

/**
 * Physiological signals from Health Connect. Either value is null when
 * Health Connect is unavailable, permission is denied, or the read failed.
 */
data class StressSignals(
    val avgRestingHr: Int?,
    val sleepHours: Float?,
)
