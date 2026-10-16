package com.sreecharan.stressrelief.data.reminders

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

private val Context.reminderDataStore by preferencesDataStore(name = "aura_reminders")

/** Wires the reminder preferences DataStore and repository. */
@Module
@InstallIn(SingletonComponent::class)
object ReminderModule {
    @Provides
    @Singleton
    @Named("reminders")
    fun provideReminderPreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.reminderDataStore

    @Provides
    @Singleton
    fun provideReminderRepository(
        @Named("reminders") dataStore: DataStore<Preferences>,
    ): ReminderRepository = ReminderRepositoryImpl(dataStore)
}
