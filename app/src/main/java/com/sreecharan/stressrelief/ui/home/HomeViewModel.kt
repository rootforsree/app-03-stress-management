package com.sreecharan.stressrelief.ui.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Content(
        val stressScore: StressScore?,
        val streakDays: Int,
        val weekDots: ImmutableList<WeekDot>,
        val totalCalmMinutes: Int,
        val checkedInToday: Boolean,
        val journalDoneToday: Boolean,
        val healthConnectState: HealthConnectUiState = HealthConnectUiState.Unavailable,
    ) : HomeUiState

    data class Error(val message: String) : HomeUiState
}

/** Health Connect opt-in state for the "Connect Health" card. */
@Immutable
sealed interface HealthConnectUiState {
    /** Health Connect is not installed/available on this device. */
    data object Unavailable : HealthConnectUiState

    /** Available but the stress-signal permissions are not granted: show the opt-in card. */
    data object Available : HealthConnectUiState

    /** Available and granted: health signals feed the stress score. */
    data object Granted : HealthConnectUiState
}

@Immutable
data class WeekDot(
    val label: String,
    val active: Boolean,
)

/** Pure display logic for the home dashboard. Covered by unit tests. */
object HomeStats {
    private val DayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

    /**
     * Seven dots for the current week (Monday first). A dot is active when the user
     * logged a mood check-in on that day.
     */
    fun weekDots(
        checkIns: List<MoodCheckIn>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeekDot> {
        val monday = today.with(DayOfWeek.MONDAY)
        val activeDays =
            checkIns
                .map { Instant.ofEpochMilli(it.timestampMs).atZone(zone).toLocalDate() }
                .toSet()
        return (0..6).map { offset ->
            val day = monday.plusDays(offset.toLong())
            WeekDot(label = DayLabels[offset], active = day in activeDays)
        }
    }

    fun streakLabel(days: Int): String =
        when {
            days <= 0 -> "Start your streak"
            days == 1 -> "1-day streak"
            else -> "$days-day streak"
        }

    /**
     * Petal count for the bloom visual. Always at least 3 petals so the bloom reads
     * as a flower even on day zero, capped at 12 so it never gets cluttered.
     */
    fun bloomPetals(streakDays: Int): Int = streakDays.coerceIn(3, 12)

    fun isToday(timestampMs: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate() == LocalDate.now(zone)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val wellness: WellnessRepository,
    val healthConnect: HealthConnectStateProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val stressScore = MutableStateFlow<StressScore?>(null)
    private val healthConnectState =
        MutableStateFlow<HealthConnectUiState>(HealthConnectUiState.Unavailable)
    private var collectJob: Job? = null

    init {
        start()
    }

    /** Re-reads the stress score (Health Connect + check-ins). Never throws. */
    fun refreshStressScore() {
        viewModelScope.launch(ioDispatcher) {
            stressScore.value = runCatching { wellness.computeStressScore() }.getOrNull()
        }
    }

    /** Re-reads Health Connect availability/permission state. Never throws. */
    fun checkHealthConnect() {
        viewModelScope.launch(ioDispatcher) {
            healthConnectState.value =
                runCatching {
                    when {
                        !healthConnect.isAvailable() -> HealthConnectUiState.Unavailable
                        healthConnect.hasAllPermissions() -> HealthConnectUiState.Granted
                        else -> HealthConnectUiState.Available
                    }
                }.getOrDefault(HealthConnectUiState.Unavailable)
        }
    }

    /** Called with the Health Connect permission-flow result; re-checks and refreshes. */
    fun onHealthPermissionResult() {
        checkHealthConnect()
        refreshStressScore()
    }

    fun retry() {
        start()
    }

    private fun start() {
        collectJob?.cancel()
        _uiState.value = HomeUiState.Loading
        refreshStressScore()
        checkHealthConnect()
        val contentFlow =
            combine(
                stressScore,
                wellness.observeTodayCheckIn(),
                wellness.observeStreakDays(),
                wellness.observeTotalCalmMinutes(),
                wellness.observeMoodHistory(7),
                wellness.observeJournalEntries(),
            ) { score, todayCheckIn, streakDays, calmMinutes, weekHistory, journals ->
                val today = LocalDate.now()
                HomeUiState.Content(
                    stressScore = score,
                    streakDays = streakDays,
                    weekDots = HomeStats.weekDots(weekHistory, today).toImmutableList(),
                    totalCalmMinutes = calmMinutes,
                    checkedInToday = todayCheckIn != null,
                    journalDoneToday = journals.any { HomeStats.isToday(it.timestampMs) },
                )
            }
        collectJob =
            viewModelScope.launch(ioDispatcher) {
                combine(contentFlow, healthConnectState) { content, health ->
                    content.copy(healthConnectState = health)
                }.catch { e ->
                    _uiState.value =
                        HomeUiState.Error(e.message ?: "Could not load your dashboard.")
                }.collect { _uiState.value = it }
            }
    }
}
