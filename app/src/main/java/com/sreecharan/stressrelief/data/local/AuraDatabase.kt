package com.sreecharan.stressrelief.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MoodCheckInEntity::class,
        BreathingSessionEntity::class,
        JournalEntryEntity::class,
        ProgramProgressEntity::class,
        DailyActivityEntity::class,
        SleepSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun moodCheckInDao(): MoodCheckInDao

    abstract fun breathingSessionDao(): BreathingSessionDao

    abstract fun journalDao(): JournalDao

    abstract fun programProgressDao(): ProgramProgressDao

    abstract fun dailyActivityDao(): DailyActivityDao

    abstract fun sleepSessionDao(): SleepSessionDao
}
