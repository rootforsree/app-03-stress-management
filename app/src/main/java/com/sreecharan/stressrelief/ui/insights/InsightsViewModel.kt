package com.sreecharan.stressrelief.ui.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.repository.SleepRepository
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@Immutable
sealed interface InsightsUiState {
    data object Loading : InsightsUiState

    data class Content(
        val trend: ImmutableList<DailyMood?>,
        val trendDelta: Double?,
        val monthCells: ImmutableList<MonthCell>,
        val monthLabel: String,
        val topActivities: ImmutableList<ActivityMood>,
        val sleep: SleepMoodInsight,
        val consistencyPct: Int,
    ) : InsightsUiState

    data class Error(
        val message: String,
    ) : InsightsUiState
}

/**
 * Insights data. Premium gating is handled at the UI layer by Builder 5's
 * ProGate (ui/paywall/BillingGate.kt); this ViewModel only computes content.
 * Content: mood trend chart data, year-in-pixels month grid, correlation cards.
 */
@HiltViewModel
class InsightsViewModel
    @Inject
    constructor(
        private val wellness: WellnessRepository,
        private val sleep: SleepRepository,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<InsightsUiState>(InsightsUiState.Loading)
        val uiState: StateFlow<InsightsUiState> = _uiState.asStateFlow()

        private var collectJob: Job? = null

        init {
            start()
        }

        fun retry() {
            start()
        }

        private fun start() {
            collectJob?.cancel()
            _uiState.value = InsightsUiState.Loading
            collectJob =
                viewModelScope.launch(ioDispatcher) {
                    combine(
                        wellness.observeMoodHistory(InsightsStats.TREND_DAYS),
                        sleep.observeRecent(InsightsStats.TREND_DAYS),
                    ) { checkIns, sessions ->
                        val trend = InsightsStats.dailySeries(checkIns)
                        val month = YearMonth.now()
                        InsightsUiState.Content(
                            trend = trend.toImmutableList(),
                            trendDelta = InsightsStats.trendDelta(trend),
                            monthCells = InsightsStats.monthCells(checkIns, month).toImmutableList(),
                            monthLabel =
                                month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                            topActivities = InsightsStats.moodByActivity(checkIns).take(3).toImmutableList(),
                            sleep = InsightsStats.sleepInsight(checkIns, sessions),
                            consistencyPct = InsightsStats.consistencyPct(checkIns),
                        )
                    }.catch { e ->
                        _uiState.value =
                            InsightsUiState.Error(e.message ?: "Could not load your insights.")
                    }.collect { _uiState.value = it }
                }
        }
    }
