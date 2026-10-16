package com.sreecharan.stressrelief.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.notifications.AuraReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
sealed interface OnboardingUiState {
    data class Paging(
        val page: Int,
        val totalPages: Int = TOTAL_PAGES,
        /** Last-page opt-in; persisted as remindersEnabled on finish. */
        val remindersOptIn: Boolean = false,
    ) : OnboardingUiState

    companion object {
        const val TOTAL_PAGES = 3
    }
}

sealed interface OnboardingEvent {
    data object NavigateHome : OnboardingEvent
}

/**
 * Max 3 pages, skippable, ends at HomeRoute. Both the complete path AND the
 * skip path persist via [SettingsRepository.setOnboardingCompleted] (the
 * app-02 bug was a skip path that forgot to persist).
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        private val scheduler: AuraReminderScheduler,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<OnboardingUiState>(OnboardingUiState.Paging(page = 0))
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        private val _events = Channel<OnboardingEvent>(Channel.BUFFERED)
        val events: Flow<OnboardingEvent> = _events.receiveAsFlow()

        fun onPageChanged(page: Int) {
            val current = _uiState.value as? OnboardingUiState.Paging ?: return
            _uiState.value = current.copy(page = page)
        }

        fun onNext() {
            val current = _uiState.value as? OnboardingUiState.Paging ?: return
            if (current.page < current.totalPages - 1) {
                _uiState.update { OnboardingUiState.Paging(page = current.page + 1) }
            } else {
                finishOnboarding()
            }
        }

        fun onSkip() {
            finishOnboarding()
        }

        fun onRemindersOptInChanged(optIn: Boolean) {
            val current = _uiState.value as? OnboardingUiState.Paging ?: return
            _uiState.value = current.copy(remindersOptIn = optIn)
        }

        private fun finishOnboarding() {
            viewModelScope.launch(ioDispatcher) {
                val optIn = (_uiState.value as? OnboardingUiState.Paging)?.remindersOptIn ?: false
                settingsRepository.setRemindersEnabled(optIn)
                settingsRepository.setOnboardingCompleted()
                // Arms the reminder alarms (no-op while reminders are disabled).
                runCatching { scheduler.scheduleAll() }
                _events.send(OnboardingEvent.NavigateHome)
            }
        }
    }
