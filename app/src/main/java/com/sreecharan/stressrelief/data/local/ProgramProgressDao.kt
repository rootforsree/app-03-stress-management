package com.sreecharan.stressrelief.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgramProgressDao {
    @Query("SELECT * FROM program_progress")
    fun observeAll(): Flow<List<ProgramProgressEntity>>

    @Query("SELECT * FROM program_progress WHERE programId = :programId")
    fun observeById(programId: String): Flow<ProgramProgressEntity?>

    @Query("SELECT * FROM program_progress WHERE programId = :programId")
    suspend fun getById(programId: String): ProgramProgressEntity?

    @Upsert
    suspend fun upsert(entity: ProgramProgressEntity)
}
