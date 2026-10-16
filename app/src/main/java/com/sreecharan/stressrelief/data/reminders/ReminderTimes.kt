package com.sreecharan.stressrelief.data.reminders

import com.sreecharan.stressrelief.data.model.QuietHours
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure reminder scheduling math. No Android framework, fully unit-testable.
 *
 * Quiet hours are honored at fire time: [isInQuietHours] tells the receiver
 * to suppress a notification whose trigger landed inside the quiet window.
 * Suppressed reminders are NOT shifted; the next daily occurrence fires
 * normally. Times are in the device timezone's wall clock.
 */
object ReminderTimes {
    /**
     * Next wall-clock occurrence of (hour, minute), strictly in the future.
     */
    fun nextDailyTrigger(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val zone = ZoneId.systemDefault()
        var next =
            LocalDateTime
                .ofInstant(Instant.ofEpochMilli(nowMillis), zone)
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)
        if (next.atZone(zone).toInstant().toEpochMilli() <= nowMillis) {
            next = next.plusDays(1)
        }
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * True when [nowMillis] falls inside the quiet-hours window. Handles the
     * overnight wrap (e.g. 22:00 -> 07:00) and the degenerate case where
     * start == end (treated as "no quiet hours").
     */
    fun isInQuietHours(
        nowMillis: Long,
        quietHours: QuietHours,
    ): Boolean {
        val start = quietHours.startHour * 60 + quietHours.startMin
        val end = quietHours.endHour * 60 + quietHours.endMin
        if (start == end) return false
        val now = minutesSinceMidnight(nowMillis)
        return if (start < end) {
            now in start until end
        } else {
            now >= start || now < end
        }
    }

    /** Minutes since local midnight for [millis]. */
    fun minutesSinceMidnight(millis: Long = System.currentTimeMillis()): Int {
        val zone = ZoneId.systemDefault()
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
        return local.hour * 60 + local.minute
    }

    /**
     * Trigger epoch millis for [type] from [prefs] at [nowMillis].
     */
    fun triggerFor(
        type: ReminderType,
        prefs: ReminderPreferences,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val (hour, minute) = prefs.timeFor(type)
        return nextDailyTrigger(hour, minute, nowMillis)
    }
}
