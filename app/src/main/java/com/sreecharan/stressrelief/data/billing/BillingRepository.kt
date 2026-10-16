package com.sreecharan.stressrelief.data.billing

import android.app.Activity
import com.revenuecat.purchases.models.StoreTransaction
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

/**
 * High-level subscription state exposed to the UI.
 *
 * [Loading] is the cold-start neutral state: the UI must not assume free or
 * paid until RevenueCat reports back, so paying users never see the free tier
 * flash on launch.
 */
sealed interface EntitlementState {
    data object Loading : EntitlementState

    data class Ready(
        val isPremium: Boolean,
        val status: SubscriptionStatus?,
    ) : EntitlementState

    /**
     * RevenueCat was not configured (no API key). Billing is unavailable and
     * the app runs in free tier. Used for local dev builds without a key.
     */
    data object Unavailable : EntitlementState
}

/**
 * Details of the current subscription, when one exists. All fields come from
 * RevenueCat's server-side entitlement state (grace periods, account hold,
 * trials and renewals are resolved there).
 */
data class SubscriptionStatus(
    val isPremium: Boolean,
    /** True while the user is inside the 3-day free trial. */
    val isInTrial: Boolean,
    /** True while RevenueCat reports a billing issue but keeps access. */
    val inGracePeriod: Boolean,
    val willRenew: Boolean,
    val expirationDate: Instant?,
    val productId: String?,
    /** True when the entitlement comes from a lifetime purchase. */
    val isLifetime: Boolean = false,
)

/**
 * A purchasable plan surfaced on the paywall.
 */
data class BillingPlan(
    /** RevenueCat package identifier, e.g. "$rc_monthly". */
    val packageId: String,
    val title: String,
    /** Localized price, e.g. "$4.99". */
    val price: String,
    /** Localized per-period price, e.g. "$4.99/mo". */
    val pricePerPeriod: String,
    val isAnnual: Boolean,
    val isLifetime: Boolean = false,
    val isBestValue: Boolean,
)

/**
 * Result of a purchase attempt.
 */
sealed interface PurchaseResult {
    data class Success(val transaction: StoreTransaction) : PurchaseResult

    /** The user backed out of the Google Play sheet. Not an error. */
    data object Cancelled : PurchaseResult

    data class Failed(val message: String) : PurchaseResult
}

interface BillingRepository {
    /**
     * Current entitlement. Starts at [EntitlementState.Loading] on cold start;
     * the cached DataStore value is applied first so the UI settles fast, then
     * RevenueCat's live state replaces it.
     */
    val entitlement: StateFlow<EntitlementState>

    /** Plans from the current RevenueCat offering, or null while loading. */
    val plans: StateFlow<List<BillingPlan>?>

    /** Refreshes plans and entitlement from RevenueCat. */
    suspend fun refresh()

    /**
     * Launches the Google Play purchase sheet for [plan]. Must be called with
     * an Activity context. Returns [PurchaseResult.Cancelled] when the user
     * dismisses the sheet; that is normal, not an error.
     */
    suspend fun purchase(activity: Activity, plan: BillingPlan): PurchaseResult

    /**
     * Restores purchases from the Play account (reinstalls, device switches).
     * Returns true when a premium entitlement was found.
     */
    suspend fun restore(): Boolean
}
