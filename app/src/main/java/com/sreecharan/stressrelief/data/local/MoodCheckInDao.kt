package com.sreecharan.stressrelief.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodCheckInDao {
    @Query("SELECT * FROM mood_checkins ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MoodCheckInEntity>>

    @Query(
        "SELECT * FROM mood_checkins WHERE timestampMs >= :dayStartMs AND timestampMs < :dayEndMs " +
            "ORDER BY timestampMs DESC",
    )
    fun observeForDay(dayStartMs: Long, dayEndMs: Long): Flow<List<MoodCheckInEntity>>

    /**
     * Bounded history read: only rows at/after [sinceMs]. Callers must pass a
     * real cutoff — never load the whole table and filter in memory.
     */
    @Query("SELECT * FROM mood_checkins WHERE timestampMs >= :sinceMs ORDER BY timestampMs DESC")
    fun observeSince(sinceMs: Long): Flow<List<MoodCheckInEntity>>

    @Insert
    suspend fun insert(entity: MoodCheckInEntity)
}
