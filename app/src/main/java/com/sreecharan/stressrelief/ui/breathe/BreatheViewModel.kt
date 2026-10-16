package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Sos
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.catalog.BreathingPattern
import com.sreecharan.stressrelief.data.catalog.BreathingPatterns
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
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

@Immutable
sealed interface BreatheUiState {
    data object Loading : BreatheUiState

    data class Content(
        val goals: ImmutableList<GoalCardUi>,
        val selectedGoal: String?,
    ) : BreatheUiState
}

sealed interface BreatheEvent {
    data class OpenSession(
        val goal: String,
        val patternId: String,
    ) : BreatheEvent
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

/**
 * Breathe tab: four goal cards, each opening a pattern picker sheet.
 * All data comes from the static catalog, so there is no loading or
 * error state beyond the initial frame.
 */
@HiltViewModel
class BreatheViewModel
    @Inject
    constructor() : ViewModel() {
        private val _uiState = MutableStateFlow<BreatheUiState>(BreatheUiState.Loading)
        val uiState: StateFlow<BreatheUiState> = _uiState.asStateFlow()

        private val _events = Channel<BreatheEvent>(Channel.BUFFERED)
        val events: Flow<BreatheEvent> = _events.receiveAsFlow()

        init {
            _uiState.value =
                BreatheUiState.Content(
                    goals = buildGoalCards().toImmutableList(),
                    selectedGoal = null,
                )
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
