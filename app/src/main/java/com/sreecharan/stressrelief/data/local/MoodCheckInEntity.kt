package com.sreecharan.stressrelief.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_checkins")
data class MoodCheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val moodScore: Int,
    val activitiesJson: String,
    val note: String = "",
)
