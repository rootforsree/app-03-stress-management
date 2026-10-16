package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.model.QuietHours
import com.sreecharan.stressrelief.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<ThemeMode>
    val dynamicColor: Flow<Boolean>
    val remindersEnabled: Flow<Boolean>
    val quietHours: Flow<QuietHours>
    val onboardingCompleted: Flow<Boolean>

    suspend fun setThemeMode(m: ThemeMode)

    suspend fun setDynamicColor(b: Boolean)

    suspend fun setRemindersEnabled(b: Boolean)

    suspend fun setQuietHours(q: QuietHours)

    suspend fun setOnboardingCompleted()
}
