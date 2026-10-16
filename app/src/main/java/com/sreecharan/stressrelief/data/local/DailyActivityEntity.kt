package com.sreecharan.stressrelief.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val dateEpochDay: Long,
    val checkInDone: Boolean,
    val breathingMinutes: Int,
    val journalDone: Boolean,
)
