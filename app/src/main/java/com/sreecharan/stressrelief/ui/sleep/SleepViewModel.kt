package com.sreecharan.stressrelief.ui.sleep

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SleepLayerUiState(
    val layer: SleepSoundLayer,
    val isEnabled: Boolean,
    val volume: Float,
)

@Immutable
sealed interface SleepUiState {
    data object Loading : SleepUiState

    data class Content(
        val layers: ImmutableList<SleepLayerUiState> = persistentListOf(),
        /** Null = infinite. */
        val timerMinutes: Int? = 30,
        val canBegin: Boolean = false,
    ) : SleepUiState

    companion object {
        val TIMER_OPTIONS: List<Int?> = listOf(15, 30, 45, 60, null)
    }
}

sealed interface SleepEvent {
    data object NavigateToSession : SleepEvent
}

/**
 * Sleep mixer screen: toggleable generated sound layers with per-layer
 * volume, a timer picker, and the "Begin wind-down" entry to the session.
 * All sound layers are free for everyone.
 */
@HiltViewModel
class SleepViewModel
    @Inject
    constructor(
        private val sessionConfig: SleepSessionConfig,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SleepUiState>(SleepUiState.Loading)
        val uiState: StateFlow<SleepUiState> = _uiState.asStateFlow()

        private val _events = Channel<SleepEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        /** Applies the transform to the Content branch; no-op while Loading. */
        private fun updateContent(transform: (SleepUiState.Content) -> SleepUiState.Content) {
            val current = _uiState.value as? SleepUiState.Content ?: return
            _uiState.value = transform(current)
        }

        init {
            _uiState.value =
                SleepUiState.Content(
                    layers =
                        SleepSoundLayers.ALL
                            .map { layer ->
                                SleepLayerUiState(
                                    layer = layer,
                                    isEnabled = layer.id == "rain",
                                    volume = 0.7f,
                                )
                            }.toImmutableList(),
                )
            refreshCanBegin()
        }

        fun onLayerToggled(
            layerId: String,
            enabled: Boolean,
        ) {
            updateContent { state ->
                state.copy(
                    layers =
                        state.layers
                            .map {
                                if (it.layer.id == layerId) it.copy(isEnabled = enabled) else it
                            }.toImmutableList(),
                )
            }
            refreshCanBegin()
        }

        fun onVolumeChanged(
            layerId: String,
            volume: Float,
        ) {
            updateContent { state ->
                state.copy(
                    layers =
                        state.layers
                            .map {
                                if (it.layer.id == layerId) {
                                    it.copy(volume = volume.coerceIn(0f, 1f))
                                } else {
                                    it
                                }
                            }.toImmutableList(),
                )
            }
        }

        fun onTimerSelected(timerMinutes: Int?) {
            updateContent { it.copy(timerMinutes = timerMinutes) }
        }

        fun onBeginClicked() {
            val state = _uiState.value as? SleepUiState.Content ?: return
            val enabled =
                state.layers
                    .filter { it.isEnabled }
                    .associate { it.layer.id to it.volume }
            if (enabled.isEmpty()) return
            sessionConfig.layers = enabled
            sessionConfig.timerMinutes = state.timerMinutes
            viewModelScope.launch { _events.send(SleepEvent.NavigateToSession) }
        }

        private fun refreshCanBegin() {
            updateContent { state ->
                state.copy(
                    canBegin = state.layers.any { it.isEnabled },
                )
            }
        }
    }
