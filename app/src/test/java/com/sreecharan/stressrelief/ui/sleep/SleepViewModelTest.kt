package com.sreecharan.stressrelief.ui.sleep

import android.app.Activity
import app.cash.turbine.test
import com.sreecharan.stressrelief.data.billing.BillingPlan
import com.sreecharan.stressrelief.data.billing.BillingRepository
import com.sreecharan.stressrelief.data.billing.EntitlementState
import com.sreecharan.stressrelief.data.billing.PurchaseResult
import com.sreecharan.stressrelief.ui.paywall.PaywallLaunchContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
class SleepViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var billing: FakeBillingRepository
    private lateinit var sessionConfig: SleepSessionConfig
    private lateinit var paywallLaunchContext: PaywallLaunchContext

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        billing = FakeBillingRepository()
        sessionConfig = SleepSessionConfig()
        paywallLaunchContext = PaywallLaunchContext()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun contentOf(viewModel: SleepViewModel): SleepUiState.Content =
        viewModel.uiState.value as SleepUiState.Content

    @Test
    fun `rain is enabled by default and begin is allowed`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(billing, sessionConfig, paywallLaunchContext)
            testDispatcher.scheduler.runCurrent()

            val state = contentOf(viewModel)
            val rain = state.layers.first { it.layer.id == "rain" }
            assertTrue(rain.isEnabled)
            assertTrue(state.canBegin)
        }

    @Test
    fun `disabling all layers disables begin`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(billing, sessionConfig, paywallLaunchContext)
            testDispatcher.scheduler.runCurrent()

            viewModel.onLayerToggled("rain", false)

            assertFalse(contentOf(viewModel).canBegin)
        }

    @Test
    fun `toggling a locked premium layer opens the paywall`() =
        runTest(testDispatcher) {
            billing.entitlementState.value = EntitlementState.Ready(isPremium = false, status = null)
            val viewModel = SleepViewModel(billing, sessionConfig, paywallLaunchContext)
            testDispatcher.scheduler.runCurrent()

            viewModel.events.test {
                viewModel.onLayerToggled("forest", true)
                runCurrent()
                assertEquals(SleepEvent.OpenPaywall, awaitItem())
            }
            val forest = contentOf(viewModel).layers.first { it.layer.id == "forest" }
            assertFalse(forest.isEnabled)
            assertFalse(paywallLaunchContext.isCloseAllowed())
        }

    @Test
    fun `premium layers unlock for premium users`() =
        runTest(testDispatcher) {
            billing.entitlementState.value = EntitlementState.Ready(isPremium = true, status = null)
            val viewModel = SleepViewModel(billing, sessionConfig, paywallLaunchContext)
            testDispatcher.scheduler.runCurrent()

            viewModel.onLayerToggled("forest", true)

            val forest = contentOf(viewModel).layers.first { it.layer.id == "forest" }
            assertTrue(forest.isUnlocked)
            assertTrue(forest.isEnabled)
        }

    @Test
    fun `timer selection updates state`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(billing, sessionConfig, paywallLaunchContext)
            testDispatcher.scheduler.runCurrent()

            viewModel.onTimerSelected(null)
            assertEquals(null, contentOf(viewModel).timerMinutes)

            viewModel.onTimerSelected(45)
            assertEquals(45, contentOf(viewModel).timerMinutes)
        }

    @Test
    fun `begin hands the mix to the session config and navigates`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(billing, sessionConfig, paywallLaunchContext)
            testDispatcher.scheduler.runCurrent()
            viewModel.onLayerToggled("ocean", true)
            viewModel.onVolumeChanged("ocean", 0.5f)
            viewModel.onTimerSelected(15)

            viewModel.events.test {
                viewModel.onBeginClicked()
                runCurrent()
                assertEquals(SleepEvent.NavigateToSession, awaitItem())
            }

            assertEquals(mapOf("rain" to 0.7f, "ocean" to 0.5f), sessionConfig.layers)
            assertEquals(15, sessionConfig.timerMinutes)
        }

    @Test
    fun `begin with no layers enabled does nothing`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(billing, sessionConfig, paywallLaunchContext)
            testDispatcher.scheduler.runCurrent()
            viewModel.onLayerToggled("rain", false)

            viewModel.onBeginClicked()
            testDispatcher.scheduler.runCurrent()

            assertTrue(sessionConfig.layers.isEmpty())
        }
}
