package com.sreecharan.stressrelief.data.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * Preferences DataStore used by billing. Kept separate from the proto-backed
 * settings store so a proto schema change can never wipe purchase state.
 */
@Module
@InstallIn(SingletonComponent::class)
object BillingDataStoreModule {
    private val Context.billingDataStore by preferencesDataStore(name = "billing_prefs")

    @Provides
    @Singleton
    @Named("billing")
    fun provideBillingPreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = context.billingDataStore
}

/** Binds the RevenueCat-backed implementation. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BillingModule {
    @Binds
    abstract fun bindBillingRepository(impl: BillingRepositoryImpl): BillingRepository
}
