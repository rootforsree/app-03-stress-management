package com.sreecharan.stressrelief.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BreathingSessionDao {
    @Query("SELECT * FROM breathing_sessions ORDER BY startMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BreathingSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationSec), 0) / 60 FROM breathing_sessions")
    fun observeTotalMinutes(): Flow<Int>

    @Insert
    suspend fun insert(entity: BreathingSessionEntity)
}
