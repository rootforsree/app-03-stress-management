package com.sreecharan.stressrelief.data.reminders

/**
 * Persisted reminder configuration. Quiet hours themselves live in
 * SettingsRepository (contract section 2); these are the per-type times.
 */
data class ReminderPreferences(
    val morningEnabled: Boolean = true,
    val morningHour: Int = ReminderType.MORNING_CHECKIN.defaultHour,
    val morningMinute: Int = ReminderType.MORNING_CHECKIN.defaultMinute,
    val nudgeEnabled: Boolean = true,
    val nudgeHour: Int = ReminderType.STREAK_NUDGE.defaultHour,
    val nudgeMinute: Int = ReminderType.STREAK_NUDGE.defaultMinute,
    val windDownEnabled: Boolean = true,
    val windDownHour: Int = ReminderType.EVENING_WINDDOWN.defaultHour,
    val windDownMinute: Int = ReminderType.EVENING_WINDDOWN.defaultMinute,
) {
    fun timeFor(type: ReminderType): Pair<Int, Int> =
        when (type) {
            ReminderType.MORNING_CHECKIN -> morningHour to morningMinute
            ReminderType.STREAK_NUDGE -> nudgeHour to nudgeMinute
            ReminderType.EVENING_WINDDOWN -> windDownHour to windDownMinute
        }

    fun isEnabled(type: ReminderType): Boolean =
        when (type) {
            ReminderType.MORNING_CHECKIN -> morningEnabled
            ReminderType.STREAK_NUDGE -> nudgeEnabled
            ReminderType.EVENING_WINDDOWN -> windDownEnabled
        }
}
