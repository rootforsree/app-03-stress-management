package com.sreecharan.stressrelief.data.reminders

import com.sreecharan.stressrelief.data.model.QuietHours
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReminderTimesTest {
    private val zone = ZoneId.systemDefault()

    private fun millisFor(
        hour: Int,
        minute: Int,
    ): Long =
        LocalDateTime
            .of(2026, 10, 15, hour, minute)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    @Test
    fun `nextDailyTrigger returns today when the time is still ahead`() {
        val now = millisFor(8, 0)
        val trigger = ReminderTimes.nextDailyTrigger(9, 0, now)

        val expected = millisFor(9, 0)
        assertEquals(expected, trigger)
    }

    @Test
    fun `nextDailyTrigger rolls to tomorrow when the time already passed`() {
        val now = millisFor(10, 0)
        val trigger = ReminderTimes.nextDailyTrigger(9, 0, now)

        val expected =
            LocalDateTime
                .of(2026, 10, 16, 9, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun `nextDailyTrigger is strictly in the future and within 24h`() {
        val now = millisFor(9, 0) // exactly on the trigger minute
        val trigger = ReminderTimes.nextDailyTrigger(9, 0, now)

        assertTrue(trigger > now, "trigger must be strictly in the future")
        assertTrue(trigger - now <= 24 * 60 * 60 * 1000L, "trigger must be within 24h")
    }

    @Test
    fun `isInQuietHours handles overnight windows`() {
        val quiet = QuietHours(startHour = 22, startMin = 0, endHour = 7, endMin = 0)

        assertTrue(ReminderTimes.isInQuietHours(millisFor(23, 30), quiet))
        assertTrue(ReminderTimes.isInQuietHours(millisFor(0, 15), quiet))
        assertTrue(ReminderTimes.isInQuietHours(millisFor(6, 59), quiet))
        assertTrue(ReminderTimes.isInQuietHours(millisFor(22, 0), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(7, 0), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(12, 0), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(21, 59), quiet))
    }

    @Test
    fun `isInQuietHours handles same-day windows`() {
        val quiet = QuietHours(startHour = 13, startMin = 0, endHour = 14, endMin = 0)

        assertTrue(ReminderTimes.isInQuietHours(millisFor(13, 0), quiet))
        assertTrue(ReminderTimes.isInQuietHours(millisFor(13, 30), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(14, 0), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(12, 59), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(23, 0), quiet))
    }

    @Test
    fun `isInQuietHours treats start equals end as no quiet hours`() {
        val quiet = QuietHours(startHour = 0, startMin = 0, endHour = 0, endMin = 0)

        assertFalse(ReminderTimes.isInQuietHours(millisFor(0, 0), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(12, 0), quiet))
        assertFalse(ReminderTimes.isInQuietHours(millisFor(23, 59), quiet))
    }

    @Test
    fun `triggerFor uses the configured time per type`() {
        val prefs =
            ReminderPreferences(
                morningHour = 7,
                morningMinute = 30,
                nudgeEnabled = false,
            )
        val now = millisFor(6, 0)

        assertEquals(millisFor(7, 30), ReminderTimes.triggerFor(ReminderType.MORNING_CHECKIN, prefs, now))
    }
}
