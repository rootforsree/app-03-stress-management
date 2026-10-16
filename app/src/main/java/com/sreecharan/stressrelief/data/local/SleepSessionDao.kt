package com.sreecharan.stressrelief.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepSessionDao {
    @Query("SELECT * FROM sleep_sessions ORDER BY startMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SleepSessionEntity>>

    @Insert
    suspend fun insert(entity: SleepSessionEntity)
}
