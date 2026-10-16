package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.health.HealthConnectClient
import com.sreecharan.stressrelief.data.health.HrvDay
import com.sreecharan.stressrelief.data.health.RestingHrDay
import com.sreecharan.stressrelief.data.health.SleepNight
import com.sreecharan.stressrelief.data.health.SleepStageType
import com.sreecharan.stressrelief.data.health.isAsleep
import com.sreecharan.stressrelief.data.local.BreathingSessionDao
import com.sreecharan.stressrelief.data.local.DailyActivityDao
import com.sreecharan.stressrelief.data.local.DailyActivityEntity
import com.sreecharan.stressrelief.data.local.JournalDao
import com.sreecharan.stressrelief.data.local.JournalEntryEntity
import com.sreecharan.stressrelief.data.local.JsonCodec
import com.sreecharan.stressrelief.data.local.MoodCheckInDao
import com.sreecharan.stressrelief.data.local.MoodCheckInEntity
import com.sreecharan.stressrelief.data.local.toDomain
import com.sreecharan.stressrelief.data.model.InsightCard
import com.sreecharan.stressrelief.data.model.InsightsData
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.Recommendation
import com.sreecharan.stressrelief.data.model.RecommendationKind
import com.sreecharan.stressrelief.data.model.RecoveryBand
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.util.TimeUtils
import com.sreecharan.stressrelief.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

@Singleton
class WellnessRepositoryImpl
    @Inject
    constructor(
        private val moodCheckInDao: MoodCheckInDao,
        private val breathingSessionDao: BreathingSessionDao,
        private val journalDao: JournalDao,
        private val dailyActivityDao: DailyActivityDao,
        private val healthConnectClient: HealthConnectClient,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : WellnessRepository {
        override fun observeMoodHistory(days: Int): Flow<List<MoodCheckIn>> {
            val dayStart = TimeUtils.startOfDayMs(System.currentTimeMillis())
            val cutoff = dayStart - (days.coerceAtLeast(1) - 1) * TimeUtils.DAY_MS
            return moodCheckInDao
                .observeSince(cutoff)
                .map { list -> list.map { it.toDomain() } }
                .flowOn(ioDispatcher)
        }

        override fun observeTodayCheckIn(): Flow<MoodCheckIn?> {
            val dayStart = TimeUtils.startOfDayMs(System.currentTimeMillis())
            return moodCheckInDao
                .observeForDay(dayStart, dayStart + TimeUtils.DAY_MS)
                .map { it.firstOrNull()?.toDomain() }
                .flowOn(ioDispatcher)
        }

        override suspend fun saveMoodCheckIn(
            score: Int,
            activities: List<String>,
            note: String,
        ) {
            withContext(ioDispatcher) {
                moodCheckInDao.insert(
                    MoodCheckInEntity(
                        timestampMs = System.currentTimeMillis(),
                        moodScore = score.coerceIn(1, 5),
                        activitiesJson = JsonCodec.encodeStrings(activities),
                        note = note,
                    ),
                )
                markActivity { copy(checkInDone = true) }
            }
        }

        override fun observeJournalEntries(): Flow<List<JournalEntry>> =
            journalDao
                .observeAll()
                .map { list -> list.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override fun observeJournalEntry(id: Long): Flow<JournalEntry?> =
            journalDao
                .observeById(id)
                .map { it?.toDomain() }
                .flowOn(ioDispatcher)

        override suspend fun saveJournalEntry(
            prompt: String,
            text: String,
        ): Long =
            withContext(ioDispatcher) {
                val id =
                    journalDao.insert(
                        JournalEntryEntity(
                            timestampMs = System.currentTimeMillis(),
                            prompt = prompt,
                            text = text,
                        ),
                    )
                markActivity { copy(journalDone = true) }
                id
            }

        override suspend fun updateJournalEntry(
            id: Long,
            text: String,
        ) {
            withContext(ioDispatcher) {
                val existing = journalDao.observeById(id).first() ?: return@withContext
                journalDao.update(existing.copy(text = text))
            }
        }

        override suspend fun deleteJournalEntry(id: Long) {
            withContext(ioDispatcher) {
                journalDao.deleteById(id)
            }
        }

        override fun observeStreakDays(): Flow<Int> =
            dailyActivityDao
                .observeLastNDays(STREAK_LOOKBACK_DAYS)
                .map { rows ->
                    StreakCalculator.computeStreak(rows.map { it.toDomain() }, TimeUtils.todayEpochDay())
                }.flowOn(ioDispatcher)

        override fun observeTotalCalmMinutes(): Flow<Int> =
            breathingSessionDao.observeTotalMinutes().flowOn(ioDispatcher)

        override suspend fun computeStressScore(): StressScore =
            withContext(ioDispatcher) {
                runCatching {
                    val moods = moodCheckInDao.observeRecent(SCORE_HISTORY_LIMIT).first()
                    val weekAgo = System.currentTimeMillis() - 7 * TimeUtils.DAY_MS
                    val recent = moods.filter { it.timestampMs >= weekAgo }
                    val avgMood = recent.map { it.moodScore }.average().takeIf { recent.isNotEmpty() }
                    val signals = healthConnectClient.readStressSignals()
                    StressScoreCalculator.compute(avgMood = avgMood, signals = signals, checkInCount = recent.size)
                }.getOrElse {
                    StressScore(
                        score = 50,
                        label = "Moderate",
                        factors = listOf("Could not compute your stress score right now"),
                    )
                }
            }

        /**
         * Builds [InsightsData] from Health Connect reads and Room history.
         * Never throws: the caller wraps it, and every external read is
         * guarded inside. Missing inputs surface as nulls and dropped
         * weights, never as zeros.
         */
        override suspend fun computeInsights(): InsightsData =
            withContext(ioDispatcher) {
                runCatching { buildInsights() }.getOrElse { InsightsData.empty() }
            }

        override suspend fun markBreathingDone(minutes: Int) {
            withContext(ioDispatcher) {
                markActivity { copy(breathingMinutes = breathingMinutes + minutes.coerceAtLeast(0)) }
            }
        }

        override suspend fun markJournalDone() {
            withContext(ioDispatcher) {
                markActivity { copy(journalDone = true) }
            }
        }

        // ------------------------------------------------------------------
        // Insights (contract section 7).
        // ------------------------------------------------------------------

        private suspend fun buildInsights(): InsightsData {
            val zone = ZoneId.systemDefault()
            val todayEpochDay = LocalDate.now(zone).toEpochDay()
            val needHours = InsightsData.SLEEP_NEED_DEFAULT_HOURS
            val calc = InsightCalculators

            val healthConnected = healthConnectClient.isAvailable() && healthConnectClient.hasAllPermissions()

            // Health Connect inputs (each read is self-guarded; empty when unavailable).
            val rawNights = if (healthConnected) healthConnectClient.readSleepNights(SLEEP_WINDOW_DAYS) else emptyList()
            val strips = if (healthConnected) healthConnectClient.readSleepStrips(SRI_WINDOW_DAYS) else emptyList()
            val rhrDays = if (healthConnected) healthConnectClient.readRestingHrDays(RHR_WINDOW_DAYS) else emptyList()
            val hrvDays = if (healthConnected) healthConnectClient.readHrvDays(HISTORY_WINDOW_DAYS) else emptyList()
            val stepDays = if (healthConnected) healthConnectClient.readStepDays(STEP_WINDOW_DAYS) else emptyList()

            // Room inputs.
            val monthAgoMs = System.currentTimeMillis() - HISTORY_WINDOW_DAYS * TimeUtils.DAY_MS
            val moods = moodCheckInDao.observeSince(monthAgoMs).first()
            val activity = dailyActivityDao.observeLastNDays(HISTORY_WINDOW_DAYS).first()
            val moodByDay = moods.groupBy { TimeUtils.startOfDayMs(it.timestampMs, zone) / TimeUtils.DAY_MS }
                .mapValues { (_, rows) -> rows.maxBy { it.timestampMs }.moodScore }
            val breathingByDay = activity.associate { it.dateEpochDay to it.breathingMinutes }
            val breathingToday = breathingByDay[todayEpochDay] ?: 0

            // Sleep nights: merge sessions per day, score each night.
            val nightInputs = mergeSleepNights(rawNights)
            val sleepNights = nightInputs.map { calc.sleepNightScore(it) }
            val debtNights = nightInputs.sortedByDescending { it.epochDay }
            val sleepDebtHours = calc.sleepDebt(debtNights, needHours)
            val sri = calc.sleepRegularityIndex(strips)
            val socialJetlag = calc.socialJetlagMin(nightInputs)
            val lastNightScore = sleepNights.maxByOrNull { it.epochDay }?.score

            // Resting HR baseline, z, trend.
            val rhrInputs = rhrDays.map { InsightCalculators.RestingHrDayInput(it.epochDay, it.bpm, it.completeness) }
            val latestRhr = rhrDays.maxByOrNull { it.epochDay }
            val rhrBaseline = calc.restingHrBaseline(rhrInputs, todayEpochDay)
            val restingHrZ =
                if (latestRhr != null && rhrBaseline != null) calc.restingHrZ(latestRhr.bpm, rhrBaseline) else null
            val rhrTrendPoints =
                rhrInputs
                    .filter { it.epochDay > todayEpochDay - TREND_WINDOW_DAYS }
                    .sortedBy { it.epochDay }
                    .mapIndexed { index, day -> InsightCalculators.OlsPoint(index.toDouble(), day.bpm) }
            val rhrTrend = calc.olsSlope(rhrTrendPoints)

            // Sleep trend (min/day).
            val sleepTrendPoints =
                nightInputs
                    .filter { it.epochDay > todayEpochDay - TREND_WINDOW_DAYS }
                    .sortedBy { it.epochDay }
                    .mapIndexedNotNull { index, night ->
                        night.totalSleepMin?.let { min ->
                            InsightCalculators.OlsPoint(index.toDouble(), min.toDouble())
                        }
                    }
            val sleepTrend = calc.olsSlope(sleepTrendPoints)

            // HRV today.
            val latestHrv = hrvDays.maxByOrNull { it.epochDay }

            // Calibration: distinct days with any health data.
            val healthDataDays =
                (rhrDays.map { it.epochDay } + nightInputs.map { it.epochDay }).toSet().size
            val calibrating = healthDataDays < CALIBRATION_DAYS

            // Recovery drivers (favorable-positive z-scores; nulls dropped + rescaled).
            val rhrDriverZ = restingHrZ?.let { -it.toDouble() }
            val debtDriverZ = sleepDebtHours?.let { (DEBT_REFERENCE_HOURS - it) / DEBT_REFERENCE_HOURS }
            val hrvDriverZ = hrvDriverZ(hrvDays, latestHrv?.epochDay)
            val breathingDriverZ = breathingDriverZ(breathingByDay, todayEpochDay, breathingToday)
            val recoveryScore =
                if (calibrating) {
                    null
                } else {
                    calc.recoveryScore(
                        InsightCalculators.RecoveryInput(
                            rhrZ = rhrDriverZ,
                            debtZ = debtDriverZ,
                            hrvZ = hrvDriverZ,
                            breathingZ = breathingDriverZ,
                        ),
                    )
                }
            val recoveryBand = calc.recoveryBand(recoveryScore, healthDataDays)

            // RHR streak: consecutive nights >= 8% above baseline, ending at the latest day.
            val (streakNights, streakPct) = rhrStreak(rhrDays, rhrBaseline)

            // Cards.
            val cards = mutableListOf<InsightCard?>()
            cards += sleepStressCorrelationCard(moodByDay, nightInputs)
            cards += breathingStressCorrelationCard(moodByDay, breathingByDay)
            cards += rhrMoodCorrelationCard(moodByDay, rhrDays)
            cards +=
                calc.trendCards(
                    rhrSlopeBpmPerDay = rhrTrend,
                    rhrPoints = rhrTrendPoints.size,
                    sleepSlopeMinPerDay = sleepTrend,
                    sleepPoints = sleepTrendPoints.size,
                )
            cards +=
                calc.habitCards(
                    InsightCalculators.HabitContext(
                        rhrZ = restingHrZ,
                        rhrStreakNights = streakNights,
                        rhrStreakPct = streakPct,
                        recoveryBand = recoveryBand,
                        recoveryScore = recoveryScore,
                        sleepDebtHours = sleepDebtHours,
                        lastNightScore = lastNightScore,
                        sri = sri,
                        socialJetlagMin = socialJetlag,
                        breathingMinutesToday = breathingToday,
                    ),
                )
            val insightCards = calc.sortCardsByPriority(cards.filterNotNull())

            // Steps: trailing-7-day window for the chart; today vs average.
            val stepsWeek = stepDays.takeLast(STEP_WINDOW_DAYS)
            val stepsToday = stepsWeek.find { it.epochDay == todayEpochDay }?.steps
            val steps7DayAvg = stepsWeek.map { it.steps }.takeIf { it.isNotEmpty() }?.average()
            val distanceTodayM = stepsWeek.find { it.epochDay == todayEpochDay }?.distanceMeters

            val recommendations =
                buildRecommendations(
                    sleepDebtHours = sleepDebtHours,
                    sleepNeedHours = needHours,
                    nightInputs = nightInputs,
                    lastNightScore = lastNightScore,
                    stepsToday = stepsToday,
                    steps7DayAvg = steps7DayAvg,
                    restingHrZ = restingHrZ,
                    latestRhrBpm = latestRhr?.bpm,
                    rhrBaselineMean = rhrBaseline?.mean,
                    recoveryBand = recoveryBand,
                    recoveryScore = recoveryScore,
                    breathingToday = breathingToday,
                )

            return InsightsData(
                sleepNights = sleepNights,
                sleepDebtHours = sleepDebtHours,
                sleepNeedHours = needHours,
                sri = sri,
                socialJetlagMin = socialJetlag,
                restingHrToday = latestRhr?.bpm?.roundToInt(),
                restingHrBaseline = rhrBaseline?.mean?.toFloat(),
                restingHrZ = restingHrZ,
                hrvRmssdToday = latestHrv?.rmssdMs?.toFloat(),
                recoveryScore = recoveryScore,
                recoveryBand = recoveryBand,
                restingHrTrendBpmPerDay = rhrTrend,
                sleepTrendMinPerDay = sleepTrend,
                insightCards = insightCards,
                recommendations = recommendations,
                stepDays = stepsWeek,
                stepsToday = stepsToday,
                steps7DayAvg = steps7DayAvg,
                distanceTodayM = distanceTodayM,
                calibrating = calibrating,
                healthConnected = healthConnected,
            )
        }

        /**
         * Merges Health Connect sessions into one input per night (keyed by
         * the day the session ended). Derives TST, TIB, deep/REM, WASO,
         * awakenings, latency, onset and wake from the stage timeline;
         * sessions without stages keep TIB but leave TST null (never guessed).
         */
        private fun mergeSleepNights(nights: List<SleepNight>): List<InsightCalculators.SleepNightInput> =
            nights
                .groupBy { it.epochDay }
                .map { (epochDay, sessions) ->
                    val segments = sessions.flatMap { it.stages }.sortedBy { it.startMs }
                    val asleepSegments = segments.filter { it.type.isAsleep() }
                    val tstMin =
                        asleepSegments
                            .takeIf { it.isNotEmpty() }
                            ?.sumOf { it.endMs - it.startMs }
                            ?.let { (it / 60_000).toInt() }
                    val tibMin =
                        sessions
                            .sumOf { session ->
                                val outOfBed =
                                    session.stages.filter { it.type == SleepStageType.OUT_OF_BED }
                                        .sumOf { it.endMs - it.startMs }
                                (session.sessionEndMs - session.sessionStartMs - outOfBed).coerceAtLeast(0L)
                            }.let { (it / 60_000).toInt() }.takeIf { it > 0 }
                    val deepMin =
                        segments.filter { it.type == SleepStageType.DEEP }
                            .takeIf { it.isNotEmpty() }
                            ?.sumOf { it.endMs - it.startMs }
                            ?.let { (it / 60_000).toInt() }
                    val remMin =
                        segments.filter { it.type == SleepStageType.REM }
                            .takeIf { it.isNotEmpty() }
                            ?.sumOf { it.endMs - it.startMs }
                            ?.let { (it / 60_000).toInt() }

                    val firstSleepMs = asleepSegments.minOfOrNull { it.startMs }
                    val lastSleepEndMs = asleepSegments.maxOfOrNull { it.endMs }
                    val latencyMin =
                        if (firstSleepMs != null) {
                            val sessionStart = sessions.minOf { it.sessionStartMs }
                            ((firstSleepMs - sessionStart) / 60_000).toInt().takeIf { it >= 0 }
                        } else {
                            null
                        }
                    val (wasoMin, awakenings) =
                        if (firstSleepMs != null && lastSleepEndMs != null) {
                            val nightAwake =
                                segments.filter {
                                    it.type == SleepStageType.AWAKE &&
                                        it.startMs >= firstSleepMs && it.endMs <= lastSleepEndMs
                                }
                            val waso = nightAwake.sumOf { it.endMs - it.startMs }.let { (it / 60_000).toInt() }
                            waso to nightAwake.size.takeIf { nightAwake.isNotEmpty() }
                        } else {
                            null to null
                        }

                    InsightCalculators.SleepNightInput(
                        epochDay = epochDay,
                        totalSleepMin = tstMin,
                        timeInBedMin = tibMin,
                        deepMin = deepMin,
                        remMin = remMin,
                        wasoMin = wasoMin,
                        awakenings = awakenings,
                        latencyMin = latencyMin,
                        sleepOnsetMs = firstSleepMs ?: sessions.minOfOrNull { it.sessionStartMs },
                        wakeMs = sessions.maxOfOrNull { it.sessionEndMs },
                    )
                }.sortedBy { it.epochDay }

        /** HRV driver: favorable-positive z of the latest RMSSD vs its trailing baseline. */
        private fun hrvDriverZ(
            hrvDays: List<HrvDay>,
            latestDay: Long?,
        ): Double? {
            if (latestDay == null) return null
            val latest = hrvDays.find { it.epochDay == latestDay } ?: return null
            val baselineDays = hrvDays.filter { it.epochDay in (latestDay - 21) until latestDay }.takeLast(21)
            if (baselineDays.isEmpty()) return null
            val mean = baselineDays.map { it.rmssdMs }.average()
            val std = sqrt(baselineDays.map { (it.rmssdMs - mean).let { d -> d * d } }.average())
            if (std <= 0.0) return null
            return (latest.rmssdMs - mean) / std
        }

        /** Breathing driver: favorable-positive z of today's minutes vs trailing 14-day baseline. */
        private fun breathingDriverZ(
            breathingByDay: Map<Long, Int>,
            todayEpochDay: Long,
            todayMinutes: Int,
        ): Double? {
            val baselineDays =
                (1..14).mapNotNull { back -> breathingByDay[todayEpochDay - back]?.toDouble() }
            if (baselineDays.isEmpty()) return null
            val mean = baselineDays.average()
            val std = sqrt(baselineDays.map { (it - mean).let { d -> d * d } }.average())
            if (std <= 0.0) return null
            return (todayMinutes - mean) / std
        }

        /**
         * Consecutive nights ending at the latest RHR day where bpm ran at
         * least 8% above the baseline mean. Returns (streak length, latest pct).
         */
        private fun rhrStreak(
            rhrDays: List<RestingHrDay>,
            baseline: InsightCalculators.HrBaseline?,
        ): Pair<Int, Double?> {
            if (baseline == null || baseline.mean <= 0.0) return 0 to null
            val byDay = rhrDays.associate { it.epochDay to it.bpm }
            val latestDay = rhrDays.maxOfOrNull { it.epochDay } ?: return 0 to null
            var streak = 0
            var day = latestDay
            while (true) {
                val bpm = byDay[day] ?: break
                if ((bpm - baseline.mean) / baseline.mean < RHR_STREAK_PCT) break
                streak++
                day--
            }
            val pct = byDay[latestDay]?.let { (it - baseline.mean) / baseline.mean * 100.0 }
            return streak to pct
        }

        /**
         * Sleep duration (day d) -> next-day stress proxy from the mood
         * check-in (day d+1). The mood-derived proxy mirrors
         * StressScoreCalculator's mood term; no stored stress history exists.
         */
        private fun sleepStressCorrelationCard(
            moodByDay: Map<Long, Int>,
            nightInputs: List<InsightCalculators.SleepNightInput>,
        ): InsightCard? {
            val calc = InsightCalculators
            val sleepByDay =
                nightInputs.mapNotNull { it.totalSleepMin?.let { min -> it.epochDay to min / 60.0 } }.toMap()
            val stressByDay = moodByDay.mapValues { (_, mood) -> calc.stressProxyFromMood(mood) }
            val (xs, ys) = calc.pairSeries(sleepByDay, stressByDay, yOffsetDays = 1)
            val r = calc.pearson(xs, ys) ?: return null
            if (xs.size < 10 || abs(r) < 0.4) return null
            val shortNights = xs.zip(ys).filter { (hours, _) -> hours < 6.0 }.map { (_, stress) -> stress }
            val longNights = xs.zip(ys).filter { (hours, _) -> hours >= 7.0 }.map { (_, stress) -> stress }
            val body =
                if (shortNights.isNotEmpty() && longNights.isNotEmpty()) {
                    val delta = shortNights.average() - longNights.average()
                    "On nights you sleep under 6h, next-day stress averages ${"%.0f".format(delta)} points " +
                        "higher (r = ${"%.2f".format(r)})."
                } else {
                    "Sleep duration and next-day stress move together (r = ${"%.2f".format(r)})."
                }
            return calc.correlationCard(r, xs.size, "Short nights, rough days", body)
        }

        /** Breathing minutes (day d) -> stress proxy (day d). */
        private fun breathingStressCorrelationCard(
            moodByDay: Map<Long, Int>,
            breathingByDay: Map<Long, Int>,
        ): InsightCard? {
            val calc = InsightCalculators
            val breathByDay = breathingByDay.mapValues { (_, min) -> min.toDouble() }
            val stressByDay = moodByDay.mapValues { (_, mood) -> calc.stressProxyFromMood(mood) }
            val (xs, ys) = calc.pairSeries(breathByDay, stressByDay)
            val r = calc.pearson(xs, ys) ?: return null
            if (xs.size < 10 || abs(r) < 0.4) return null
            val withBreath = xs.zip(ys).filter { (min, _) -> min >= 10.0 }.map { (_, stress) -> stress }
            val without = xs.zip(ys).filter { (min, _) -> min < 10.0 }.map { (_, stress) -> stress }
            val body =
                if (withBreath.isNotEmpty() && without.isNotEmpty()) {
                    val delta = without.average() - withBreath.average()
                    "On days with 10+ breathing minutes, stress averages ${"%.0f".format(delta)} points " +
                        "lower (r = ${"%.2f".format(r)})."
                } else {
                    "Breathing minutes and stress move together (r = ${"%.2f".format(r)})."
                }
            return calc.correlationCard(r, xs.size, "Breathing days feel lighter", body)
        }

        /** Resting HR (day d) -> next-day mood (day d+1). */
        private fun rhrMoodCorrelationCard(
            moodByDay: Map<Long, Int>,
            rhrDays: List<RestingHrDay>,
        ): InsightCard? {
            val calc = InsightCalculators
            val rhrByDay = rhrDays.associate { it.epochDay to it.bpm }
            val moodDoubleByDay = moodByDay.mapValues { (_, mood) -> mood.toDouble() }
            val (xs, ys) = calc.pairSeries(rhrByDay, moodDoubleByDay, yOffsetDays = 1)
            val r = calc.pearson(xs, ys) ?: return null
            if (xs.size < 10 || abs(r) < 0.4) return null
            val body =
                if (r < 0) {
                    "When resting HR runs high, next-day mood averages lower (r = ${"%.2f".format(r)})."
                } else {
                    "Resting HR and next-day mood move together (r = ${"%.2f".format(r)})."
                }
            return calc.correlationCard(r, xs.size, "Heart rate and mood", body)
        }

        /**
         * Actionable "do this today" items, each tied to a visible number.
         * Priority-ordered; the UI shows the top 4.
         */
        private fun buildRecommendations(
            sleepDebtHours: Float?,
            sleepNeedHours: Float,
            nightInputs: List<InsightCalculators.SleepNightInput>,
            lastNightScore: Int?,
            stepsToday: Long?,
            steps7DayAvg: Double?,
            restingHrZ: Float?,
            latestRhrBpm: Double?,
            rhrBaselineMean: Double?,
            recoveryBand: RecoveryBand,
            recoveryScore: Int?,
            breathingToday: Int,
        ): List<Recommendation> {
            val zone = ZoneId.systemDefault()
            val recs = mutableListOf<Pair<Int, Recommendation>>()

            if (sleepDebtHours != null && sleepDebtHours >= RECOMMEND_DEBT_HOURS) {
                val usualWakeMs =
                    nightInputs.mapNotNull { it.wakeMs }.takeIf { it.isNotEmpty() }?.average()?.toLong()
                val bedtimeText =
                    if (usualWakeMs != null) {
                        val target = usualWakeMs - (sleepNeedHours * 3_600_000).toLong()
                        "Lights out by ${formatClock(target, zone)} covers ${"%.1f".format(sleepNeedHours)}h " +
                            "before your usual ${formatClock(usualWakeMs, zone)} wake-up."
                    } else {
                        "An early night tonight pays some of it back."
                    }
                recs +=
                    0 to
                        Recommendation(
                            title = "Pay back sleep debt",
                            body = "You owe ${"%.1f".format(sleepDebtHours)}h of sleep. $bedtimeText",
                            metricLabel = "Sleep debt",
                            metricValue = "${"%.1f".format(sleepDebtHours)} h",
                            kind = RecommendationKind.SLEEP,
                        )
            }

            if (restingHrZ != null && restingHrZ >= RECOMMEND_RHR_Z && latestRhrBpm != null && rhrBaselineMean != null &&
                rhrBaselineMean > 0
            ) {
                val pct = ((latestRhrBpm - rhrBaselineMean) / rhrBaselineMean * 100).roundToInt()
                recs +=
                    1 to
                        Recommendation(
                            title = "Keep today light",
                            body =
                                "Resting HR is $pct% above your baseline " +
                                    "(${latestRhrBpm.roundToInt()} vs ${rhrBaselineMean.roundToInt()} bpm). " +
                                    "Skip the hard workout.",
                            metricLabel = "Resting HR",
                            metricValue = "${latestRhrBpm.roundToInt()} bpm",
                            kind = RecommendationKind.RECOVERY,
                        )
            }

            if (stepsToday != null && steps7DayAvg != null && steps7DayAvg >= RECOMMEND_MIN_AVG_STEPS) {
                if (stepsToday < steps7DayAvg * RECOMMEND_LOW_DAY_FRACTION) {
                    recs +=
                        2 to
                            Recommendation(
                                title = "Move more today",
                                body =
                                    "${formatSteps(stepsToday)} steps so far vs your " +
                                        "${formatSteps(steps7DayAvg.roundToLong())} daily average. " +
                                        "A brisk 20-minute walk adds roughly 2,000.",
                                metricLabel = "Steps today",
                                metricValue = formatSteps(stepsToday),
                                kind = RecommendationKind.ACTIVITY,
                            )
                }
            }

            if (steps7DayAvg != null && steps7DayAvg < RECOMMEND_LOW_AVG_STEPS && steps7DayAvg >= RECOMMEND_MIN_AVG_STEPS) {
                recs +=
                    3 to
                        Recommendation(
                            title = "Raise the baseline",
                            body =
                                "Your 7-day average is ${formatSteps(steps7DayAvg.roundToLong())} steps. " +
                                    "7,000 a day is a solid first target.",
                            metricLabel = "7-day average",
                            metricValue = formatSteps(steps7DayAvg.roundToLong()),
                            kind = RecommendationKind.ACTIVITY,
                        )
            }

            if (lastNightScore != null && lastNightScore < RECOMMEND_POOR_NIGHT) {
                recs +=
                    4 to
                        Recommendation(
                            title = "Reset tonight",
                            body = "Last night scored $lastNightScore/100. Wind down 30 minutes earlier than usual.",
                            metricLabel = "Last night",
                            metricValue = "$lastNightScore/100",
                            kind = RecommendationKind.SLEEP,
                        )
            }

            if (recoveryBand == RecoveryBand.DEPLETED && recoveryScore != null) {
                recs +=
                    5 to
                        Recommendation(
                            title = "Protect recovery",
                            body = "Recovery is $recoveryScore/100 and depleted. Prioritize sleep over training today.",
                            metricLabel = "Recovery",
                            metricValue = "$recoveryScore/100",
                            kind = RecommendationKind.RECOVERY,
                        )
            }

            if (breathingToday == 0) {
                recs +=
                    6 to
                        Recommendation(
                            title = "Take 5 slow minutes",
                            body = "No breathing time yet today. A 5-minute session now blunts the evening spike.",
                            metricLabel = "Breathing today",
                            metricValue = "0 min",
                            kind = RecommendationKind.BREATHING,
                        )
            }

            return recs.sortedBy { it.first }.take(RECOMMEND_MAX).map { it.second }
        }

        private fun formatClock(
            epochMs: Long,
            zone: ZoneId,
        ): String {
            val t = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
            val hour12 =
                when {
                    t.hour == 0 -> 12
                    t.hour > 12 -> t.hour - 12
                    else -> t.hour
                }
            val ampm = if (t.hour < 12) "AM" else "PM"
            return "$hour12:${"%02d".format(t.minute)} $ampm"
        }

        private fun formatSteps(steps: Long): String = "%,d".format(steps)

        companion object {
            private const val STREAK_LOOKBACK_DAYS = 730
            private const val SCORE_HISTORY_LIMIT = 200
            private const val SLEEP_WINDOW_DAYS = 14
            private const val SRI_WINDOW_DAYS = 9
            private const val RHR_WINDOW_DAYS = 22
            private const val HISTORY_WINDOW_DAYS = 30
            private const val TREND_WINDOW_DAYS = 14L
            private const val CALIBRATION_DAYS = 7
            private const val DEBT_REFERENCE_HOURS = 2.0
            private const val RHR_STREAK_PCT = 0.08
            private const val STEP_WINDOW_DAYS = 7
            private const val RECOMMEND_MAX = 4
            private const val RECOMMEND_DEBT_HOURS = 2.0f
            private const val RECOMMEND_RHR_Z = 1.5f
            private const val RECOMMEND_MIN_AVG_STEPS = 500.0
            private const val RECOMMEND_LOW_AVG_STEPS = 5000.0
            private const val RECOMMEND_LOW_DAY_FRACTION = 0.5
            private const val RECOMMEND_POOR_NIGHT = 55
        }

        private suspend fun markActivity(transform: DailyActivityEntity.() -> DailyActivityEntity) {
            val today = TimeUtils.todayEpochDay()
            val existing = dailyActivityDao.getDay(today)
            val updated =
                existing?.transform()
                    ?: DailyActivityEntity(
                        dateEpochDay = today,
                        checkInDone = false,
                        breathingMinutes = 0,
                        journalDone = false,
                    ).transform()
            dailyActivityDao.upsert(updated)
        }
    }
