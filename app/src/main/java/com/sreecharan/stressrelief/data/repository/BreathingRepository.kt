package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.ProgramProgress
import kotlinx.coroutines.flow.Flow

interface BreathingRepository {
    fun observeRecentSessions(limit: Int): Flow<List<BreathingSession>>

    suspend fun recordSession(goal: String, patternId: String, durationSec: Int, completed: Boolean)

    fun observePrograms(): Flow<List<ProgramProgress>>

    suspend fun completeProgramDay(programId: String, day: Int)
}
