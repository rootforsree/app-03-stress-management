package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sreecharan.stressrelief.data.catalog.BreathingPatterns
import com.sreecharan.stressrelief.data.catalog.Programs
import com.sreecharan.stressrelief.data.model.ProgramProgress
import com.sreecharan.stressrelief.data.repository.BreathingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class DayStatus {
    DONE,
    CURRENT,
    LOCKED,
}

@Immutable
data class ProgramDayUi(
    val day: Int,
    val title: String,
    val minutes: Int,
    val patternId: String,
    val status: DayStatus,
)

@Immutable
data class ProgramUi(
    val id: String,
    val title: String,
    val description: String,
    val days: ImmutableList<ProgramDayUi>,
)

@Immutable
sealed interface ProgramsUiState {
    data object Loading : ProgramsUiState

    data class Content(
        val programs: ImmutableList<ProgramUi>,
    ) : ProgramsUiState

    data object Error : ProgramsUiState
}

sealed interface ProgramsEvent {
    data class StartProgramSession(
        val programId: String,
        val day: Int,
        val goal: String,
        val patternId: String,
    ) : ProgramsEvent
}

/**
 * Seven-day programs. Stored [ProgramProgress] rows are joined with the
 * static catalog via [Programs.withProgress], so unstarted programs show
 * day 1 as current and the rest locked. Completing a day's breathing
 * session calls [BreathingRepository.completeProgramDay] from the session
 * screen; this screen only launches it.
 */
@HiltViewModel
class ProgramsViewModel
    @Inject
    constructor(
        private val breathingRepository: BreathingRepository,
    ) : ViewModel() {
        private val retryTrigger = MutableStateFlow(0)

        private val _events = Channel<ProgramsEvent>(Channel.BUFFERED)
        val events: Flow<ProgramsEvent> = _events.receiveAsFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val uiState: StateFlow<ProgramsUiState> =
            retryTrigger
                .flatMapLatest { programsFlow() }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgramsUiState.Loading)

        fun onDayClick(
            programId: String,
            day: Int,
            patternId: String,
            status: DayStatus,
        ) {
            if (status != DayStatus.CURRENT) return
            val goal = BreathingPatterns.byId(patternId)?.goal ?: "CALM"
            _events.trySend(ProgramsEvent.StartProgramSession(programId, day, goal, patternId))
        }

        fun onRetry() {
            retryTrigger.value += 1
        }

        private fun programsFlow(): Flow<ProgramsUiState> =
            breathingRepository
                .observePrograms()
                .map<List<ProgramProgress>, ProgramsUiState> { stored ->
                    ProgramsUiState.Content(buildPrograms(stored))
                }.catch { emit(ProgramsUiState.Error) }

        private fun buildPrograms(stored: List<ProgramProgress>): ImmutableList<ProgramUi> {
            val joined = Programs.withProgress(stored)
            return Programs.all
                .map { program ->
                    val progress = joined.first { it.programId == program.id }
                    val completed = progress.completedDays.toSet()
                    val days =
                        program.days
                            .map { day ->
                                val status =
                                    when {
                                        day.day in completed || day.day < progress.currentDay -> DayStatus.DONE
                                        day.day == progress.currentDay -> DayStatus.CURRENT
                                        else -> DayStatus.LOCKED
                                    }
                                ProgramDayUi(
                                    day = day.day,
                                    title = day.title,
                                    minutes = day.minutes,
                                    patternId = day.patternId,
                                    status = status,
                                )
                            }.toImmutableList()
                    ProgramUi(
                        id = program.id,
                        title = program.title,
                        description = program.description,
                        days = days,
                    )
                }.toImmutableList()
        }
    }
