package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.health.SleepDayStrip
import com.sreecharan.stressrelief.data.health.StepDay
import com.sreecharan.stressrelief.data.health.StepSourceEntry
import com.sreecharan.stressrelief.data.model.InsightCard
import com.sreecharan.stressrelief.data.model.InsightKind
import com.sreecharan.stressrelief.data.model.RecoveryBand
import com.sreecharan.stressrelief.data.model.SleepNightScore
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * All nine insight algorithms from contract section 7. Pure functions,
 * unit-testable, zero Android imports.
 *
 * Formulas follow research/health-algos-sleep.md (sleep quality composite,
 * Oura-style sleep debt, Phillips 2017 SRI, Roenneberg social jetlag) and
 * research/health-algos-hrv.md (personal-baseline z-scores, whoop-style
 * recovery composite with missing-input rescaling, OLS trends, Pearson
 * correlations).
 *
 * Conventions:
 * - Missing inputs are dropped and remaining weights rescaled; missing data
 *   is never scored as zero.
 * - z-scores are signed so that positive means FAVORABLE (below RHR baseline,
 *   low debt, above HRV baseline, above breathing baseline), except
 *   [restingHrZ] which follows the contract literally: (today - mean) / std.
 */
object InsightCalculators {
    // ------------------------------------------------------------------
    // Shared input types (pure data, no framework types).
    // ------------------------------------------------------------------

    /** One night of raw sleep inputs. Nulls mean the input is missing. */
    data class SleepNightInput(
        val epochDay: Long,
        val totalSleepMin: Int?,
        val timeInBedMin: Int?,
        val deepMin: Int?,
        val remMin: Int?,
        val wasoMin: Int?,
        val awakenings: Int?,
        val latencyMin: Int?,
        val sleepOnsetMs: Long?,
        val wakeMs: Long?,
    )

    data class RestingHrDayInput(
        val epochDay: Long,
        val bpm: Double,
        /** 0..1 overnight coverage; days at or below 0.7 are excluded. */
        val completeness: Double,
    )

    data class HrvDayInput(
        val epochDay: Long,
        val rmssdMs: Double,
    )

    data class BreathingDayInput(
        val epochDay: Long,
        val minutes: Int,
    )

    data class OlsPoint(
        val x: Double,
        val y: Double,
    )

    /** Personal baseline: trailing mean/std over admitted days. */
    data class HrBaseline(
        val mean: Double,
        val std: Double,
        val days: Int,
    )

    /**
     * Recovery drivers as favorable-positive z-scores. Any null driver is
     * dropped and the remaining weights rescaled.
     */
    data class RecoveryInput(
        val rhrZ: Double?,
        val debtZ: Double?,
        val hrvZ: Double?,
        val breathingZ: Double?,
    )

    data class HabitContext(
        val rhrZ: Float?,
        val rhrStreakNights: Int,
        val rhrStreakPct: Double?,
        val recoveryBand: RecoveryBand,
        val recoveryScore: Int?,
        val sleepDebtHours: Float?,
        val lastNightScore: Int?,
        val sri: Int?,
        val socialJetlagMin: Int?,
        val breathingMinutesToday: Int,
    )

    // ------------------------------------------------------------------
    // 1. Sleep quality score (per night).
    // ------------------------------------------------------------------

    /**
     * 30% Duration + 25% Efficiency + 25% Architecture + 15% Continuity +
     * 5% Latency, trapezoidal sub-scoring. Components with missing inputs are
     * dropped and weights rescaled. Returns a score of null when nothing is
     * scorable.
     */
    fun sleepNightScore(input: SleepNightInput): SleepNightScore {
        val tst = input.totalSleepMin?.takeIf { it > 0 }
        val tib = input.timeInBedMin?.takeIf { it > 0 }

        val durationScore = tst?.let { trapezoid(it.toDouble(), 240.0, 420.0, 540.0, 600.0).roundToInt() }

        val efficiencyPct = if (tst != null && tib != null) tst.toDouble() / tib * 100.0 else null
        val efficiencyScore =
            efficiencyPct?.let {
                when {
                    it >= 90.0 -> 100
                    it <= 60.0 -> 0
                    else -> ((it - 60.0) / 30.0 * 100.0).roundToInt()
                }
            }

        val deepPct = if (tst != null && input.deepMin != null) input.deepMin.toDouble() / tst * 100.0 else null
        val remPct = if (tst != null && input.remMin != null) input.remMin.toDouble() / tst * 100.0 else null
        val deepScore = deepPct?.let { trapezoid(it, 5.0, 13.0, 23.0, 32.0).roundToInt() }
        val remScore = remPct?.let { trapezoid(it, 10.0, 20.0, 25.0, 35.0).roundToInt() }
        val architectureScore =
            listOfNotNull(deepScore, remScore).takeIf { it.isNotEmpty() }?.average()?.roundToInt()

        val wasoScore =
            input.wasoMin?.let {
                when {
                    it <= 20 -> 100
                    it >= 60 -> 0
                    else -> ((60 - it) / 40.0 * 100.0).roundToInt()
                }
            }
        val awakeningScore =
            input.awakenings?.let {
                when {
                    it <= 1 -> 100
                    it >= 6 -> 0
                    else -> ((6 - it) / 5.0 * 100.0).roundToInt()
                }
            }
        val continuityScore = listOfNotNull(wasoScore, awakeningScore).takeIf { it.isNotEmpty() }?.min()

        val latencyScore = input.latencyMin?.let { trapezoid(it.toDouble(), 5.0, 10.0, 20.0, 30.0).roundToInt() }

        val weighted =
            listOf(
                durationScore to WEIGHT_DURATION,
                efficiencyScore to WEIGHT_EFFICIENCY,
                architectureScore to WEIGHT_ARCHITECTURE,
                continuityScore to WEIGHT_CONTINUITY,
                latencyScore to WEIGHT_LATENCY,
            ).mapNotNull { (score, weight) -> score?.let { it to weight } }
        val score =
            if (weighted.isEmpty()) {
                null
            } else {
                val weightSum = weighted.sumOf { (_, w) -> w }
                (weighted.sumOf { (s, w) -> s * w } / weightSum).roundToInt().coerceIn(0, 100)
            }

        return SleepNightScore(
            epochDay = input.epochDay,
            totalSleepMin = input.totalSleepMin,
            timeInBedMin = input.timeInBedMin,
            sleepEfficiencyPct = efficiencyPct?.roundToInt(),
            deepPct = deepPct?.toFloat(),
            remPct = remPct?.toFloat(),
            wasoMin = input.wasoMin,
            awakenings = input.awakenings,
            latencyMin = input.latencyMin,
            bedTimeMs = input.sleepOnsetMs,
            wakeTimeMs = input.wakeMs,
            score = score,
            durationScore = durationScore,
            efficiencyScore = efficiencyScore,
            architectureScore = architectureScore,
            continuityScore = continuityScore,
            latencyScore = latencyScore,
        )
    }

    // ------------------------------------------------------------------
    // 2. Sleep debt (14-night linear-decay window).
    // ------------------------------------------------------------------

    /**
     * debt = sum over the last 14 nights of w(i) * (need - slept_i),
     * w(i) = 1 - 0.75 * i / 13. Nights with no data are skipped but consume
     * their slot (index 0 = most recent night). Null when fewer than 5 valid
     * nights. Clamped to [0, 10] hours.
     */
    fun sleepDebt(
        nights: List<SleepNightInput>,
        sleepNeedHours: Float = 8f,
    ): Float? {
        var valid = 0
        var debt = 0.0
        nights.take(DEBT_WINDOW_NIGHTS).forEachIndexed { index, night ->
            val sleptHours = night.totalSleepMin?.let { it / 60.0 } ?: return@forEachIndexed
            valid++
            debt += (1.0 - 0.75 * index / 13.0) * (sleepNeedHours - sleptHours)
        }
        if (valid < DEBT_MIN_VALID_NIGHTS) return null
        return debt.coerceIn(0.0, 10.0).toFloat()
    }

    // ------------------------------------------------------------------
    // 3. Sleep Regularity Index (Phillips et al. 2017).
    // ------------------------------------------------------------------

    /**
     * SRI = -100 + 200 * matches / (M * pairs), M = 1440 minutes. Only
     * calendar-adjacent day pairs count (a true 24h lag). Null when fewer
     * than 5 day-pairs.
     */
    fun sleepRegularityIndex(strips: List<SleepDayStrip>): Int? {
        val sorted = strips.sortedBy { it.epochDay }
        var matches = 0L
        var pairs = 0
        for (i in 0 until sorted.size - 1) {
            val today = sorted[i]
            val tomorrow = sorted[i + 1]
            if (tomorrow.epochDay != today.epochDay + 1) continue
            val minutes = minOf(today.asleep.size, tomorrow.asleep.size, MINUTES_PER_DAY)
            for (minute in 0 until minutes) {
                if (today.asleep[minute] == tomorrow.asleep[minute]) matches++
            }
            pairs++
        }
        if (pairs < SRI_MIN_DAY_PAIRS) return null
        val total = pairs.toLong() * MINUTES_PER_DAY
        return (-100.0 + 200.0 * matches / total).roundToInt().coerceIn(-100, 100)
    }

    // ------------------------------------------------------------------
    // 4. Social jetlag.
    // ------------------------------------------------------------------

    /**
     * |mean mid-sleep Mon-Fri - mean mid-sleep Sat-Sun| in minutes, where
     * mid-sleep is the clock-time midpoint of the night (minutes since
     * midnight, so calendar-day offsets cancel out). Null when there are
     * fewer than 2 weekday or 1 weekend mid-sleeps.
     */
    fun socialJetlagMin(nights: List<SleepNightInput>): Int? {
        val mids =
            nights.mapNotNull { night ->
                val onset = night.sleepOnsetMs
                val wake = night.wakeMs
                if (onset == null || wake == null || wake <= onset) return@mapNotNull null
                val midMin = Math.floorMod((onset + wake) / 2 / 60_000, MINUTES_PER_DAY)
                midMin to dayOfWeek(night.epochDay)
            }
        val weekday = mids.filter { (_, dow) -> dow <= 4 }.map { (mid, _) -> mid }
        val weekend = mids.filter { (_, dow) -> dow >= 5 }.map { (mid, _) -> mid }
        if (weekday.size < 2 || weekend.isEmpty()) return null
        return abs(weekday.average() - weekend.average()).roundToInt()
    }

    // ------------------------------------------------------------------
    // 5. Resting-HR baseline and z-score.
    // ------------------------------------------------------------------

    /**
     * 21-day rolling baseline over nightly resting HR, excluding today.
     * A day is admitted only when completeness > 0.7 (anti-ratchet).
     */
    fun restingHrBaseline(
        days: List<RestingHrDayInput>,
        todayEpochDay: Long,
    ): HrBaseline? {
        val window =
            days.filter {
                it.epochDay in (todayEpochDay - BASELINE_DAYS) until todayEpochDay &&
                    it.completeness > COMPLETENESS_THRESHOLD
            }
        if (window.isEmpty()) return null
        val mean = window.map { it.bpm }.average()
        val variance = window.map { (it.bpm - mean).let { d -> d * d } }.average()
        return HrBaseline(mean, sqrt(variance), window.size)
    }

    /**
     * z = (today - mean) / std, one-sided high. Positive means above
     * baseline. Null when the baseline has no spread.
     */
    fun restingHrZ(
        todayBpm: Double,
        baseline: HrBaseline,
    ): Float? {
        if (baseline.std <= 0.0) return null
        return ((todayBpm - baseline.mean) / baseline.std).toFloat()
    }

    // ------------------------------------------------------------------
    // 6. Recovery score.
    // ------------------------------------------------------------------

    /**
     * Weighted sum of favorable-positive driver z-scores, logistic squash
     * 100 / (1 + e^(-1.6 * (z + 0.2))). Weights: RHR 0.30 / sleep debt 0.20 /
     * HRV RMSSD 0.40 / breathing minutes 0.10. Null drivers are dropped and
     * the remaining weights rescaled; null when every driver is missing.
     */
    fun recoveryScore(input: RecoveryInput): Int? {
        val drivers =
            listOf(
                input.rhrZ to WEIGHT_RHR,
                input.debtZ to WEIGHT_DEBT,
                input.hrvZ to WEIGHT_HRV,
                input.breathingZ to WEIGHT_BREATHING,
            ).filter { (z, _) -> z != null }
        if (drivers.isEmpty()) return null
        val weightSum = drivers.sumOf { (_, w) -> w }
        val z = drivers.sumOf { (value, w) -> value!! * w } / weightSum
        return (100.0 / (1.0 + exp(-1.6 * (z + 0.2)))).roundToInt().coerceIn(0, 100)
    }

    /** Band for a recovery score; CALIBRATING when under 7 days of data. */
    fun recoveryBand(
        score: Int?,
        daysOfData: Int,
    ): RecoveryBand =
        when {
            daysOfData < CALIBRATION_DAYS || score == null -> RecoveryBand.CALIBRATING
            score >= 67 -> RecoveryBand.CHARGED
            score >= 40 -> RecoveryBand.COASTING
            else -> RecoveryBand.DEPLETED
        }

    // ------------------------------------------------------------------
    // 7. Trend slopes (OLS).
    // ------------------------------------------------------------------

    /** Ordinary least-squares slope of y on x. Null with < 2 points or no spread in x. */
    fun olsSlope(points: List<OlsPoint>): Float? {
        if (points.size < 2) return null
        val n = points.size.toDouble()
        val sumX = points.sumOf { it.x }
        val sumY = points.sumOf { it.y }
        val sumXX = points.sumOf { it.x * it.x }
        val sumXY = points.sumOf { it.x * it.y }
        val denominator = n * sumXX - sumX * sumX
        if (denominator == 0.0) return null
        return ((n * sumXY - sumX * sumY) / denominator).toFloat()
    }

    // ------------------------------------------------------------------
    // 8. Correlation cards (Pearson).
    // ------------------------------------------------------------------

    /** Pearson r, null when the pair is degenerate. */
    fun pearson(
        xs: List<Double>,
        ys: List<Double>,
    ): Double? {
        if (xs.size != ys.size || xs.size < 2) return null
        val meanX = xs.average()
        val meanY = ys.average()
        var numerator = 0.0
        var denomX = 0.0
        var denomY = 0.0
        for (i in xs.indices) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            numerator += dx * dy
            denomX += dx * dx
            denomY += dy * dy
        }
        val denominator = sqrt(denomX * denomY)
        if (denominator == 0.0) return null
        return (numerator / denominator).coerceIn(-1.0, 1.0)
    }

    /**
     * Aligns two day-keyed series; y is read [yOffsetDays] after x
     * (1 = next-day). Days missing on either side are dropped.
     */
    fun pairSeries(
        xByDay: Map<Long, Double>,
        yByDay: Map<Long, Double>,
        yOffsetDays: Long = 0,
    ): Pair<List<Double>, List<Double>> {
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        for ((day, x) in xByDay) {
            val y = yByDay[day + yOffsetDays] ?: continue
            xs += x
            ys += y
        }
        return xs to ys
    }

    /**
     * Emits a CORRELATION card when |r| >= 0.4 and n >= 10, else null.
     * The caller builds the plain-language title/body with the numbers.
     */
    fun correlationCard(
        r: Double,
        n: Int,
        title: String,
        body: String,
    ): InsightCard? =
        if (abs(r) >= CORRELATION_MIN_ABS_R && n >= CORRELATION_MIN_N) {
            InsightCard(title, body, InsightKind.CORRELATION)
        } else {
            null
        }

    /** Mood-derived stress proxy 0..100, higher = more stressed. Mirrors StressScoreCalculator's mood term. */
    fun stressProxyFromMood(moodScore: Int): Double = (5 - moodScore.coerceIn(1, 5)) / 4.0 * 100.0

    // ------------------------------------------------------------------
    // 10. Step dedup (the 3x-inflation fix).
    // ------------------------------------------------------------------

    /**
     * Collapses raw step/distance records to one [StepDay] per day.
     *
     * Writers duplicate rows: Samsung Health emits 2 identical step rows per
     * day per device UUID and Google Fit emits its own copy of the same
     * underlying activity, so naive sums come back ~3x inflated. Rule:
     * per (day, writer package) take max steps (identical duplicates) and sum
     * distance (that writer's non-overlapping segments); per day take the max
     * across writers (writers observe the same phone, so cross-writer sums
     * double count). Pure and unit-testable.
     */
    fun dedupeStepDays(entries: List<StepSourceEntry>): List<StepDay> {
        if (entries.isEmpty()) return emptyList()
        val perWriter =
            entries.groupBy { it.epochDay to it.packageName }.map { (key, records) ->
                val (day, _) = key
                Triple(day, records.maxOf { it.steps }, records.sumOf { it.distanceMeters })
            }
        return perWriter
            .groupBy { it.first }
            .map { (day, writers) ->
                StepDay(
                    epochDay = day,
                    steps = writers.maxOf { it.second },
                    distanceMeters = writers.maxOf { it.third }.takeIf { it > 0.0 },
                )
            }.sortedBy { it.epochDay }
    }

    // ------------------------------------------------------------------
    // 9. Rule-based cards (recovery anomalies, sleep, trends, habits).
    // ------------------------------------------------------------------

    /** Trend cards from trailing-14-day OLS slopes. */
    fun trendCards(
        rhrSlopeBpmPerDay: Float?,
        rhrPoints: Int,
        sleepSlopeMinPerDay: Float?,
        sleepPoints: Int,
    ): List<InsightCard> =
        buildList {
            if (rhrSlopeBpmPerDay != null && rhrSlopeBpmPerDay >= RHR_SLOPE_ALERT && rhrPoints >= TREND_MIN_POINTS) {
                add(
                    InsightCard(
                        title = "Resting HR is climbing",
                        body = "Up ${"%.1f".format(rhrSlopeBpmPerDay)} bpm per day over the last 2 weeks.",
                        kind = InsightKind.TREND,
                    ),
                )
            }
            val sleepAlert = sleepSlopeMinPerDay != null && sleepSlopeMinPerDay <= SLEEP_SLOPE_ALERT
            if (sleepAlert && sleepPoints >= TREND_MIN_POINTS) {
                add(
                    InsightCard(
                        title = "Sleep is shrinking",
                        body = "Down ${"%.0f".format(-sleepSlopeMinPerDay)} min per night over the last 2 weeks.",
                        kind = InsightKind.TREND,
                    ),
                )
            }
        }

    /** Rule-based cards from the latest state. */
    fun habitCards(ctx: HabitContext): List<InsightCard> =
        buildList {
            val z = ctx.rhrZ
            when {
                z != null && z >= RHR_Z_EXTREME ->
                    add(
                        InsightCard(
                            title = "Resting heart rate is unusually high",
                            body =
                                "Tonight's reading sits ${"%.1f".format(z)} standard deviations above " +
                                    "your baseline. Keep today light.",
                            kind = InsightKind.RECOVERY,
                        ),
                    )
                z != null && z >= RHR_Z_ELEVATED ->
                    add(
                        InsightCard(
                            title = "Resting heart rate is elevated",
                            body =
                                "${"%.1f".format(z)} standard deviations above your baseline. " +
                                    "An early night helps.",
                            kind = InsightKind.RECOVERY,
                        ),
                    )
            }
            if (ctx.rhrStreakNights >= RHR_STREAK_NIGHTS && ctx.rhrStreakPct != null) {
                add(
                    InsightCard(
                        title = "Resting HR is running high",
                        body =
                            "${"%.0f".format(ctx.rhrStreakPct)}% above your baseline " +
                                "${ctx.rhrStreakNights} nights running. Consider an early wind-down.",
                        kind = InsightKind.RECOVERY,
                    ),
                )
            }
            if (ctx.recoveryBand == RecoveryBand.DEPLETED && ctx.recoveryScore != null) {
                add(
                    InsightCard(
                        title = "Recovery is depleted",
                        body = "Your recovery score is ${ctx.recoveryScore}. Protect sleep tonight.",
                        kind = InsightKind.RECOVERY,
                    ),
                )
            }
            if (ctx.sleepDebtHours != null && ctx.sleepDebtHours >= SLEEP_DEBT_ALERT_HOURS) {
                add(
                    InsightCard(
                        title = "Sleep debt is climbing",
                        body =
                            "You owe ${"%.1f".format(ctx.sleepDebtHours)} hours of sleep. " +
                                "An early night pays some back.",
                        kind = InsightKind.SLEEP,
                    ),
                )
            }
            if (ctx.lastNightScore != null && ctx.lastNightScore < POOR_NIGHT_SCORE) {
                add(
                    InsightCard(
                        title = "Rough night",
                        body = "Last night scored ${ctx.lastNightScore}. Tonight is a good night to reset.",
                        kind = InsightKind.SLEEP,
                    ),
                )
            }
            if (ctx.socialJetlagMin != null && ctx.socialJetlagMin >= SOCIAL_JETLAG_ALERT_MIN) {
                add(
                    InsightCard(
                        title = "Weekend sleep shift",
                        body =
                            "Your weekend sleep midpoint runs ${formatDurationMin(ctx.socialJetlagMin)} " +
                                "later than on weekdays.",
                        kind = InsightKind.SLEEP,
                    ),
                )
            }
            if (ctx.sri != null && ctx.sri < SRI_LOW) {
                add(
                    InsightCard(
                        title = "Sleep timing is irregular",
                        body = "Your regularity index is ${ctx.sri}. A fixed wake time helps most.",
                        kind = InsightKind.HABIT,
                    ),
                )
            }
            if (ctx.breathingMinutesToday == 0) {
                add(
                    InsightCard(
                        title = "No breathing time yet today",
                        body = "Even 5 slow minutes can blunt the evening spike.",
                        kind = InsightKind.HABIT,
                    ),
                )
            }
        }

    /**
     * Priority order for the card list: RECOVERY anomalies first, then
     * SLEEP, then CORRELATION, then TREND, then HABIT.
     */
    fun sortCardsByPriority(cards: List<InsightCard>): List<InsightCard> {
        val order =
            mapOf(
                InsightKind.RECOVERY to 0,
                InsightKind.SLEEP to 1,
                InsightKind.CORRELATION to 2,
                InsightKind.TREND to 3,
                InsightKind.HABIT to 4,
            )
        return cards.sortedBy { order[it.kind] ?: 5 }
    }

    // ------------------------------------------------------------------
    // Helpers.
    // ------------------------------------------------------------------

    /**
     * Trapezoidal score: 0 at/below [zeroLow] and at/above [zeroHigh],
     * 100 across [optLow, optHigh], linear ramps between.
     */
    private fun trapezoid(
        x: Double,
        zeroLow: Double,
        optLow: Double,
        optHigh: Double,
        zeroHigh: Double,
    ): Double =
        when {
            x <= zeroLow || x >= zeroHigh -> 0.0
            x >= optLow && x <= optHigh -> 100.0
            x < optLow -> 100.0 * (x - zeroLow) / (optLow - zeroLow)
            else -> 100.0 * (zeroHigh - x) / (zeroHigh - optHigh)
        }

    /** 0 = Monday .. 6 = Sunday. */
    private fun dayOfWeek(epochDay: Long): Int = Math.floorMod(epochDay + 3, 7)

    private fun formatDurationMin(totalMin: Int): String {
        val hours = totalMin / 60
        val minutes = totalMin % 60
        return when {
            hours == 0 -> "${minutes}m"
            minutes == 0 -> "${hours}h"
            else -> "${hours}h ${minutes}m"
        }
    }

    private const val WEIGHT_DURATION = 0.30
    private const val WEIGHT_EFFICIENCY = 0.25
    private const val WEIGHT_ARCHITECTURE = 0.25
    private const val WEIGHT_CONTINUITY = 0.15
    private const val WEIGHT_LATENCY = 0.05

    private const val DEBT_WINDOW_NIGHTS = 14
    private const val DEBT_MIN_VALID_NIGHTS = 5

    private const val MINUTES_PER_DAY = 1440
    private const val SRI_MIN_DAY_PAIRS = 5

    private const val BASELINE_DAYS = 21L
    private const val COMPLETENESS_THRESHOLD = 0.7
    private const val CALIBRATION_DAYS = 7

    private const val WEIGHT_RHR = 0.30
    private const val WEIGHT_DEBT = 0.20
    private const val WEIGHT_HRV = 0.40
    private const val WEIGHT_BREATHING = 0.10

    private const val CORRELATION_MIN_ABS_R = 0.4
    private const val CORRELATION_MIN_N = 10

    private const val RHR_Z_ELEVATED = 1.5f
    private const val RHR_Z_EXTREME = 3.0f
    private const val RHR_STREAK_NIGHTS = 3
    private const val RHR_SLOPE_ALERT = 0.3f
    private const val SLEEP_SLOPE_ALERT = -5.0f
    private const val TREND_MIN_POINTS = 7
    private const val SLEEP_DEBT_ALERT_HOURS = 3.0f
    private const val POOR_NIGHT_SCORE = 55
    private const val SOCIAL_JETLAG_ALERT_MIN = 60
    private const val SRI_LOW = 60
}
