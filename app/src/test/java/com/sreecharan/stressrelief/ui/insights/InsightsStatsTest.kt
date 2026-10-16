package com.sreecharan.stressrelief.ui.insights

import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.SleepSession
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InsightsStatsTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 15)

    private fun ms(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun checkIn(
        id: Long,
        day: LocalDate,
        score: Int,
        activities: List<String> = emptyList(),
    ) = MoodCheckIn(id, ms(day), score, activities, "")

    @Test
    fun `daily series averages multiple check-ins and nulls empty days`() {
        val series =
            InsightsStats.dailySeries(
                listOf(
                    checkIn(1, today, 4),
                    checkIn(2, today, 2),
                    checkIn(3, today.minusDays(2), 5),
                ),
                days = 3,
                today = today,
                zone = zone,
            )
        assertEquals(3, series.size)
        assertEquals(5.0, series[0]?.average)
        assertNull(series[1], "yesterday had no check-in")
        assertEquals(3.0, series[2]?.average, "today averages 4 and 2")
    }

    @Test
    fun `mood by activity sorts best first`() {
        val ranked =
            InsightsStats.moodByActivity(
                listOf(
                    checkIn(1, today, 2, listOf("work")),
                    checkIn(2, today, 4, listOf("work")),
                    checkIn(3, today, 5, listOf("exercise")),
                ),
            )
        assertEquals("exercise", ranked[0].tag)
        assertEquals(5.0, ranked[0].average)
        assertEquals("work", ranked[1].tag)
        assertEquals(3.0, ranked[1].average)
        assertEquals(2, ranked[1].count)
    }

    @Test
    fun `trend delta is positive when recent mood climbs`() {
        val series =
            (0 until 10).map { back ->
                val score = if (back < 5) 4 else 2
                DailyMood(today.minusDays(back.toLong()), score.toDouble())
            }.reversed()
        val delta = InsightsStats.trendDelta(series)
        assertNotNull(delta)
        assertTrue(delta > 0, "recent 4s beat older 2s")
        assertEquals(2.0, delta, 0.001)
    }

    @Test
    fun `trend delta needs at least four data points`() {
        val series =
            listOf(
                DailyMood(today.minusDays(1), 2.0),
                null,
                DailyMood(today, 5.0),
            )
        assertNull(InsightsStats.trendDelta(series))
    }

    @Test
    fun `month cells form a 42-cell monday-first grid with mood fractions`() {
        // October 2026: the 1st is a Thursday, so 3 leading blanks.
        val month = YearMonth.of(2026, 10)
        val cells =
            InsightsStats.monthCells(
                listOf(
                    checkIn(1, LocalDate.of(2026, 10, 1), 1),
                    checkIn(2, LocalDate.of(2026, 10, 31), 5),
                ),
                month,
                zone,
            )
        assertEquals(42, cells.size)
        assertEquals(listOf(null, null, null), cells.take(3).map { it.dayOfMonth })
        assertEquals(1, cells[3].dayOfMonth)
        assertEquals(0.0, cells[3].moodFraction, "mood 1 maps to 0.0")
        val last = cells.first { it.dayOfMonth == 31 }
        assertEquals(1.0, last.moodFraction, "mood 5 maps to 1.0")
        val mid = cells.first { it.dayOfMonth == 15 }
        assertNull(mid.moodFraction, "no check-in means no color")
    }

    @Test
    fun `sleep insight compares check-in days with and without sleep`() {
        val insight =
            InsightsStats.sleepInsight(
                checkIns =
                    listOf(
                        checkIn(1, today, 5),
                        checkIn(2, today.minusDays(1), 2),
                    ),
                sessions =
                    listOf(
                        SleepSession(1, ms(today.minusDays(1)), 420, listOf("rain")),
                    ),
                today = today,
                zone = zone,
            )
        assertEquals(2.0, insight.averageWithSleep)
        assertEquals(5.0, insight.averageWithoutSleep)
        assertEquals(1, insight.sessions)
    }

    @Test
    fun `consistency counts active days as a percentage`() {
        val pct =
            InsightsStats.consistencyPct(
                listOf(
                    checkIn(1, today, 4),
                    checkIn(2, today.minusDays(1), 4),
                    checkIn(3, today.minusDays(40), 4),
                ),
                days = 10,
                today = today,
                zone = zone,
            )
        assertEquals(20, pct)
    }
}
