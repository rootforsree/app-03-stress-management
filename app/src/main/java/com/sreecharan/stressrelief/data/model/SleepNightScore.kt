package com.sreecharan.stressrelief.data.model

/**
 * One night of scored sleep. All fields are nullable when the underlying
 * data is missing; a missing input is never scored as zero.
 */
data class SleepNightScore(
    val epochDay: Long,
    val totalSleepMin: Int?,
    val timeInBedMin: Int?,
    val sleepEfficiencyPct: Int?,
    /** TST / TIB * 100. */
    val deepPct: Float?,
    /** deep / TST * 100. */
    val remPct: Float?,
    val wasoMin: Int?,
    val awakenings: Int?,
    val latencyMin: Int?,
    /** 0..100 composite, null when the night cannot be scored. */
    val score: Int?,
    val durationScore: Int?,
    val efficiencyScore: Int?,
    val architectureScore: Int?,
    val continuityScore: Int?,
    val latencyScore: Int?,
)
