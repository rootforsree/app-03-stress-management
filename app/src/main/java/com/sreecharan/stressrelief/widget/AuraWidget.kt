package com.sreecharan.stressrelief.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sreecharan.stressrelief.DeepLinks
import com.sreecharan.stressrelief.R

internal val KEY_WIDGET_STRESS_SCORE = intPreferencesKey("widget_stress_score")
internal val KEY_WIDGET_STRESS_LABEL = stringPreferencesKey("widget_stress_label")
internal val KEY_WIDGET_STREAK = intPreferencesKey("widget_streak_days")
internal val KEY_WIDGET_UPDATED = longPreferencesKey("widget_updated_ms")

/**
 * Home-screen widget: today's stress score plus the current streak, with
 * one-tap actions for a mood check-in and a breathing session.
 *
 * Reads the cached values written by [AuraWidgetUpdater] (Glance composition
 * cannot easily read Room or Health Connect).
 */
class AuraWidget : GlanceAppWidget() {
    companion object {
        val Context.widgetDataStore by preferencesDataStore(name = "aura_widget")
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val score = prefs[KEY_WIDGET_STRESS_SCORE]
            val label = prefs[KEY_WIDGET_STRESS_LABEL]
            val streak = prefs[KEY_WIDGET_STREAK] ?: 0
            val moodIntent = DeepLinks.viewIntent(context, DeepLinks.MOOD)
            val breatheIntent = DeepLinks.viewIntent(context, DeepLinks.BREATHE)
            WidgetContent(
                score = score,
                label = label,
                streakDays = streak,
                stressTitle = context.getString(R.string.widget_stress_title),
                moodLabel = context.getString(R.string.widget_action_mood),
                breatheLabel = context.getString(R.string.widget_action_breathe),
                streakLabel =
                    context.getString(
                        if (streak == 1) {
                            R.string.widget_streak_one
                        } else {
                            R.string.widget_streak_many
                        },
                    ),
                onMoodClick = actionStartActivity(moodIntent),
                onBreatheClick = actionStartActivity(breatheIntent),
            )
        }
    }

    @Composable
    private fun WidgetContent(
        score: Int?,
        label: String?,
        streakDays: Int,
        stressTitle: String,
        moodLabel: String,
        breatheLabel: String,
        streakLabel: String,
        onMoodClick: Action,
        onBreatheClick: Action,
    ) {
        Column(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF0E1B1A))
                    .padding(16.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = stressTitle,
                        style =
                            TextStyle(
                                fontSize = 12.sp,
                                color = ColorProvider(Color(0xFF7DD3C8)),
                            ),
                    )
                    if (score != null) {
                        Text(
                            text = "$score",
                            style =
                                TextStyle(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color.White),
                                ),
                        )
                        if (label != null) {
                            Text(
                                text = label,
                                style =
                                    TextStyle(
                                        fontSize = 12.sp,
                                        color = ColorProvider(Color(0xFFB8C9C6)),
                                    ),
                            )
                        }
                    } else {
                        Text(
                            text = "--",
                            style =
                                TextStyle(
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(Color.White),
                                ),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.Horizontal.End) {
                    Text(
                        text = "$streakDays",
                        style =
                            TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorProvider(Color(0xFFA78BFA)),
                            ),
                    )
                    Text(
                        text = streakLabel,
                        style =
                            TextStyle(
                                fontSize = 12.sp,
                                color = ColorProvider(Color(0xFFB8C9C6)),
                            ),
                    )
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                androidx.glance.Button(
                    text = moodLabel,
                    onClick = onMoodClick,
                    modifier = GlanceModifier.defaultWeight(),
                )
                Spacer(GlanceModifier.width(8.dp))
                androidx.glance.Button(
                    text = breatheLabel,
                    onClick = onBreatheClick,
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
    }
}
