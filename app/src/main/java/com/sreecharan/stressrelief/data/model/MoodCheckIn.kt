package com.sreecharan.stressrelief.data.model

data class MoodCheckIn(
    val id: Long,
    val timestampMs: Long,
    val moodScore: Int,
    val activities: List<String>,
    val note: String,
)
