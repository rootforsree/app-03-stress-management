package com.sreecharan.stressrelief.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.sreecharan.stressrelief.di.IoDispatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms all reminder alarms after reboot and after clock changes.
 * Declared in the manifest with RECEIVE_BOOT_COMPLETED.
 */
@AndroidEntryPoint
class AuraBootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: AuraReminderScheduler

    @Inject @IoDispatcher
    lateinit var ioDispatcher: CoroutineDispatcher

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
            -> {
                val pendingResult = goAsync()
                CoroutineScope(SupervisorJob() + ioDispatcher).launch {
                    try {
                        scheduler.scheduleAll()
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }
}
