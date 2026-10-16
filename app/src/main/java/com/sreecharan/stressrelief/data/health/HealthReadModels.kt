package com.sreecharan.stressrelief.data.health

/**
 * Framework-free views of Health Connect reads, produced by
 * [HealthConnectClient] and consumed by the repository layer. Pure Kotlin:
 * no Android or Health Connect SDK types leak past these.
 */

/** One night of sleep as read from Health Connect. */
data class SleepNight(
    /** Local calendar day the session ended (the morning the night belongs to). */
    val epochDay: Long,
    val sessionStartMs: Long,
    val sessionEndMs: Long,
    val stages: List<SleepStageSegment>,
)

data class SleepStageSegment(
    val startMs: Long,
    val endMs: Long,
    val type: SleepStageType,
)

enum class SleepStageType {
    AWAKE,
    LIGHT,
    DEEP,
    REM,
    /** Generic "asleep" stage some writers emit instead of light/deep/REM. */
    SLEEPING,
    OUT_OF_BED,
    UNKNOWN,
}

/** One day of resting-HR data. */
data class RestingHrDay(
    val epochDay: Long,
    val bpm: Double,
    /**
     * 0..1 fraction of the overnight 00:00-06:00 window covered by heart-rate
     * samples. Always 1.0 for record-based days (a RestingHeartRateRecord is
     * the source's own summary); computed from sample coverage for derived
     * days. Days below 0.7 are excluded from baselines (anti-ratchet).
     */
    val completeness: Double,
    /** True when derived from the lowest-20% overnight sample fallback. */
    val derived: Boolean,
)

/** One day of HRV RMSSD. Only present when a writer supplies the record. */
data class HrvDay(
    val epochDay: Long,
    val rmssdMs: Double,
)

/**
 * One day of step/distance totals, already deduped across writers.
 *
 * Data-quality rule (from real-world Health Connect behavior): Samsung
 * Health writes 2 identical rows per day per device UUID and Google Fit
 * writes its own copy, so raw step counts come back ~3x inflated. The
 * repository collapses to one row per day (max per writer, then max across
 * writers). Distance in meters is the more reliable signal and is preferred
 * wherever both exist.
 */
data class StepDay(
    val epochDay: Long,
    val steps: Long,
    /** Null when no writer supplied distance for the day. */
    val distanceMeters: Double?,
)

/**
 * Minute-level asleep/awake strip for one local calendar day.
 * [asleep][i] is true when minute-of-day i was spent asleep.
 */
class SleepDayStrip(
    val epochDay: Long,
    val asleep: BooleanArray,
) {
    override fun equals(other: Any?): Boolean =
        other is SleepDayStrip && epochDay == other.epochDay && asleep.contentEquals(other.asleep)

    override fun hashCode(): Int = 31 * epochDay.hashCode() + asleep.contentHashCode()
}

/**
 * One raw step/distance record as read from Health Connect, before dedup.
 * Framework-free so the collapse rule stays unit-testable.
 */
data class StepSourceEntry(
    val epochDay: Long,
    val packageName: String,
    val steps: Long,
    val distanceMeters: Double,
)
