package com.sreecharan.stressrelief.data.model

data class JournalEntry(
    val id: Long,
    val timestampMs: Long,
    val prompt: String,
    val text: String,
)
