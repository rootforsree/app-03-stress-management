package com.sreecharan.stressrelief.data.model

/**
 * Everything the Insights screen needs. Produced by a single
 * [com.sreecharan.stressrelief.data.repository.WellnessRepository.computeInsights]
 * call. Missing inputs surface as nulls, never as zeros.
 */
data class InsightsData(
    /** Last 14 nights, ascending by epochDay. */
    val sleepNights: List<SleepNightScore>,
    /** Null when fewer than 5 valid nights. */
    val sleepDebtHours: Float?,
    val sleepNeedHours: Float,
    /** -100..100, null when fewer than 5 day-pairs. */
    val sri: Int?,
    /** Null when there is insufficient weekday/weekend data. */
    val socialJetlagMin: Int?,
    val restingHrToday: Int?,
    /** 21-day rolling mean of nightly resting HR. */
    val restingHrBaseline: Float?,
    /** Today's z-score vs baseline, positive when above baseline. */
    val restingHrZ: Float?,
    /** Null when no writer (expected on most devices). */
    val hrvRmssdToday: Float?,
    /** 0..100, null while calibrating. */
    val recoveryScore: Int?,
    val recoveryBand: RecoveryBand,
    /** OLS slope, trailing 14 days. */
    val restingHrTrendBpmPerDay: Float?,
    /** OLS slope, trailing 14 days. */
    val sleepTrendMinPerDay: Float?,
    /** Plain-language, ordered by priority. */
    val insightCards: List<InsightCard>,
    /** True when there are fewer than 7 days of health data. */
    val calibrating: Boolean,
    /** False when Health Connect is unavailable or denied. */
    val healthConnected: Boolean,
) {
    companion object {
        /** Neutral fallback: no data anywhere. computeInsights never throws. */
        fun empty(): InsightsData =
            InsightsData(
                sleepNights = emptyList(),
                sleepDebtHours = null,
                sleepNeedHours = SLEEP_NEED_DEFAULT_HOURS,
                sri = null,
                socialJetlagMin = null,
                restingHrToday = null,
                restingHrBaseline = null,
                restingHrZ = null,
                hrvRmssdToday = null,
                recoveryScore = null,
                recoveryBand = RecoveryBand.CALIBRATING,
                restingHrTrendBpmPerDay = null,
                sleepTrendMinPerDay = null,
                insightCards = emptyList(),
                calibrating = true,
                healthConnected = false,
            )

        const val SLEEP_NEED_DEFAULT_HOURS: Float = 8.0f
    }
}

enum class RecoveryBand {
    CHARGED,
    COASTING,
    DEPLETED,
    CALIBRATING,
}

data class InsightCard(
    /** Short, e.g. "Sleep debt is climbing". */
    val title: String,
    /** One plain sentence with the number. */
    val body: String,
    val kind: InsightKind,
)

enum class InsightKind {
    SLEEP,
    RECOVERY,
    TREND,
    CORRELATION,
    HABIT,
}
