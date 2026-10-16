package com.sreecharan.stressrelief.data.local

import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.DailyActivity
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.ProgramProgress
import com.sreecharan.stressrelief.data.model.SleepSession

internal fun MoodCheckInEntity.toDomain(): MoodCheckIn =
    MoodCheckIn(
        id = id,
        timestampMs = timestampMs,
        moodScore = moodScore,
        activities = JsonCodec.decodeStrings(activitiesJson),
        note = note,
    )

internal fun BreathingSessionEntity.toDomain(): BreathingSession =
    BreathingSession(
        id = id,
        startMs = startMs,
        durationSec = durationSec,
        goal = goal,
        patternId = patternId,
        completed = completed,
    )

internal fun JournalEntryEntity.toDomain(): JournalEntry =
    JournalEntry(
        id = id,
        timestampMs = timestampMs,
        prompt = prompt,
        text = text,
    )

internal fun JournalEntry.toEntity(): JournalEntryEntity =
    JournalEntryEntity(
        id = id,
        timestampMs = timestampMs,
        prompt = prompt,
        text = text,
    )

internal fun ProgramProgressEntity.toDomain(): ProgramProgress =
    ProgramProgress(
        programId = programId,
        currentDay = currentDay,
        completedDays = JsonCodec.decodeInts(completedDaysJson),
        startedMs = startedMs,
    )

internal fun ProgramProgress.toEntity(): ProgramProgressEntity =
    ProgramProgressEntity(
        programId = programId,
        currentDay = currentDay,
        completedDaysJson = JsonCodec.encodeInts(completedDays),
        startedMs = startedMs,
    )

internal fun DailyActivityEntity.toDomain(): DailyActivity =
    DailyActivity(
        dateEpochDay = dateEpochDay,
        checkInDone = checkInDone,
        breathingMinutes = breathingMinutes,
        journalDone = journalDone,
    )

internal fun SleepSessionEntity.toDomain(): SleepSession =
    SleepSession(
        id = id,
        startMs = startMs,
        durationMin = durationMin,
        sounds = JsonCodec.decodeStrings(soundsJson),
    )
