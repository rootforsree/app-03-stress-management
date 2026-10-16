package com.sreecharan.stressrelief.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    private val Context.auraDataStore by preferencesDataStore(name = "aura_prefs")

    @Provides
    @Singleton
    @Named("aura")
    fun provideAuraDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.auraDataStore
}
