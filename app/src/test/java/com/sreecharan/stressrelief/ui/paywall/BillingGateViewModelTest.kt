package com.sreecharan.stressrelief.ui.paywall

import android.app.Activity
import com.sreecharan.stressrelief.data.billing.BillingPlan
import com.sreecharan.stressrelief.data.billing.BillingRepository
import com.sreecharan.stressrelief.data.billing.EntitlementState
import com.sreecharan.stressrelief.data.billing.PurchaseResult
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

private class FakeBillingRepository : BillingRepository {
    val entitlementState = MutableStateFlow<EntitlementState>(EntitlementState.Loading)
    override val entitlement: StateFlow<EntitlementState> = entitlementState
    override val plans: StateFlow<List<BillingPlan>?> = MutableStateFlow(null)

    override suspend fun refresh() = Unit

    override suspend fun purchase(
        activity: Activity,
        plan: BillingPlan,
    ): PurchaseResult = PurchaseResult.Cancelled

    override suspend fun restore(): Boolean = false
}

@OptIn(ExperimentalCoroutinesApi::class)
class BillingGateViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `entitlement passes through to the gate`() =
        runTest(testDispatcher) {
            val billing = FakeBillingRepository()
            val viewModel = BillingGateViewModel(billing)

            assertEquals(EntitlementState.Loading, viewModel.entitlement.value)

            billing.entitlementState.value =
                EntitlementState.Ready(isPremium = true, status = null)

            assertEquals(
                EntitlementState.Ready(isPremium = true, status = null),
                viewModel.entitlement.value,
            )
        }
}
