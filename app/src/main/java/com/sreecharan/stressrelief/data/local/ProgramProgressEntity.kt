package com.sreecharan.stressrelief.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "program_progress")
data class ProgramProgressEntity(
    @PrimaryKey val programId: String,
    val currentDay: Int,
    val completedDaysJson: String,
    val startedMs: Long,
)
