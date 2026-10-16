package com.sreecharan.stressrelief.di

import android.content.Context
import com.sreecharan.stressrelief.data.health.HealthConnectClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object HealthModule {
    @Provides
    @Singleton
    fun provideHealthConnectClient(
        @ApplicationContext context: Context,
    ): HealthConnectClient = HealthConnectClient(context)
}
