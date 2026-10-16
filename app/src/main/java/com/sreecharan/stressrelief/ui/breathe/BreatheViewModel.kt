package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.catalog.BreathingPattern
import com.sreecharan.stressrelief.data.catalog.BreathingPatterns
import com.sreecharan.stressrelief.data.datastore.CustomPatternStore
import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.CustomPattern
import com.sreecharan.stressrelief.data.repository.BreathingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Visual theme key for a goal card; resolved to gradient colors in the UI. */
enum class GoalTheme {
    CALM,
    SLEEP,
    FOCUS,
    SOS,
}

@Immutable
data class PatternUi(
    val id: String,
    val name: String,
    val inhaleSec: Int,
    val holdSec: Int,
    val exhaleSec: Int,
    val holdAfterSec: Int,
    val rounds: Int,
    val description: String,
)

@Immutable
data class GoalCardUi(
    val goal: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val theme: GoalTheme,
    val patterns: ImmutableList<PatternUi>,
)

/** One day of the 7-day breathing diary. */
@Immutable
data class DiaryDay(
    val epochDay: Long,
    val minutes: Float,
    val isToday: Boolean,
)

@Immutable
sealed interface BreatheUiState {
    data object Loading : BreatheUiState

    data class Content(
        val goals: ImmutableList<GoalCardUi>,
        val selectedGoal: String?,
        val customPatterns: ImmutableList<CustomPattern>,
        val diary: ImmutableList<DiaryDay>,
        val goalMin: Int,
    ) : BreatheUiState
}

sealed interface BreatheEvent {
    data class OpenSession(
        val goal: String,
        val patternId: String,
    ) : BreatheEvent

    data object OpenPatternBuilder : BreatheEvent
}

private data class GoalDef(
    val goal: String,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: ImageVector,
    val theme: GoalTheme,
)

private fun BreathingPattern.toUi(): PatternUi =
    PatternUi(
        id = id,
        name = name,
        inhaleSec = inhaleSec,
        holdSec = holdSec,
        exhaleSec = exhaleSec,
        holdAfterSec = holdAfterSec,
        rounds = rounds,
        description = description,
    )

private const val DIARY_SESSION_LIMIT = 300
private const val DIARY_DAYS = 7

/**
 * Minutes breathed per calendar day for the last [DIARY_DAYS] days, oldest
 * first, ending today. Days without sessions report 0 minutes.
 */
internal fun diaryDays(sessions: List<BreathingSession>): List<DiaryDay> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val secondsByDay =
        sessions
            .groupBy { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() }
            .mapValues { (_, daySessions) -> daySessions.sumOf { it.durationSec } }
    return (DIARY_DAYS - 1 downTo 0).map { daysBack ->
        val date = today.minusDays(daysBack.toLong())
        DiaryDay(
            epochDay = date.toEpochDay(),
            minutes = (secondsByDay[date] ?: 0) / 60f,
            isToday = daysBack == 0,
        )
    }
}

/**
 * Breathe tab: four goal cards, each opening a pattern picker sheet; the
 * sheet lists catalog patterns, the user's custom patterns, and a
 * "Create your own" entry. Below the header sits the 7-day breathing diary
 * against the daily goal.
 */
@HiltViewModel
class BreatheViewModel
    @Inject
    constructor(
        private val breathingRepository: BreathingRepository,
        private val customPatternStore: CustomPatternStore,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<BreatheUiState>(BreatheUiState.Loading)
        val uiState: StateFlow<BreatheUiState> = _uiState.asStateFlow()

        private val _events = Channel<BreatheEvent>(Channel.BUFFERED)
        val events: Flow<BreatheEvent> = _events.receiveAsFlow()

        init {
            viewModelScope.launch {
                combine(
                    breathingRepository.observeRecentSessions(DIARY_SESSION_LIMIT),
                    customPatternStore.patterns,
                    customPatternStore.breathingGoalMin,
                ) { sessions, customs, goalMin ->
                    Triple(sessions, customs, goalMin)
                }.collect { (sessions, customs, goalMin) ->
                    val selected = (_uiState.value as? BreatheUiState.Content)?.selectedGoal
                    _uiState.value =
                        BreatheUiState.Content(
                            goals = buildGoalCards().toImmutableList(),
                            selectedGoal = selected,
                            customPatterns = customs.toImmutableList(),
                            diary = diaryDays(sessions).toImmutableList(),
                            goalMin = goalMin,
                        )
                }
            }
        }

        fun onGoalClick(goal: String) {
            val content = _uiState.value as? BreatheUiState.Content ?: return
            _uiState.value = content.copy(selectedGoal = goal)
        }

        fun onPickerDismiss() {
            val content = _uiState.value as? BreatheUiState.Content ?: return
            _uiState.value = content.copy(selectedGoal = null)
        }

        fun onPatternClick(patternId: String) {
            val content = _uiState.value as? BreatheUiState.Content ?: return
            val goal = content.selectedGoal ?: return
            _uiState.value = content.copy(selectedGoal = null)
            _events.trySend(BreatheEvent.OpenSession(goal, patternId))
        }

        fun onCreateCustomClick() {
            val content = _uiState.value as? BreatheUiState.Content ?: return
            _uiState.value = content.copy(selectedGoal = null)
            _events.trySend(BreatheEvent.OpenPatternBuilder)
        }

        fun onGoalChange(minutes: Int) {
            viewModelScope.launch {
                runCatching { customPatternStore.setBreathingGoalMin(minutes) }
            }
        }

        private fun buildGoalCards(): List<GoalCardUi> {
            val defs =
                listOf(
                    GoalDef(
                        "CALM",
                        R.string.breathe_goal_calm,
                        R.string.breathe_goal_calm_sub,
                        Icons.Rounded.Spa,
                        GoalTheme.CALM,
                    ),
                    GoalDef(
                        "SLEEP",
                        R.string.breathe_goal_sleep,
                        R.string.breathe_goal_sleep_sub,
                        Icons.Rounded.Bedtime,
                        GoalTheme.SLEEP,
                    ),
                    GoalDef(
                        "FOCUS",
                        R.string.breathe_goal_focus,
                        R.string.breathe_goal_focus_sub,
                        Icons.Rounded.Psychology,
                        GoalTheme.FOCUS,
                    ),
                    GoalDef(
                        "SOS",
                        R.string.breathe_goal_sos,
                        R.string.breathe_goal_sos_sub,
                        Icons.Rounded.Sos,
                        GoalTheme.SOS,
                    ),
                )
            return defs.map { def ->
                GoalCardUi(
                    goal = def.goal,
                    titleRes = def.titleRes,
                    subtitleRes = def.subtitleRes,
                    icon = def.icon,
                    theme = def.theme,
                    patterns = BreathingPatterns.forGoal(def.goal).map { it.toUi() }.toImmutableList(),
                )
            }
        }
    }
