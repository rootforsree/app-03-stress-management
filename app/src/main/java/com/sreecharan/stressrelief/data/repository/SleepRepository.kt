package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.model.SleepSession
import kotlinx.coroutines.flow.Flow

interface SleepRepository {
    suspend fun recordSleepSession(durationMin: Int, sounds: List<String>)

    fun observeRecent(limit: Int): Flow<List<SleepSession>>
}
