package com.sreecharan.stressrelief.di

import com.sreecharan.stressrelief.data.health.HealthConnectClient
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the Health Connect client as the UI-facing state provider so
 * ViewModels depend on the narrow interface, not the concrete client.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HealthBindings {
    @Binds
    @Singleton
    abstract fun bindHealthConnectStateProvider(client: HealthConnectClient): HealthConnectStateProvider
}
