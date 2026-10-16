package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sreecharan.stressrelief.data.catalog.BreathingPattern
import com.sreecharan.stressrelief.data.catalog.BreathingPatterns
import com.sreecharan.stressrelief.data.datastore.CustomPatternStore
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.data.model.CustomPattern
import com.sreecharan.stressrelief.data.repository.BreathingRepository
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.di.IoDispatcher
import com.sreecharan.stressrelief.navigation.BreathingSessionRoute
import com.sreecharan.stressrelief.widget.AuraWidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max

@Immutable
sealed interface BreathingSessionUiState {
    data object Loading : BreathingSessionUiState

    data class Ready(
        val patternName: String,
        val goalTitle: String,
        val totalRounds: Int,
        val totalSeconds: Int,
    ) : BreathingSessionUiState

    data class Active(
        val phase: BreathPhase,
        val phaseProgress: Float,
        val phaseElapsedMs: Long,
        val phaseDurationMs: Long,
        val secondsRemainingInPhase: Int,
        val round: Int,
        val totalRounds: Int,
        val totalProgress: Float,
        val isPaused: Boolean,
        val patternName: String,
        val pace: Float,
    ) : BreathingSessionUiState

    data class Celebrating(
        val minutes: Int,
        val rounds: Int,
    ) : BreathingSessionUiState
}

sealed interface BreathingSessionEvent {
    /** Fired on every phase change; the UI plays a haptic tick. */
    data class PhaseChanged(
        val phase: BreathPhase,
    ) : BreathingSessionEvent

    data object NavigateBack : BreathingSessionEvent
}

private fun BreathingPattern.toSpec(): BreathingPatternSpec =
    BreathingPatternSpec(
        id = id,
        name = name,
        inhaleSec = inhaleSec,
        holdSec = holdSec,
        exhaleSec = exhaleSec,
        holdAfterSec = holdAfterSec,
        rounds = rounds,
    )

private fun CustomPattern.toSpec(): BreathingPatternSpec =
    BreathingPatternSpec(
        id = id,
        name = name,
        inhaleSec = inhaleSec,
        holdSec = holdSec,
        exhaleSec = exhaleSec,
        holdAfterSec = holdAfterSec,
        rounds = rounds,
    )

private val FallbackSpec =
    BreathingPatternSpec(
        id = "4-4-6",
        name = "4-4-6 Calm",
        inhaleSec = 4,
        holdSec = 0,
        exhaleSec = 6,
        holdAfterSec = 0,
        rounds = 6,
    )

/**
 * Resolves a pattern id to a timing spec. Pure function of its inputs, so it
 * is unit-testable without a ViewModel.
 *
 * - "custom-*" ids resolve against the user's saved custom patterns.
 * - Anything else resolves against the static catalog.
 * - Unknown ids fall back to "4-4-6". Never throws.
 */
internal fun resolvePatternSpec(
    patternId: String,
    customPatterns: List<CustomPattern>,
): BreathingPatternSpec =
    runCatching {
        if (patternId.startsWith(CUSTOM_PATTERN_ID_PREFIX)) {
            customPatterns.firstOrNull { it.id == patternId }?.toSpec()
        } else {
            BreathingPatterns.byId(patternId)?.toSpec()
        }
    }.getOrNull() ?: FallbackSpec

private const val CUSTOM_PATTERN_ID_PREFIX = "custom-"

/**
 * Drives one breathing session. A 100ms ticker advances a paced clock
 * through [BreathingPhaseMachine]; every phase change emits
 * [BreathingSessionEvent.PhaseChanged] for the haptic tick.
 *
 * The pattern id resolves asynchronously (custom patterns come from
 * DataStore), so the screen shows Loading until the spec is ready.
 *
 * Finish (natural or early exit) records via the repositories on the IO
 * dispatcher, then emits [BreathingSessionEvent.NavigateBack]. Repository
 * failures are swallowed so a session can never crash at its best moment.
 * A natural finish also writes the session back to Health Connect,
 * fire-and-forget; a false return is silently skipped.
 */
@HiltViewModel
class BreathingSessionViewModel
    @Inject
    constructor(
        private val breathingRepository: BreathingRepository,
        private val wellnessRepository: WellnessRepository,
        private val customPatternStore: CustomPatternStore,
        private val healthConnect: HealthConnectStateProvider,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        savedStateHandle: SavedStateHandle,
        private val widgetUpdater: AuraWidgetUpdater,
    ) : ViewModel() {
        companion object {
            const val TICK_MS = 100L
            const val CELEBRATION_MS = 2_400L
            const val MIN_PACE = 0.5f
            const val MAX_PACE = 2.0f
            private const val DEFAULT_PACE = 1.0f
            private const val MS_PER_SEC = 1_000L
            private const val SECS_PER_MIN = 60
            private const val ROUND_UP_SECS = 30
        }

        private val routeArgs: BreathingSessionRoute = savedStateHandle.toRoute()

        // Resolved asynchronously in init; null until the spec is ready.
        private var spec: BreathingPatternSpec? = null
        private var machine: BreathingPhaseMachine? = null
        private val pace = MutableStateFlow(DEFAULT_PACE)

        private val _uiState =
            MutableStateFlow<BreathingSessionUiState>(BreathingSessionUiState.Loading)
        val uiState: StateFlow<BreathingSessionUiState> = _uiState.asStateFlow()

        private val _events = Channel<BreathingSessionEvent>(Channel.BUFFERED)
        val events: Flow<BreathingSessionEvent> = _events.receiveAsFlow()

        private var programId: String? = null
        private var programDay: Int = -1

        // pacedMs drives the phase machine (pace-adjusted); wallActiveMs is
        // real time spent unpaused, used for recording and Health Connect.
        private var pacedMs = 0L
        private var wallActiveMs = 0L
        private var sessionStartWallMs = 0L
        private var ticker: Job? = null
        private var lastPhase: BreathPhase? = null
        private var finished = false

        init {
            viewModelScope.launch {
                val resolved = resolveSpec(routeArgs.patternId)
                spec = resolved
                machine = BreathingPhaseMachine(resolved)
                sessionStartWallMs = System.currentTimeMillis()
                _uiState.value =
                    BreathingSessionUiState.Ready(
                        patternName = resolved.name,
                        goalTitle = goalTitle(routeArgs.goal),
                        totalRounds = resolved.rounds,
                        totalSeconds = resolved.totalSeconds,
                    )
                startTicker()
            }
        }

        private suspend fun resolveSpec(patternId: String): BreathingPatternSpec =
            runCatching {
                val customs = customPatternStore.patterns.first()
                resolvePatternSpec(patternId, customs)
            }.getOrDefault(FallbackSpec)

        /**
         * Optional program context, supplied by the route when the session was
         * started from a program day. On natural finish the day is completed.
         */
        fun attachProgramContext(
            programId: String?,
            day: Int,
        ) {
            this.programId = programId
            this.programDay = day
        }

        /** Pace multiplier, 0.5x..2x. Takes effect live in the ticker. */
        fun setPace(value: Float) {
            val coerced = value.coerceIn(MIN_PACE, MAX_PACE)
            if (pace.value == coerced) return
            pace.value = coerced
            val current = _uiState.value as? BreathingSessionUiState.Active ?: return
            _uiState.value = current.copy(pace = coerced)
        }

        fun onPauseResume() {
            val current = _uiState.value as? BreathingSessionUiState.Active ?: return
            if (finished) return
            if (current.isPaused) {
                _uiState.value = current.copy(isPaused = false)
                startTicker()
            } else {
                ticker?.cancel()
                ticker = null
                _uiState.value = current.copy(isPaused = true)
            }
        }

        fun onExitEarly() {
            if (finished) {
                _events.trySend(BreathingSessionEvent.NavigateBack)
                return
            }
            ticker?.cancel()
            ticker = null
            finished = true
            val durationSec = (wallActiveMs / MS_PER_SEC).toInt()
            if (durationSec <= 0) {
                _events.trySend(BreathingSessionEvent.NavigateBack)
                return
            }
            viewModelScope.launch {
                withContext(ioDispatcher) {
                    runCatching {
                        breathingRepository.recordSession(
                            goal = routeArgs.goal,
                            patternId = spec?.id ?: FallbackSpec.id,
                            durationSec = durationSec,
                            completed = false,
                        )
                        wellnessRepository.markBreathingDone(max(1, durationSec / SECS_PER_MIN))
                    }
                    runCatching { widgetUpdater.refresh() }
                }
                _events.send(BreathingSessionEvent.NavigateBack)
            }
        }

        private fun startTicker() {
            ticker?.cancel()
            ticker =
                viewModelScope.launch {
                    while (isActive && !finished) {
                        delay(TICK_MS)
                        advance(TICK_MS)
                    }
                }
        }

        private fun advance(deltaMs: Long) {
            val machine = machine ?: return
            if (finished) return
            wallActiveMs += deltaMs
            pacedMs += (deltaMs * pace.value).toLong()
            val pos = machine.positionAt(pacedMs)
            if (pos == null) {
                onSessionFinished()
                return
            }
            if (pos.phase != lastPhase) {
                lastPhase = pos.phase
                _events.trySend(BreathingSessionEvent.PhaseChanged(pos.phase))
            }
            _uiState.value =
                BreathingSessionUiState.Active(
                    phase = pos.phase,
                    phaseProgress = pos.phaseProgress,
                    phaseElapsedMs = pos.phaseElapsedMs,
                    phaseDurationMs = pos.phaseDurationMs,
                    secondsRemainingInPhase = pos.secondsRemainingInPhase,
                    round = pos.round,
                    totalRounds = pos.totalRounds,
                    totalProgress = pos.totalProgress,
                    isPaused = false,
                    patternName = spec?.name.orEmpty(),
                    pace = pace.value,
                )
        }

        private fun onSessionFinished() {
            finished = true
            ticker?.cancel()
            ticker = null
            val durationSec = (wallActiveMs / MS_PER_SEC).toInt()
            val minutes = max(1, (durationSec + ROUND_UP_SECS) / SECS_PER_MIN)
            _uiState.value =
                BreathingSessionUiState.Celebrating(
                    minutes = minutes,
                    rounds = spec?.rounds ?: 0,
                )
            val startMs = sessionStartWallMs
            val endMs = startMs + wallActiveMs
            // Health Connect write-back: fire-and-forget on IO. A false
            // return (or a throw, though the contract says never) is a silent
            // skip; the celebration must never wait on it.
            viewModelScope.launch(ioDispatcher) {
                runCatching { healthConnect.writeBreathingSession(startMs, endMs) }
            }
            viewModelScope.launch {
                withContext(ioDispatcher) {
                    runCatching {
                        breathingRepository.recordSession(
                            goal = routeArgs.goal,
                            patternId = spec?.id ?: FallbackSpec.id,
                            durationSec = durationSec,
                            completed = true,
                        )
                        wellnessRepository.markBreathingDone(minutes)
                        val pid = programId
                        if (pid != null && programDay >= 0) {
                            breathingRepository.completeProgramDay(pid, programDay)
                        }
                    }
                    runCatching { widgetUpdater.refresh() }
                }
                delay(CELEBRATION_MS)
                _events.send(BreathingSessionEvent.NavigateBack)
            }
        }
    }
