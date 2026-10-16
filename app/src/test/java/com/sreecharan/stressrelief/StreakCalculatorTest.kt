package com.sreecharan.stressrelief

import com.sreecharan.stressrelief.data.model.DailyActivity
import com.sreecharan.stressrelief.data.repository.StreakCalculator
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Pure-JVM tests for streak computation. No Android framework needed. */
class StreakCalculatorTest {
    private val today = 20_000L

    private fun day(
        offset: Long,
        active: Boolean = true,
    ) = DailyActivity(
        dateEpochDay = today - offset,
        checkInDone = active,
        breathingMinutes = if (active) 5 else 0,
        journalDone = false,
    )

    @Test
    fun `consecutive active days count`() {
        val rows = listOf(day(0), day(1), day(2))

        assertEquals(3, StreakCalculator.computeStreak(rows, today))
    }

    @Test
    fun `gap breaks the streak`() {
        val rows = listOf(day(0), day(1), day(3), day(4))

        assertEquals(2, StreakCalculator.computeStreak(rows, today))
    }

    @Test
    fun `missing today does not break the streak`() {
        val rows = listOf(day(1), day(2))

        assertEquals(2, StreakCalculator.computeStreak(rows, today))
    }

    @Test
    fun `empty activity is zero`() {
        assertEquals(0, StreakCalculator.computeStreak(emptyList(), today))
    }

    @Test
    fun `inactive row does not count as activity`() {
        val rows = listOf(day(0, active = false), day(1))

        assertEquals(1, StreakCalculator.computeStreak(rows, today))
    }

    @Test
    fun `single breathing minute counts`() {
        val rows = listOf(DailyActivity(today, checkInDone = false, breathingMinutes = 1, journalDone = false))

        assertEquals(1, StreakCalculator.computeStreak(rows, today))
    }

    @Test
    fun `streak starts at yesterday when today is inactive`() {
        val rows = listOf(day(0, active = false), day(1), day(2), day(3))

        assertEquals(3, StreakCalculator.computeStreak(rows, today))
    }
}
