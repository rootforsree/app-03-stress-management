package com.sreecharan.stressrelief.ui.breathe

import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.ProgramProgress
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.repository.BreathingRepository
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeBreathingRepository : BreathingRepository {
    data class RecordedSession(
        val goal: String,
        val patternId: String,
        val durationSec: Int,
        val completed: Boolean,
    )

    val recordedSessions = mutableListOf<RecordedSession>()
    val completedDays = mutableListOf<Pair<String, Int>>()
    val programsFlow = MutableStateFlow<List<ProgramProgress>>(emptyList())

    override fun observeRecentSessions(limit: Int): Flow<List<BreathingSession>> = flowOf(emptyList())

    override suspend fun recordSession(
        goal: String,
        patternId: String,
        durationSec: Int,
        completed: Boolean,
    ) {
        recordedSessions += RecordedSession(goal, patternId, durationSec, completed)
    }

    override fun observePrograms(): Flow<List<ProgramProgress>> = programsFlow

    override suspend fun completeProgramDay(
        programId: String,
        day: Int,
    ) {
        completedDays += programId to day
    }
}

class FakeWellnessRepository : WellnessRepository {
    val breathingMinutesCalls = mutableListOf<Int>()
    var journalDoneCalls = 0

    override fun observeMoodHistory(days: Int): Flow<List<MoodCheckIn>> = flowOf(emptyList())

    override fun observeTodayCheckIn(): Flow<MoodCheckIn?> = flowOf(null)

    override suspend fun saveMoodCheckIn(
        score: Int,
        activities: List<String>,
        note: String,
    ) = Unit

    override fun observeJournalEntries(): Flow<List<JournalEntry>> = flowOf(emptyList())

    override suspend fun saveJournalEntry(
        prompt: String,
        text: String,
    ): Long = -1L

    override suspend fun updateJournalEntry(
        id: Long,
        text: String,
    ) = Unit

    override suspend fun deleteJournalEntry(id: Long) = Unit

    override fun observeStreakDays(): Flow<Int> = flowOf(0)

    override fun observeTotalCalmMinutes(): Flow<Int> = flowOf(0)

    override suspend fun computeStressScore(): StressScore =
        StressScore(score = 50, label = "Moderate", factors = emptyList())

    override suspend fun markBreathingDone(minutes: Int) {
        breathingMinutesCalls += minutes
    }

    override suspend fun markJournalDone() {
        journalDoneCalls += 1
    }
}
