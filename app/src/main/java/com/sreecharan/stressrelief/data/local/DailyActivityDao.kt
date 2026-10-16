package com.sreecharan.stressrelief.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activity WHERE dateEpochDay = :epochDay")
    fun observeDay(epochDay: Long): Flow<DailyActivityEntity?>

    @Query("SELECT * FROM daily_activity ORDER BY dateEpochDay DESC LIMIT :n")
    fun observeLastNDays(n: Int): Flow<List<DailyActivityEntity>>

    @Query("SELECT * FROM daily_activity WHERE dateEpochDay = :epochDay")
    suspend fun getDay(epochDay: Long): DailyActivityEntity?

    @Upsert
    suspend fun upsert(entity: DailyActivityEntity)
}
