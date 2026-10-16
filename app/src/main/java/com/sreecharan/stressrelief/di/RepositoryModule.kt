package com.sreecharan.stressrelief.di

import com.sreecharan.stressrelief.data.datastore.SettingsRepositoryImpl
import com.sreecharan.stressrelief.data.repository.BreathingRepository
import com.sreecharan.stressrelief.data.repository.BreathingRepositoryImpl
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import com.sreecharan.stressrelief.data.repository.SleepRepository
import com.sreecharan.stressrelief.data.repository.SleepRepositoryImpl
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.data.repository.WellnessRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindWellnessRepository(impl: WellnessRepositoryImpl): WellnessRepository

    @Binds
    abstract fun bindBreathingRepository(impl: BreathingRepositoryImpl): BreathingRepository

    @Binds
    abstract fun bindSleepRepository(impl: SleepRepositoryImpl): SleepRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
