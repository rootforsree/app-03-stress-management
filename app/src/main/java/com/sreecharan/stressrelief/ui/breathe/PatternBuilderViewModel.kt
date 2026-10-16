package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.datastore.CustomPatternStore
import com.sreecharan.stressrelief.data.model.CustomPattern
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val MAX_NAME_CHARS = 40
private const val MIN_INHALE_SEC = 1
private const val MAX_INHALE_SEC = 30
private const val MIN_HOLD_SEC = 0
private const val MAX_HOLD_SEC = 30
private const val MIN_EXHALE_SEC = 1
private const val MAX_EXHALE_SEC = 30
private const val MIN_HOLD_AFTER_SEC = 0
private const val MAX_HOLD_AFTER_SEC = 30
private const val MIN_ROUNDS = 1
private const val MAX_ROUNDS = 30

@Immutable
sealed interface PatternBuilderUiState {
    data class Editing(
        val name: String,
        val goal: String,
        val inhaleSec: Int,
        val holdSec: Int,
        val exhaleSec: Int,
        val holdAfterSec: Int,
        val rounds: Int,
        val isSaving: Boolean,
    ) : PatternBuilderUiState
}

sealed interface PatternBuilderEvent {
    /** A pattern was saved; the screen either starts a session or goes back. */
    data class Saved(
        val patternId: String,
        val goal: String,
        val startSession: Boolean,
    ) : PatternBuilderEvent
}

/**
 * Builds one custom breathing pattern. All bounds are coerced in the
 * setters, and again in [CustomPatternStore.savePattern], so an invalid
 * pattern can never be persisted.
 */
@HiltViewModel
class PatternBuilderViewModel
    @Inject
    constructor(
        private val customPatternStore: CustomPatternStore,
    ) : ViewModel() {
        private val _uiState =
            MutableStateFlow<PatternBuilderUiState>(
                PatternBuilderUiState.Editing(
                    name = "",
                    goal = "CALM",
                    inhaleSec = 4,
                    holdSec = 0,
                    exhaleSec = 6,
                    holdAfterSec = 0,
                    rounds = 6,
                    isSaving = false,
                ),
            )
        val uiState: StateFlow<PatternBuilderUiState> = _uiState.asStateFlow()

        private val _events = Channel<PatternBuilderEvent>(Channel.BUFFERED)
        val events: Flow<PatternBuilderEvent> = _events.receiveAsFlow()

        fun onNameChange(name: String) {
            _uiState.update { state ->
                (state as? PatternBuilderUiState.Editing)?.copy(name = name.take(MAX_NAME_CHARS))
                    ?: state
            }
        }

        fun onGoalChange(goal: String) {
            _uiState.update { state ->
                (state as? PatternBuilderUiState.Editing)?.copy(goal = goal) ?: state
            }
        }

        fun onInhaleChange(seconds: Int) = updatePhase { copy(inhaleSec = seconds.coerceIn(MIN_INHALE_SEC, MAX_INHALE_SEC)) }

        fun onHoldChange(seconds: Int) = updatePhase { copy(holdSec = seconds.coerceIn(MIN_HOLD_SEC, MAX_HOLD_SEC)) }

        fun onExhaleChange(seconds: Int) = updatePhase { copy(exhaleSec = seconds.coerceIn(MIN_EXHALE_SEC, MAX_EXHALE_SEC)) }

        fun onHoldAfterChange(seconds: Int) =
            updatePhase { copy(holdAfterSec = seconds.coerceIn(MIN_HOLD_AFTER_SEC, MAX_HOLD_AFTER_SEC)) }

        fun onRoundsChange(rounds: Int) = updatePhase { copy(rounds = rounds.coerceIn(MIN_ROUNDS, MAX_ROUNDS)) }

        private inline fun updatePhase(transform: PatternBuilderUiState.Editing.() -> PatternBuilderUiState.Editing) {
            _uiState.update { state ->
                (state as? PatternBuilderUiState.Editing)?.transform() ?: state
            }
        }

        /**
         * Persists the pattern, then emits [PatternBuilderEvent.Saved].
         * A blank name falls back to [fallbackName] (a localized default).
         */
        fun onSave(
            fallbackName: String,
            startSession: Boolean,
        ) {
            val editing = _uiState.value as? PatternBuilderUiState.Editing ?: return
            if (editing.isSaving) return
            _uiState.value = editing.copy(isSaving = true)
            viewModelScope.launch {
                val pattern =
                    CustomPattern(
                        id = "custom-" + UUID.randomUUID().toString(),
                        name = editing.name.ifBlank { fallbackName }.take(MAX_NAME_CHARS),
                        inhaleSec = editing.inhaleSec,
                        holdSec = editing.holdSec,
                        exhaleSec = editing.exhaleSec,
                        holdAfterSec = editing.holdAfterSec,
                        rounds = editing.rounds,
                    )
                runCatching { customPatternStore.savePattern(pattern) }
                _events.send(
                    PatternBuilderEvent.Saved(
                        patternId = pattern.id,
                        goal = editing.goal,
                        startSession = startSession,
                    ),
                )
            }
        }
    }
