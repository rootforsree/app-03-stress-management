package com.sreecharan.stressrelief.data.model

data class BreathingSession(
    val id: Long,
    val startMs: Long,
    val durationSec: Int,
    val goal: String,
    val patternId: String,
    val completed: Boolean,
)
