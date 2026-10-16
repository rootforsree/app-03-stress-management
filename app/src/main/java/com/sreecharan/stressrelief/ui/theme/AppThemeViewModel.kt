package com.sreecharan.stressrelief.ui.theme

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.model.ThemeMode
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** App-level theme state consumed by MainActivity before any screen draws. */
@Immutable
sealed interface AppThemeUiState {
    data object Loading : AppThemeUiState

    data class Ready(
        val themeMode: ThemeMode,
        val dynamicColor: Boolean,
    ) : AppThemeUiState
}

@HiltViewModel
class AppThemeViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
    ) : ViewModel() {
        val uiState: StateFlow<AppThemeUiState> =
            combine(
                settingsRepository.themeMode,
                settingsRepository.dynamicColor,
            ) { mode, dynamic ->
                AppThemeUiState.Ready(themeMode = mode, dynamicColor = dynamic)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AppThemeUiState.Loading,
            )

        val onboardingCompleted: StateFlow<Boolean> =
            settingsRepository.onboardingCompleted
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = false,
                )
    }
