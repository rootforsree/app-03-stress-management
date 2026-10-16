package com.sreecharan.stressrelief.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sreecharan.stressrelief.DeepLinks
import com.sreecharan.stressrelief.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds and posts Aura's reminder notifications. Notification text comes
 * from strings.xml (no hardcoded UI text); taps deep-link into the app.
 */
@Singleton
class AuraNotifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            const val CHANNEL_REMINDERS = "aura_reminders"

            private const val NOTIF_MORNING = 6101
            private const val NOTIF_NUDGE = 6102
            private const val NOTIF_WINDDOWN = 6103
        }

        fun ensureChannels() {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel =
                NotificationChannel(
                    CHANNEL_REMINDERS,
                    context.getString(R.string.notif_channel_reminders),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = context.getString(R.string.notif_channel_reminders_desc)
                }
            manager.createNotificationChannel(channel)
        }

        fun postMorningCheckIn() {
            post(
                id = NOTIF_MORNING,
                titleRes = R.string.notif_morning_title,
                bodyRes = R.string.notif_morning_body,
                deepLink = DeepLinks.MOOD,
            )
        }

        fun postStreakNudge() {
            post(
                id = NOTIF_NUDGE,
                titleRes = R.string.notif_nudge_title,
                bodyRes = R.string.notif_nudge_body,
                deepLink = DeepLinks.MOOD,
            )
        }

        fun postEveningWindDown() {
            post(
                id = NOTIF_WINDDOWN,
                titleRes = R.string.notif_winddown_title,
                bodyRes = R.string.notif_winddown_body,
                deepLink = DeepLinks.BREATHE,
            )
        }

        private fun post(
            id: Int,
            titleRes: Int,
            bodyRes: Int,
            deepLink: String,
        ) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
            ensureChannels()
            val contentIntent =
                PendingIntent.getActivity(
                    context,
                    id,
                    DeepLinks.viewIntent(context, deepLink),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL_REMINDERS)
                    .setSmallIcon(R.drawable.ic_stat_aura)
                    .setContentTitle(context.getString(titleRes))
                    .setContentText(context.getString(bodyRes))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(bodyRes)))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build()
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }
