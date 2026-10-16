package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.StressScore
import kotlinx.coroutines.flow.Flow

interface WellnessRepository {
    fun observeMoodHistory(days: Int): Flow<List<MoodCheckIn>>

    fun observeTodayCheckIn(): Flow<MoodCheckIn?>

    suspend fun saveMoodCheckIn(score: Int, activities: List<String>, note: String)

    fun observeJournalEntries(): Flow<List<JournalEntry>>

    /** Single entry by id (null when deleted); avoids loading the whole table. */
    fun observeJournalEntry(id: Long): Flow<JournalEntry?>

    suspend fun saveJournalEntry(prompt: String, text: String): Long

    suspend fun updateJournalEntry(id: Long, text: String)

    suspend fun deleteJournalEntry(id: Long)

    fun observeStreakDays(): Flow<Int>

    fun observeTotalCalmMinutes(): Flow<Int>

    suspend fun computeStressScore(): StressScore

    suspend fun markBreathingDone(minutes: Int)

    suspend fun markJournalDone()
}
