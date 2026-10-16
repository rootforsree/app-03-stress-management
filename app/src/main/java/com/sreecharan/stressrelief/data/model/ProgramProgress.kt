package com.sreecharan.stressrelief.data.model

data class ProgramProgress(
    val programId: String,
    val currentDay: Int,
    val completedDays: List<Int>,
    val startedMs: Long,
)
