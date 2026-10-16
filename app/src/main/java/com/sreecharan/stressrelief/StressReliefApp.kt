package com.sreecharan.stressrelief

import android.app.Application
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class StressReliefApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Billing stays disabled when no key is baked in (free tier fully works).
        if (BuildConfig.REVENUECAT_API_KEY.isNotBlank()) {
            Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(
                PurchasesConfiguration
                    .Builder(this, BuildConfig.REVENUECAT_API_KEY)
                    .build(),
            )
        }
    }
}
