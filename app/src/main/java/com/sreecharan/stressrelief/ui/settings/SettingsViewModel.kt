package com.sreecharan.stressrelief.ui.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.model.QuietHours
import com.sreecharan.stressrelief.data.model.ThemeMode
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.notifications.AuraReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(
        val themeMode: ThemeMode = ThemeMode.SYSTEM,
        val dynamicColor: Boolean = false,
        val remindersEnabled: Boolean = false,
        val quietHours: QuietHours = QuietHours(22, 0, 7, 0),
    ) : SettingsUiState
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val scheduler: AuraReminderScheduler,
    ) : ViewModel() {
        val uiState: StateFlow<SettingsUiState> =
            combine(
                settingsRepository.themeMode,
                settingsRepository.dynamicColor,
                settingsRepository.remindersEnabled,
                settingsRepository.quietHours,
            ) { mode, dynamic, reminders, quiet ->
                SettingsUiState.Ready(
                    themeMode = mode,
                    dynamicColor = dynamic,
                    remindersEnabled = reminders,
                    quietHours = quiet,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState.Loading,
            )

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch(ioDispatcher) { settingsRepository.setThemeMode(mode) }
        }

        fun setDynamicColor(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) { settingsRepository.setDynamicColor(enabled) }
        }

        fun setRemindersEnabled(enabled: Boolean) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.setRemindersEnabled(enabled)
                runCatching { scheduler.scheduleAll() }
            }
        }

        fun setQuietHours(quietHours: QuietHours) {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.setQuietHours(quietHours)
                runCatching { scheduler.scheduleAll() }
            }
        }

        /**
         * Resets all user preferences to defaults. Full Room data wipe (journal,
         * moods, sessions) is Builder 2's domain: AuraDatabase.clearAllTables()
         * should be called alongside this when it exists.
         */
        fun resetAllData() {
            viewModelScope.launch(ioDispatcher) {
                settingsRepository.setThemeMode(ThemeMode.SYSTEM)
                settingsRepository.setDynamicColor(false)
                settingsRepository.setRemindersEnabled(false)
                settingsRepository.setQuietHours(QuietHours(22, 0, 7, 0))
            }
        }
    }
