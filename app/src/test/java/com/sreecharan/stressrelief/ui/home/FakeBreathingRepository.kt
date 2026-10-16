package com.sreecharan.stressrelief.ui.home

import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.ProgramProgress
import com.sreecharan.stressrelief.data.repository.BreathingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Configurable test fake for BreathingRepository. */
class FakeBreathingRepository(
    sessions: List<BreathingSession> = emptyList(),
    programs: List<ProgramProgress> = emptyList(),
) : BreathingRepository {
    val sessionsFlow = MutableStateFlow(sessions)
    val programsFlow = MutableStateFlow(programs)
    val recordedSessions = mutableListOf<BreathingSession>()
    val completedDays = mutableListOf<Pair<String, Int>>()

    override fun observeRecentSessions(limit: Int): Flow<List<BreathingSession>> = sessionsFlow

    override suspend fun recordSession(
        goal: String,
        patternId: String,
        durationSec: Int,
        completed: Boolean,
    ) {
        recordedSessions +=
            BreathingSession(
                id = recordedSessions.size.toLong(),
                startMs = System.currentTimeMillis(),
                durationSec = durationSec,
                goal = goal,
                patternId = patternId,
                completed = completed,
            )
    }

    override fun observePrograms(): Flow<List<ProgramProgress>> = programsFlow

    override suspend fun completeProgramDay(
        programId: String,
        day: Int,
    ) {
        completedDays += programId to day
    }
}
