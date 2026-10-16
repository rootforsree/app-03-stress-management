package com.sreecharan.stressrelief.ui.paywall

import android.app.Activity
import app.cash.turbine.test
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.billing.BillingPlan
import com.sreecharan.stressrelief.data.billing.BillingRepository
import com.sreecharan.stressrelief.data.billing.EntitlementState
import com.sreecharan.stressrelief.data.billing.PurchaseResult
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeBillingRepository : BillingRepository {
    val entitlementState = MutableStateFlow<EntitlementState>(EntitlementState.Loading)
    override val entitlement: StateFlow<EntitlementState> = entitlementState

    val plansState = MutableStateFlow<List<BillingPlan>?>(null)
    override val plans: StateFlow<List<BillingPlan>?> = plansState

    var purchaseResult: PurchaseResult = PurchaseResult.Cancelled
    var restoreResult: Boolean = false
    var refreshCalls = 0

    override suspend fun refresh() {
        refreshCalls++
    }

    override suspend fun purchase(
        activity: Activity,
        plan: BillingPlan,
    ): PurchaseResult = purchaseResult

    override suspend fun restore(): Boolean = restoreResult
}

private fun monthlyPlan() =
    BillingPlan(
        packageId = "\$rc_monthly",
        title = "Monthly",
        price = "$4.99",
        pricePerPeriod = "$4.99/mo",
        isAnnual = false,
        isBestValue = false,
    )

private fun annualPlan() =
    BillingPlan(
        packageId = "\$rc_annual",
        title = "Yearly",
        price = "$29.99",
        pricePerPeriod = "$29.99/yr",
        isAnnual = true,
        isBestValue = true,
    )

private fun lifetimePlan() =
    BillingPlan(
        packageId = "\$rc_lifetime",
        title = "Lifetime",
        price = "$79.00",
        pricePerPeriod = "$79.00 once",
        isAnnual = false,
        isLifetime = true,
        isBestValue = false,
    )

/** Unwraps the Content branch; the plans collector always produces it. */
private fun PaywallUiState.content(): PaywallUiState.Content =
    this as? PaywallUiState.Content ?: error("expected PaywallUiState.Content")

@OptIn(ExperimentalCoroutinesApi::class)
class PaywallViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var billing: FakeBillingRepository
    private lateinit var launchContext: PaywallLaunchContext

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        billing = FakeBillingRepository()
        launchContext = PaywallLaunchContext()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `annual plan is preselected when plans arrive`() =
        runTest(testDispatcher) {
            val viewModel = PaywallViewModel(billing, launchContext)
            billing.plansState.value = listOf(monthlyPlan(), lifetimePlan(), annualPlan())
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                "\$rc_annual",
                viewModel.uiState.value
                    .content()
                    .selectedPlanId,
            )
        }

    @Test
    fun `lifetime is preselected when no annual plan exists`() =
        runTest(testDispatcher) {
            val viewModel = PaywallViewModel(billing, launchContext)
            billing.plansState.value = listOf(monthlyPlan(), lifetimePlan())
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                "\$rc_lifetime",
                viewModel.uiState.value
                    .content()
                    .selectedPlanId,
            )
        }

    @Test
    fun `selecting a plan updates state and clears errors`() =
        runTest(testDispatcher) {
            val viewModel = PaywallViewModel(billing, launchContext)
            billing.plansState.value = listOf(monthlyPlan(), annualPlan())
            testDispatcher.scheduler.runCurrent()

            viewModel.onPlanSelected("\$rc_monthly")

            assertEquals(
                "\$rc_monthly",
                viewModel.uiState.value
                    .content()
                    .selectedPlanId,
            )
            assertNull(
                viewModel.uiState.value
                    .content()
                    .errorMessage,
            )
        }

    @Test
    fun `successful purchase releases the button and waits for entitlement`() =
        runTest(testDispatcher) {
            val viewModel = PaywallViewModel(billing, launchContext)
            billing.plansState.value = listOf(annualPlan())
            testDispatcher.scheduler.runCurrent()
            billing.purchaseResult =
                PurchaseResult.Success(
                    transaction = mockk<com.revenuecat.purchases.models.StoreTransaction>(),
                )

            viewModel.events.test {
                viewModel.onPurchaseClicked(mockk<Activity>())
                testDispatcher.scheduler.runCurrent()
                // Success clears the spinner; the entitlement listener is the
                // single Dismiss sender, so no event arrives here.
                assertFalse(
                    viewModel.uiState.value
                        .content()
                        .isPurchasing,
                )
                expectNoEvents()
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun `cancelled purchase stays on the paywall without error`() =
        runTest(testDispatcher) {
            billing.purchaseResult = PurchaseResult.Cancelled
            val viewModel = PaywallViewModel(billing, launchContext)
            billing.plansState.value = listOf(annualPlan())
            testDispatcher.scheduler.runCurrent()

            viewModel.onPurchaseClicked(mockk<Activity>())
            testDispatcher.scheduler.runCurrent()

            assertFalse(
                viewModel.uiState.value
                    .content()
                    .isPurchasing,
            )
            assertNull(
                viewModel.uiState.value
                    .content()
                    .errorMessage,
            )
        }

    @Test
    fun `failed purchase surfaces the error message`() =
        runTest(testDispatcher) {
            billing.purchaseResult = PurchaseResult.Failed("Network error")
            val viewModel = PaywallViewModel(billing, launchContext)
            billing.plansState.value = listOf(annualPlan())
            testDispatcher.scheduler.runCurrent()

            viewModel.onPurchaseClicked(mockk<Activity>())
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                "Network error",
                viewModel.uiState.value
                    .content()
                    .errorMessage,
            )
            assertFalse(
                viewModel.uiState.value
                    .content()
                    .isPurchasing,
            )
        }

    @Test
    fun `restore with nothing found shows the restore message`() =
        runTest(testDispatcher) {
            billing.restoreResult = false
            val viewModel = PaywallViewModel(billing, launchContext)

            viewModel.onRestoreClicked()
            testDispatcher.scheduler.runCurrent()

            assertEquals(
                R.string.paywall_restore_none,
                viewModel.uiState.value
                    .content()
                    .restoreMessageRes,
            )
            assertFalse(
                viewModel.uiState.value
                    .content()
                    .isRestoring,
            )
        }

    @Test
    fun `billing unavailable sets the unavailable flag`() =
        runTest(testDispatcher) {
            val viewModel = PaywallViewModel(billing, launchContext)
            billing.entitlementState.value = EntitlementState.Unavailable
            testDispatcher.scheduler.runCurrent()

            assertTrue(
                viewModel.uiState.value
                    .content()
                    .billingUnavailable,
            )
        }

    @Test
    fun `premium entitlement emits Dismiss`() =
        runTest(testDispatcher) {
            val viewModel = PaywallViewModel(billing, launchContext)

            viewModel.events.test {
                billing.entitlementState.value =
                    EntitlementState.Ready(isPremium = true, status = null)
                assertEquals(PaywallEvent.Dismiss, awaitItem())
            }
        }

    @Test
    fun `premium-feature launch hides the close button`() =
        runTest(testDispatcher) {
            launchContext.launchFromPremiumFeature()
            val viewModel = PaywallViewModel(billing, launchContext)
            testDispatcher.scheduler.runCurrent()

            assertFalse(
                viewModel.uiState.value
                    .content()
                    .allowClose,
            )
        }

    @Test
    fun `settings launch keeps the close button`() =
        runTest(testDispatcher) {
            launchContext.launchFromSettings()
            val viewModel = PaywallViewModel(billing, launchContext)
            testDispatcher.scheduler.runCurrent()

            assertTrue(
                viewModel.uiState.value
                    .content()
                    .allowClose,
            )
        }

    @Test
    fun `viewmodel refreshes billing on init`() =
        runTest(testDispatcher) {
            PaywallViewModel(billing, launchContext)
            testDispatcher.scheduler.runCurrent()

            assertTrue(billing.refreshCalls >= 1)
        }
}
