package com.sreecharan.stressrelief.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable
import com.sreecharan.stressrelief.data.repository.SleepRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max

@Immutable
sealed interface SleepSessionUiState {
    data object Loading : SleepSessionUiState

    data class Content(
        val layerIds: ImmutableList<String> = persistentListOf(),
        /** Seconds left, or null for an infinite session. */
        val remainingSec: Long? = null,
        val totalSec: Long? = null,
        val elapsedSec: Long = 0L,
        val isPlaying: Boolean = false,
    ) : SleepSessionUiState
}

sealed interface SleepSessionEvent {
    data object Finished : SleepSessionEvent
}

/**
 * Active wind-down player: starts the [SleepAudioPlayer] with the mix handed
 * over by [SleepViewModel], runs the countdown, and records the session with
 * [SleepRepository.recordSleepSession] on stop or timer expiry.
 */
@HiltViewModel
class SleepSessionViewModel
    @Inject
    constructor(
        private val sessionConfig: SleepSessionConfig,
        private val soundEngine: SleepAudioPlayer,
        private val sleepRepository: SleepRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<SleepSessionUiState>(SleepSessionUiState.Loading)
        val uiState: StateFlow<SleepSessionUiState> = _uiState.asStateFlow()

        private val _events = Channel<SleepSessionEvent>(Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        private var tickerJob: Job? = null
        private var finished = false

        init {
            val layers = sessionConfig.layers.ifEmpty { mapOf("rain" to 0.7f) }
            val timerMinutes = sessionConfig.timerMinutes
            val totalSec = timerMinutes?.times(60L)
            _uiState.value =
                SleepSessionUiState.Content(
                    layerIds = layers.keys.toList().toImmutableList(),
                    remainingSec = totalSec,
                    totalSec = totalSec,
                    isPlaying = true,
                )
            soundEngine.setMix(layers)
            soundEngine.start()
            startTicker()
        }

        fun onStopClicked() {
            finishSession()
        }

        private fun startTicker() {
            tickerJob?.cancel()
            tickerJob =
                viewModelScope.launch {
                    while (true) {
                        delay(1000L)
                        val state = _uiState.value as? SleepSessionUiState.Content ?: break
                        val nextElapsed = state.elapsedSec + 1
                        val nextRemaining = state.remainingSec?.minus(1)?.coerceAtLeast(0)
                        _uiState.value =
                            state.copy(elapsedSec = nextElapsed, remainingSec = nextRemaining)
                        if (nextRemaining == 0L) {
                            finishSession()
                            break
                        }
                    }
                }
        }

        private fun finishSession() {
            if (finished) return
            finished = true
            tickerJob?.cancel()
            val state = _uiState.value as? SleepSessionUiState.Content ?: return
            val durationMin = max(1, (state.elapsedSec / 60).toInt())
            soundEngine.stop()
            viewModelScope.launch {
                try {
                    sleepRepository.recordSleepSession(durationMin, state.layerIds)
                } catch (_: Exception) {
                    // Recording must never trap the user on this screen.
                }
                _events.send(SleepSessionEvent.Finished)
            }
        }

        override fun onCleared() {
            super.onCleared()
            if (!finished) {
                soundEngine.stop()
            }
        }
    }
