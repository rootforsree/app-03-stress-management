package com.sreecharan.stressrelief.data.model

data class DailyActivity(
    val dateEpochDay: Long,
    val checkInDone: Boolean,
    val breathingMinutes: Int,
    val journalDone: Boolean,
)
