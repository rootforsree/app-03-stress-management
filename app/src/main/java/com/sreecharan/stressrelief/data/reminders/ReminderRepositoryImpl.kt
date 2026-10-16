package com.sreecharan.stressrelief.data.reminders

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl
    @Inject
    constructor(
        @Named("reminders") private val dataStore: DataStore<Preferences>,
    ) : ReminderRepository {
        private companion object {
            val KEY_MORNING_ENABLED = booleanPreferencesKey("rem_morning_enabled")
            val KEY_MORNING_HOUR = intPreferencesKey("rem_morning_hour")
            val KEY_MORNING_MINUTE = intPreferencesKey("rem_morning_minute")
            val KEY_NUDGE_ENABLED = booleanPreferencesKey("rem_nudge_enabled")
            val KEY_NUDGE_HOUR = intPreferencesKey("rem_nudge_hour")
            val KEY_NUDGE_MINUTE = intPreferencesKey("rem_nudge_minute")
            val KEY_WINDDOWN_ENABLED = booleanPreferencesKey("rem_winddown_enabled")
            val KEY_WINDDOWN_HOUR = intPreferencesKey("rem_winddown_hour")
            val KEY_WINDDOWN_MINUTE = intPreferencesKey("rem_winddown_minute")
        }

        override val preferences: Flow<ReminderPreferences> =
            dataStore.data.map { prefs ->
                val defaults = ReminderPreferences()
                ReminderPreferences(
                    morningEnabled = prefs[KEY_MORNING_ENABLED] ?: defaults.morningEnabled,
                    morningHour = prefs[KEY_MORNING_HOUR] ?: defaults.morningHour,
                    morningMinute = prefs[KEY_MORNING_MINUTE] ?: defaults.morningMinute,
                    nudgeEnabled = prefs[KEY_NUDGE_ENABLED] ?: defaults.nudgeEnabled,
                    nudgeHour = prefs[KEY_NUDGE_HOUR] ?: defaults.nudgeHour,
                    nudgeMinute = prefs[KEY_NUDGE_MINUTE] ?: defaults.nudgeMinute,
                    windDownEnabled = prefs[KEY_WINDDOWN_ENABLED] ?: defaults.windDownEnabled,
                    windDownHour = prefs[KEY_WINDDOWN_HOUR] ?: defaults.windDownHour,
                    windDownMinute = prefs[KEY_WINDDOWN_MINUTE] ?: defaults.windDownMinute,
                )
            }

        override suspend fun update(transform: (ReminderPreferences) -> ReminderPreferences) {
            dataStore.edit { prefs ->
                val current =
                    ReminderPreferences(
                        morningEnabled = prefs[KEY_MORNING_ENABLED] ?: true,
                        morningHour = prefs[KEY_MORNING_HOUR] ?: ReminderType.MORNING_CHECKIN.defaultHour,
                        morningMinute = prefs[KEY_MORNING_MINUTE] ?: ReminderType.MORNING_CHECKIN.defaultMinute,
                        nudgeEnabled = prefs[KEY_NUDGE_ENABLED] ?: true,
                        nudgeHour = prefs[KEY_NUDGE_HOUR] ?: ReminderType.STREAK_NUDGE.defaultHour,
                        nudgeMinute = prefs[KEY_NUDGE_MINUTE] ?: ReminderType.STREAK_NUDGE.defaultMinute,
                        windDownEnabled = prefs[KEY_WINDDOWN_ENABLED] ?: true,
                        windDownHour = prefs[KEY_WINDDOWN_HOUR] ?: ReminderType.EVENING_WINDDOWN.defaultHour,
                        windDownMinute = prefs[KEY_WINDDOWN_MINUTE] ?: ReminderType.EVENING_WINDDOWN.defaultMinute,
                    )
                val next = transform(current)
                prefs[KEY_MORNING_ENABLED] = next.morningEnabled
                prefs[KEY_MORNING_HOUR] = next.morningHour
                prefs[KEY_MORNING_MINUTE] = next.morningMinute
                prefs[KEY_NUDGE_ENABLED] = next.nudgeEnabled
                prefs[KEY_NUDGE_HOUR] = next.nudgeHour
                prefs[KEY_NUDGE_MINUTE] = next.nudgeMinute
                prefs[KEY_WINDDOWN_ENABLED] = next.windDownEnabled
                prefs[KEY_WINDDOWN_HOUR] = next.windDownHour
                prefs[KEY_WINDDOWN_MINUTE] = next.windDownMinute
            }
        }
    }
