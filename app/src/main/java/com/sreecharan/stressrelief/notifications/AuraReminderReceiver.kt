package com.sreecharan.stressrelief.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.data.reminders.ReminderTimes
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.widget.AuraWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Fires on every scheduled reminder alarm. Honors quiet hours (suppresses
 * the notification when the trigger lands inside the window), posts the
 * notification, re-arms the next occurrence (alarms are one-shot), and
 * refreshes the widget.
 *
 * The streak nudge only posts when no mood check-in happened today, so an
 * active user never gets nagged.
 */
@AndroidEntryPoint
class AuraReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var wellnessRepository: WellnessRepository

    @Inject lateinit var notifications: AuraNotifications

    @Inject lateinit var scheduler: AuraReminderScheduler

    @Inject lateinit var widgetUpdater: AuraWidgetUpdater

    @Inject @IoDispatcher lateinit var ioDispatcher: CoroutineDispatcher

    private val scope get() = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                handle(intent.action)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(action: String?) {
        val remindersOn = settingsRepository.remindersEnabled.first()
        if (!remindersOn) return
        val quietHours = settingsRepository.quietHours.first()
        val inQuietHours = ReminderTimes.isInQuietHours(System.currentTimeMillis(), quietHours)

        when (action) {
            AuraReminderScheduler.ACTION_MORNING -> {
                if (!inQuietHours) notifications.postMorningCheckIn()
                scheduler.scheduleAll() // re-arm tomorrow's occurrence
            }
            AuraReminderScheduler.ACTION_NUDGE -> {
                val checkedInToday = wellnessRepository.observeTodayCheckIn().first() != null
                if (!checkedInToday && !inQuietHours) notifications.postStreakNudge()
                scheduler.scheduleAll()
            }
            AuraReminderScheduler.ACTION_WINDDOWN -> {
                if (!inQuietHours) notifications.postEveningWindDown()
                scheduler.scheduleAll()
            }
        }
        widgetUpdater.refresh()
    }
}
