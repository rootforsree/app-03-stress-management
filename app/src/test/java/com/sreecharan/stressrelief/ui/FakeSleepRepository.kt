package com.sreecharan.stressrelief.ui

import com.sreecharan.stressrelief.data.model.SleepSession
import com.sreecharan.stressrelief.data.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test fake for SleepRepository. */
class FakeSleepRepository(
    sessions: List<SleepSession> = emptyList(),
) : SleepRepository {
    val sessionsFlow = MutableStateFlow(sessions)
    val recorded = mutableListOf<Pair<Int, List<String>>>()

    override suspend fun recordSleepSession(durationMin: Int, sounds: List<String>) {
        recorded += durationMin to sounds
    }

    override fun observeRecent(limit: Int): Flow<List<SleepSession>> = sessionsFlow
}
