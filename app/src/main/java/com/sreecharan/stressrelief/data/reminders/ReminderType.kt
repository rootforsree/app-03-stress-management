package com.sreecharan.stressrelief.data.reminders

/**
 * The three reminder types Aura schedules. Each has a fixed sensible default
 * time; the user can change times and toggles from Settings (Builder 1).
 */
enum class ReminderType(
    val defaultHour: Int,
    val defaultMinute: Int,
) {
    /** Morning prompt to log how the day starts. */
    MORNING_CHECKIN(9, 0),

    /** Evening nudge when no activity happened today (streak at risk). */
    STREAK_NUDGE(18, 0),

    /** Bedtime wind-down prompt to open a sleep session. */
    EVENING_WINDDOWN(21, 30),
}
