package com.sreecharan.stressrelief.ui.insights

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.data.model.InsightsData
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Insights screen state. The dashboard is free for everyone (v2): no premium
 * gate, no entitlement check. Content comes from a single
 * [WellnessRepository.computeInsights] call.
 */
sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    data class Content(
        val insights: InsightsData,
    ) : InsightsUiState

    data object Error : InsightsUiState
}

@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        private val wellness: WellnessRepository,
        val healthConnect: HealthConnectStateProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)
        val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

        private var loadJob: Job? = null

        init {
            load()
        }

        fun retry() {
            load()
        }

        /** Re-reads insights after the Health Connect permission flow returns. */
        fun onHealthPermissionResult() {
            load()
        }

        private fun load() {
            loadJob?.cancel()
            _uiState.value = InsightsUiState.Loading
            loadJob =
                viewModelScope.launch(ioDispatcher) {
                    // computeInsights never throws by contract; the guard is belt and braces.
                    val insights = runCatching { wellness.computeInsights() }.getOrNull()
                    _uiState.value =
                        if (insights != null) {
                            InsightsUiState.Content(insights)
                        } else {
                            InsightsUiState.Error
                        }
                }
        }
    }
