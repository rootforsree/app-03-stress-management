package com.sreecharan.stressrelief.data.billing

import android.app.Activity
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import com.sreecharan.stressrelief.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * RevenueCat-backed billing. Single source of truth is RevenueCat's
 * server-side entitlement state; a DataStore boolean caches the last known
 * premium flag so cold start settles fast without flashing the free tier.
 *
 * Entitlement id in the RevenueCat dashboard: "premium".
 */
@Singleton
class BillingRepositoryImpl
    @Inject
    constructor(
        @Named("billing") private val preferences: DataStore<Preferences>,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : BillingRepository {
        companion object {
            const val ENTITLEMENT_ID = "premium"

            private val KEY_PREMIUM_CACHED = booleanPreferencesKey("premium_cached")
        }

        private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

        private val _entitlement = MutableStateFlow<EntitlementState>(EntitlementState.Loading)
        override val entitlement: StateFlow<EntitlementState> = _entitlement.asStateFlow()

        private val _plans = MutableStateFlow<List<BillingPlan>?>(null)
        override val plans: StateFlow<List<BillingPlan>?> = _plans.asStateFlow()

        private var packagesById: Map<String, Package> = emptyMap()

        private val isConfigured: Boolean
            get() =
                try {
                    Purchases.sharedInstance
                    true
                } catch (_: UninitializedPropertyAccessException) {
                    false
                } catch (_: IllegalStateException) {
                    false
                }

        init {
            scope.launch {
                // Apply the cached flag first: fast, no free-tier flash.
                val cached: Boolean =
                    preferences.data
                        .map { it[KEY_PREMIUM_CACHED] ?: false }
                        .first()
                if (isConfigured) {
                    _entitlement.value =
                        EntitlementState.Ready(isPremium = cached, status = null)
                    refresh()
                } else {
                    _entitlement.value = EntitlementState.Unavailable
                }
            }
        }

        override suspend fun refresh() {
            if (!isConfigured) {
                _entitlement.value = EntitlementState.Unavailable
                return
            }
            try {
                val offerings = awaitOfferings()
                applyOfferings(offerings)
                val info = awaitCustomerInfo()
                applyCustomerInfo(info)
            } catch (_: Exception) {
                // Keep the cached state; a retry happens on next foreground
                // via the paywall or settings screen.
            }
        }

        override suspend fun purchase(
            activity: Activity,
            plan: BillingPlan,
        ): PurchaseResult {
            if (!isConfigured) return PurchaseResult.Failed("Billing is not configured.")
            val rcPackage =
                packagesById[plan.packageId]
                    ?: return PurchaseResult.Failed("Plan is no longer available.")
            val params = PurchaseParams.Builder(activity, rcPackage).build()
            return suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.purchase(
                    params,
                    object : PurchaseCallback {
                        override fun onCompleted(
                            storeTransaction: StoreTransaction,
                            customerInfo: CustomerInfo,
                        ) {
                            applyCustomerInfo(customerInfo)
                            if (cont.isActive) cont.resume(PurchaseResult.Success(storeTransaction))
                        }

                        override fun onError(
                            error: PurchasesError,
                            userCancelled: Boolean,
                        ) {
                            if (cont.isActive) {
                                cont.resume(
                                    if (userCancelled) {
                                        PurchaseResult.Cancelled
                                    } else {
                                        PurchaseResult.Failed(error.toUserMessage())
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }

        override suspend fun restore(): Boolean {
            if (!isConfigured) return false
            return try {
                val info = awaitRestore()
                applyCustomerInfo(info)
                info.entitlements[ENTITLEMENT_ID]?.isActive == true
            } catch (_: Exception) {
                false
            }
        }

        private fun applyOfferings(offerings: Offerings) {
            val offering = offerings.current
            if (offering == null) {
                _plans.value = emptyList()
                return
            }
            packagesById = offering.availablePackages.associateBy { it.identifier }
            _plans.value =
                offering.availablePackages
                    .mapNotNull { pkg ->
                        val product = pkg.product
                        val typeName = pkg.packageType.name
                        val isAnnual = typeName == "ANNUAL"
                        val isLifetime = typeName == "LIFETIME"
                        BillingPlan(
                            packageId = pkg.identifier,
                            title = product.title.substringBefore(" (").ifBlank { product.title },
                            price = product.price.formatted,
                            pricePerPeriod =
                                when {
                                    isLifetime -> "${product.price.formatted} once"
                                    isAnnual -> "${product.price.formatted}/yr"
                                    else -> "${product.price.formatted}/mo"
                                },
                            isAnnual = isAnnual,
                            isLifetime = isLifetime,
                            isBestValue = isAnnual,
                        )
                    }
                    // Annual first (preselected), then lifetime, then monthly.
                    .sortedWith(
                        compareByDescending<BillingPlan> { it.isAnnual }
                            .thenByDescending { it.isLifetime },
                    )
        }

        private fun applyCustomerInfo(info: CustomerInfo) {
            val entitlement = info.entitlements[ENTITLEMENT_ID]
            val isPremium = entitlement?.isActive == true
            val status =
                entitlement?.let {
                    SubscriptionStatus(
                        isPremium = isPremium,
                        isInTrial = it.periodType.name == "TRIAL",
                        // Billing issue with access still active = grace period / account hold.
                        inGracePeriod = it.billingIssueDetectedAt != null && isPremium,
                        willRenew = it.willRenew,
                        expirationDate = it.expirationDate?.toInstant(),
                        productId = it.productIdentifier,
                        isLifetime = it.periodType.name == "LIFETIME",
                    )
                }
            _entitlement.value = EntitlementState.Ready(isPremium, status)
            scope.launch {
                preferences.edit { prefs ->
                    prefs[KEY_PREMIUM_CACHED] = isPremium
                }
            }
        }

        private suspend fun awaitOfferings(): Offerings =
            suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.getOfferings(
                    object : ReceiveOfferingsCallback {
                        override fun onReceived(offerings: Offerings) {
                            if (cont.isActive) cont.resume(offerings)
                        }

                        override fun onError(error: PurchasesError) {
                            if (cont.isActive) {
                                cont.resumeWith(
                                    Result.failure(RuntimeException(error.toUserMessage())),
                                )
                            }
                        }
                    },
                )
            }

        private suspend fun awaitCustomerInfo(): CustomerInfo =
            suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.getCustomerInfo(
                    object : ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            if (cont.isActive) cont.resume(customerInfo)
                        }

                        override fun onError(error: PurchasesError) {
                            if (cont.isActive) {
                                cont.resumeWith(
                                    Result.failure(RuntimeException(error.toUserMessage())),
                                )
                            }
                        }
                    },
                )
            }

        private suspend fun awaitRestore(): CustomerInfo =
            suspendCancellableCoroutine { cont ->
                Purchases.sharedInstance.restorePurchases(
                    object : ReceiveCustomerInfoCallback {
                        override fun onReceived(customerInfo: CustomerInfo) {
                            if (cont.isActive) cont.resume(customerInfo)
                        }

                        override fun onError(error: PurchasesError) {
                            if (cont.isActive) {
                                cont.resumeWith(
                                    Result.failure(RuntimeException(error.toUserMessage())),
                                )
                            }
                        }
                    },
                )
            }
    }

private fun PurchasesError.toUserMessage(): String =
    when (code.name) {
        "PurchaseNotAllowedError" -> "Purchases are not allowed on this device."
        "PurchaseInvalidError" -> "This purchase is not valid."
        "ProductNotAvailableForPurchaseError" -> "This plan is not available right now."
        "ProductAlreadyPurchasedError" -> "You already own this plan."
        "NetworkError" -> "Network error. Check your connection and try again."
        "StoreProblemError" -> "The Play Store had a problem. Try again."
        else -> message.ifBlank { "Something went wrong. Try again." }
    }
