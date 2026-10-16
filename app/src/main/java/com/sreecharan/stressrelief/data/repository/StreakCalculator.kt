package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.model.DailyActivity

/**
 * Pure streak math: consecutive calendar days with any activity
 * (check-in, breathing, or journaling). A missing today does not break the
 * streak as long as yesterday had activity.
 */
object StreakCalculator {
    fun computeStreak(
        activities: List<DailyActivity>,
        todayEpochDay: Long,
    ): Int {
        val byDay = activities.associateBy { it.dateEpochDay }
        var streak = 0
        var day = todayEpochDay
        if (!byDay[day].isActiveDay()) day -= 1
        while (byDay[day].isActiveDay()) {
            streak++
            day--
        }
        return streak
    }

    private fun DailyActivity?.isActiveDay(): Boolean =
        this != null && (checkInDone || breathingMinutes > 0 || journalDone)
}
