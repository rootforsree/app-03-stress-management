package com.sreecharan.stressrelief.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.data.reminders.ReminderPreferences
import com.sreecharan.stressrelief.data.reminders.ReminderRepository
import com.sreecharan.stressrelief.data.reminders.ReminderTimes
import com.sreecharan.stressrelief.data.reminders.ReminderType
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns all AlarmManager scheduling. One-shot exact alarms, re-scheduled
 * inside [AuraReminderReceiver] after each fire. Never setRepeating (inexact
 * since API 19) and never WorkManager (15-min minimum, Doze-batched).
 *
 * Quiet hours (from SettingsRepository) are honored at fire time by the
 * receiver, not by shifting triggers here, so a quiet-hours change takes
 * effect without rescheduling.
 *
 * Call [scheduleAll] after: onboarding completes, reminder settings change,
 * quiet-hours change, boot, time/timezone/locale change.
 */
@Singleton
class AuraReminderScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val reminderRepository: ReminderRepository,
        private val settingsRepository: SettingsRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        companion object {
            const val ACTION_MORNING = "com.sreecharan.stressrelief.action.MORNING_CHECKIN"
            const val ACTION_NUDGE = "com.sreecharan.stressrelief.action.STREAK_NUDGE"
            const val ACTION_WINDDOWN = "com.sreecharan.stressrelief.action.EVENING_WINDDOWN"

            private const val REQ_MORNING = 6001
            private const val REQ_NUDGE = 6002
            private const val REQ_WINDDOWN = 6003
        }

        private val alarmManager: AlarmManager? =
            context.getSystemService(AlarmManager::class.java)

        /** True when exact alarms will actually fire at the requested time. */
        fun canScheduleExact(): Boolean {
            val am = alarmManager ?: return false
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        }

        suspend fun scheduleAll() {
            withContext(ioDispatcher) {
                cancelAll()
                val remindersOn = settingsRepository.remindersEnabled.first()
                if (!remindersOn) return@withContext
                val prefs = reminderRepository.preferences.first()
                scheduleType(ReminderType.MORNING_CHECKIN, prefs)
                scheduleType(ReminderType.STREAK_NUDGE, prefs)
                scheduleType(ReminderType.EVENING_WINDDOWN, prefs)
            }
        }

        fun cancelAll() {
            val am = alarmManager ?: return
            am.cancel(pendingIntent(ACTION_MORNING, REQ_MORNING))
            am.cancel(pendingIntent(ACTION_NUDGE, REQ_NUDGE))
            am.cancel(pendingIntent(ACTION_WINDDOWN, REQ_WINDDOWN))
        }

        private fun scheduleType(
            type: ReminderType,
            prefs: ReminderPreferences,
        ) {
            if (!prefs.isEnabled(type)) return
            val (action, req) =
                when (type) {
                    ReminderType.MORNING_CHECKIN -> ACTION_MORNING to REQ_MORNING
                    ReminderType.STREAK_NUDGE -> ACTION_NUDGE to REQ_NUDGE
                    ReminderType.EVENING_WINDDOWN -> ACTION_WINDDOWN to REQ_WINDDOWN
                }
            val trigger = ReminderTimes.triggerFor(type, prefs)
            scheduleExact(trigger, pendingIntent(action, req))
        }

        private fun pendingIntent(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, AuraReminderReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Exact when allowed; 10-minute window fallback when the user denied
         * SCHEDULE_EXACT_ALARM. Callers should surface the "may arrive late"
         * note when [canScheduleExact] is false.
         */
        private fun scheduleExact(triggerAtMillis: Long, pendingIntent: PendingIntent) {
            val am = alarmManager ?: return
            try {
                if (canScheduleExact()) {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    am.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        10 * 60 * 1000L,
                        pendingIntent,
                    )
                }
            } catch (_: SecurityException) {
                // Exact alarm revoked; the inexact fallback needs the same
                // permission on API 31+, so there is nothing more to do here.
            }
        }
    }
