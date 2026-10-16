package com.sreecharan.stressrelief.widget

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.widget.AuraWidget.Companion.widgetDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes fresh values into the widget's cached state and triggers a Glance
 * update. Glance composition cannot easily read Room or Health Connect, so
 * the stress score and streak are cached here whenever anything meaningful
 * happens: alarm fires, check-in saved, app comes to foreground.
 *
 * [WellnessRepository.computeStressScore] degrades gracefully when Health
 * Connect is unavailable; a failure here only leaves the widget showing the
 * last cached values.
 */
@Singleton
class AuraWidgetUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val wellnessRepository: WellnessRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) {
        suspend fun refresh() {
            withContext(ioDispatcher) {
                val streak = wellnessRepository.observeStreakDays().first()
                val score =
                    try {
                        wellnessRepository.computeStressScore()
                    } catch (_: Exception) {
                        null
                    }
                context.widgetDataStore.edit { prefs ->
                    prefs[KEY_WIDGET_STREAK] = streak
                    if (score != null) {
                        prefs[KEY_WIDGET_STRESS_SCORE] = score.score.coerceIn(0, 100)
                        prefs[KEY_WIDGET_STRESS_LABEL] = score.label
                    }
                    prefs[KEY_WIDGET_UPDATED] = System.currentTimeMillis()
                }
                updateWidget()
            }
        }

        suspend fun updateWidget() {
            withContext(ioDispatcher) {
                try {
                    val manager = GlanceAppWidgetManager(context)
                    val widget = AuraWidget()
                    manager.getGlanceIds(AuraWidget::class.java).forEach { glanceId ->
                        widget.update(context, glanceId)
                    }
                } catch (_: Exception) {
                    // Widget host unavailable (e.g. work profile); not fatal.
                }
            }
        }
    }
