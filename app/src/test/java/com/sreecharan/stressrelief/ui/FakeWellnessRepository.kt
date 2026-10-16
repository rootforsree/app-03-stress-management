package com.sreecharan.stressrelief.ui

import com.sreecharan.stressrelief.data.model.InsightsData
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.RecoveryBand
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Test fake for WellnessRepository. Backing flows can be updated mid-test. */
class FakeWellnessRepository(
    moodHistory: List<MoodCheckIn> = emptyList(),
    todayCheckIn: MoodCheckIn? = null,
    journalEntries: List<JournalEntry> = emptyList(),
    streakDays: Int = 0,
    calmMinutes: Int = 0,
    stressScore: StressScore? = null,
) : WellnessRepository {
    val moodHistoryFlow = MutableStateFlow(moodHistory)
    val todayCheckInFlow = MutableStateFlow(todayCheckIn)
    val journalEntriesFlow = MutableStateFlow(journalEntries)
    val streakDaysFlow = MutableStateFlow(streakDays)
    val calmMinutesFlow = MutableStateFlow(calmMinutes)
    var stressScoreResult: StressScore? = stressScore

    val savedMoods = mutableListOf<Triple<Int, List<String>, String>>()
    val deletedJournalIds = mutableListOf<Long>()
    val updatedJournals = mutableListOf<Pair<Long, String>>()
    var journalDoneMarked = false
    var breathingDoneMinutes: Int? = null
    private var nextJournalId = 100L

    override fun observeMoodHistory(days: Int): Flow<List<MoodCheckIn>> = moodHistoryFlow

    override fun observeTodayCheckIn(): Flow<MoodCheckIn?> = todayCheckInFlow

    override suspend fun saveMoodCheckIn(
        score: Int,
        activities: List<String>,
        note: String,
    ) {
        savedMoods += Triple(score, activities, note)
    }

    override fun observeJournalEntries(): Flow<List<JournalEntry>> = journalEntriesFlow

    override fun observeJournalEntry(id: Long): Flow<JournalEntry?> =
        journalEntriesFlow.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun saveJournalEntry(
        prompt: String,
        text: String,
    ): Long {
        val id = nextJournalId++
        journalEntriesFlow.value =
            journalEntriesFlow.value + JournalEntry(id, System.currentTimeMillis(), prompt, text)
        return id
    }

    override suspend fun updateJournalEntry(
        id: Long,
        text: String,
    ) {
        updatedJournals += id to text
        journalEntriesFlow.value =
            journalEntriesFlow.value.map { if (it.id == id) it.copy(text = text) else it }
    }

    override suspend fun deleteJournalEntry(id: Long) {
        deletedJournalIds += id
        journalEntriesFlow.value = journalEntriesFlow.value.filterNot { it.id == id }
    }

    override fun observeStreakDays(): Flow<Int> = streakDaysFlow

    override fun observeTotalCalmMinutes(): Flow<Int> = calmMinutesFlow

    override suspend fun computeStressScore(): StressScore = stressScoreResult ?: StressScore(50, "Steady", emptyList())

    override suspend fun computeInsights(): InsightsData =
        InsightsData(
            sleepNights = emptyList(),
            sleepDebtHours = null,
            sleepNeedHours = 8f,
            sri = null,
            socialJetlagMin = null,
            restingHrToday = null,
            restingHrBaseline = null,
            restingHrZ = null,
            hrvRmssdToday = null,
            recoveryScore = null,
            recoveryBand = RecoveryBand.CALIBRATING,
            restingHrTrendBpmPerDay = null,
            sleepTrendMinPerDay = null,
            insightCards = emptyList(),
            recommendations = emptyList(),
            stepDays = emptyList(),
            stepsToday = null,
            steps7DayAvg = null,
            distanceTodayM = null,
            calibrating = true,
            healthConnected = false,
        )

    override suspend fun markBreathingDone(minutes: Int) {
        breathingDoneMinutes = minutes
    }

    override suspend fun markJournalDone() {
        journalDoneMarked = true
    }
}
