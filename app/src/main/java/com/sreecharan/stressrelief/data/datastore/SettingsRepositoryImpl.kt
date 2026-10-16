package com.sreecharan.stressrelief.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sreecharan.stressrelief.data.model.QuietHours
import com.sreecharan.stressrelief.data.model.ThemeMode
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        @Named("aura") private val dataStore: DataStore<Preferences>,
    ) : SettingsRepository {
        private object Keys {
            val THEME_MODE = stringPreferencesKey("theme_mode")
            val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
            val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
            val QUIET_START_HOUR = intPreferencesKey("quiet_hours_start_hour")
            val QUIET_START_MIN = intPreferencesKey("quiet_hours_start_minute")
            val QUIET_END_HOUR = intPreferencesKey("quiet_hours_end_hour")
            val QUIET_END_MIN = intPreferencesKey("quiet_hours_end_minute")
            val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        }

        override val themeMode: Flow<ThemeMode> =
            dataStore.data.map { prefs ->
                prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM
            }

        override val dynamicColor: Flow<Boolean> =
            dataStore.data.map { prefs -> prefs[Keys.DYNAMIC_COLOR] ?: false }

        override val remindersEnabled: Flow<Boolean> =
            dataStore.data.map { prefs -> prefs[Keys.REMINDERS_ENABLED] ?: true }

        override val quietHours: Flow<QuietHours> =
            dataStore.data.map { prefs ->
                QuietHours(
                    startHour = prefs[Keys.QUIET_START_HOUR] ?: 22,
                    startMin = prefs[Keys.QUIET_START_MIN] ?: 0,
                    endHour = prefs[Keys.QUIET_END_HOUR] ?: 7,
                    endMin = prefs[Keys.QUIET_END_MIN] ?: 0,
                )
            }

        override val onboardingCompleted: Flow<Boolean> =
            dataStore.data.map { prefs -> prefs[Keys.ONBOARDING_COMPLETED] ?: false }

        override suspend fun setThemeMode(m: ThemeMode) {
            dataStore.edit { it[Keys.THEME_MODE] = m.name }
        }

        override suspend fun setDynamicColor(b: Boolean) {
            dataStore.edit { it[Keys.DYNAMIC_COLOR] = b }
        }

        override suspend fun setRemindersEnabled(b: Boolean) {
            dataStore.edit { it[Keys.REMINDERS_ENABLED] = b }
        }

        override suspend fun setQuietHours(q: QuietHours) {
            dataStore.edit {
                it[Keys.QUIET_START_HOUR] = q.startHour.coerceIn(0, 23)
                it[Keys.QUIET_START_MIN] = q.startMin.coerceIn(0, 59)
                it[Keys.QUIET_END_HOUR] = q.endHour.coerceIn(0, 23)
                it[Keys.QUIET_END_MIN] = q.endMin.coerceIn(0, 59)
            }
        }

        override suspend fun setOnboardingCompleted() {
            dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = true }
        }
    }
