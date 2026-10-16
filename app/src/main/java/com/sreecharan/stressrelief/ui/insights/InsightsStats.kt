package com.sreecharan.stressrelief.ui.insights

import androidx.compose.runtime.Immutable
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.SleepSession
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@Immutable
data class DailyMood(
    val date: LocalDate,
    val average: Double,
)

@Immutable
data class ActivityMood(
    val tag: String,
    val average: Double,
    val count: Int,
)

@Immutable
data class SleepMoodInsight(
    val averageWithSleep: Double?,
    val averageWithoutSleep: Double?,
    val sessions: Int,
)

@Immutable
data class MonthCell(
    /** Null for padding cells before the 1st of the month. */
    val dayOfMonth: Int?,
    /** Average mood normalized to 0..1; null when nothing was logged that day. */
    val moodFraction: Double?,
)

/** Pure insight computations over mood check-ins and sleep sessions. Unit tested. */
object InsightsStats {
    const val TREND_DAYS = 30

    private fun dateOf(timestampMs: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()

    /** Average mood per day for the last [days] days, oldest first. Null = no check-in. */
    fun dailySeries(
        checkIns: List<MoodCheckIn>,
        days: Int = TREND_DAYS,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<DailyMood?> {
        val byDay = checkIns.groupBy { dateOf(it.timestampMs, zone) }
        return (days - 1 downTo 0).map { back ->
            val day = today.minusDays(back.toLong())
            val scores = byDay[day]?.map { it.moodScore }
            if (scores.isNullOrEmpty()) {
                null
            } else {
                DailyMood(day, scores.average())
            }
        }
    }

    /** Average mood per activity tag, best first. */
    fun moodByActivity(checkIns: List<MoodCheckIn>): List<ActivityMood> =
        checkIns
            .flatMap { checkIn -> checkIn.activities.map { tag -> tag to checkIn.moodScore } }
            .groupBy({ it.first }, { it.second })
            .map { (tag, scores) -> ActivityMood(tag, scores.average(), scores.size) }
            .sortedByDescending { it.average }

    /**
     * Trend delta: average of the most recent half of non-null days minus the average
     * of the older half. Positive means mood is climbing. Null when there is not
     * enough data.
     */
    fun trendDelta(series: List<DailyMood?>): Double? {
        val points = series.filterNotNull().map { it.average }
        if (points.size < 4) return null
        val half = points.size / 2
        val older = points.take(half).average()
        val newer = points.drop(points.size - half).average()
        return newer - older
    }

    /**
     * Year-in-Pixels month grid: 42 cells (6 weeks, Monday first). Mood is normalized
     * to 0..1 across the 1..5 scale.
     */
    fun monthCells(
        checkIns: List<MoodCheckIn>,
        month: YearMonth = YearMonth.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<MonthCell> {
        val byDay = checkIns.groupBy { dateOf(it.timestampMs, zone) }
        val first = month.atDay(1)
        // Monday-first offset: Monday = 0 .. Sunday = 6.
        val leadingBlanks = (first.dayOfWeek.value - 1) % 7
        val cells = MutableList(leadingBlanks) { MonthCell(null, null) }
        (1..month.lengthOfMonth()).forEach { day ->
            val date = month.atDay(day)
            val scores = byDay[date]?.map { it.moodScore }
            val fraction = scores?.average()?.let { (it - 1) / 4.0 }?.coerceIn(0.0, 1.0)
            cells.add(MonthCell(day, fraction))
        }
        while (cells.size % 7 != 0) cells.add(MonthCell(null, null))
        while (cells.size < 42) cells.add(MonthCell(null, null))
        return cells
    }

    /**
     * Compares average mood on days with at least one logged sleep session against
     * days without. Only days that have a mood check-in count.
     */
    fun sleepInsight(
        checkIns: List<MoodCheckIn>,
        sessions: List<SleepSession>,
        days: Int = TREND_DAYS,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): SleepMoodInsight {
        val cutoff = today.minusDays(days.toLong())
        val sleepDays = sessions.map { dateOf(it.startMs, zone) }.toSet()
        val recent = checkIns.filter { dateOf(it.timestampMs, zone).isAfter(cutoff) }
        val withSleep = recent.filter { dateOf(it.timestampMs, zone) in sleepDays }
        val withoutSleep = recent.filter { dateOf(it.timestampMs, zone) !in sleepDays }
        return SleepMoodInsight(
            averageWithSleep = withSleep.map { it.moodScore }.averageOrNull(),
            averageWithoutSleep = withoutSleep.map { it.moodScore }.averageOrNull(),
            sessions = sessions.count { dateOf(it.startMs, zone).isAfter(cutoff) },
        )
    }

    /** Percentage of the last [days] days with at least one check-in. */
    fun consistencyPct(
        checkIns: List<MoodCheckIn>,
        days: Int = TREND_DAYS,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        if (days <= 0) return 0
        val cutoff = today.minusDays(days.toLong())
        val activeDays = checkIns.map { dateOf(it.timestampMs, zone) }.filter { it.isAfter(cutoff) }.toSet()
        return ((activeDays.size / days.toDouble()) * 100).toInt().coerceIn(0, 100)
    }

    private fun List<Int>.averageOrNull(): Double? = if (isEmpty()) null else average()
}
