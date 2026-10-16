package com.sreecharan.stressrelief.di

import android.content.Context
import androidx.room.Room
import com.sreecharan.stressrelief.data.local.AuraDatabase
import com.sreecharan.stressrelief.data.local.BreathingSessionDao
import com.sreecharan.stressrelief.data.local.DailyActivityDao
import com.sreecharan.stressrelief.data.local.JournalDao
import com.sreecharan.stressrelief.data.local.MoodCheckInDao
import com.sreecharan.stressrelief.data.local.ProgramProgressDao
import com.sreecharan.stressrelief.data.local.SleepSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AuraDatabase =
        Room
            .databaseBuilder(context, AuraDatabase::class.java, "aura.db")
            .build()

    @Provides
    fun provideMoodCheckInDao(database: AuraDatabase): MoodCheckInDao = database.moodCheckInDao()

    @Provides
    fun provideBreathingSessionDao(database: AuraDatabase): BreathingSessionDao = database.breathingSessionDao()

    @Provides
    fun provideJournalDao(database: AuraDatabase): JournalDao = database.journalDao()

    @Provides
    fun provideProgramProgressDao(database: AuraDatabase): ProgramProgressDao = database.programProgressDao()

    @Provides
    fun provideDailyActivityDao(database: AuraDatabase): DailyActivityDao = database.dailyActivityDao()

    @Provides
    fun provideSleepSessionDao(database: AuraDatabase): SleepSessionDao = database.sleepSessionDao()
}
