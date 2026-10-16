package com.sreecharan.stressrelief.data.model

data class SleepSession(
    val id: Long,
    val startMs: Long,
    val durationMin: Int,
    val sounds: List<String>,
)
