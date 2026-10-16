package com.sreecharan.stressrelief.ui.breathe

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sreecharan.stressrelief.data.datastore.CustomPatternStore
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.data.model.CustomPattern
import com.sreecharan.stressrelief.widget.AuraWidgetUpdater
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BreathingSessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var breathingRepo: FakeBreathingRepository
    private lateinit var wellnessRepo: FakeWellnessRepository
    private lateinit var healthConnect: HealthConnectStateProvider

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        breathingRepo = FakeBreathingRepository()
        wellnessRepo = FakeWellnessRepository()
        healthConnect = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeCustomStore(customs: List<CustomPattern> = emptyList()): CustomPatternStore {
        val store = mockk<CustomPatternStore>()
        every { store.patterns } returns flowOf(customs)
        every { store.breathingGoalMin } returns flowOf(10)
        return store
    }

    private fun TestScope.createVm(
        patternId: String = "4-4-6",
        goal: String = "CALM",
        customs: List<CustomPattern> = emptyList(),
    ): BreathingSessionViewModel =
        BreathingSessionViewModel(
            breathingRepository = breathingRepo,
            wellnessRepository = wellnessRepo,
            customPatternStore = fakeCustomStore(customs),
            healthConnect = healthConnect,
            ioDispatcher = testDispatcher,
            savedStateHandle = SavedStateHandle(mapOf("goal" to goal, "patternId" to patternId)),
            widgetUpdater = mockk<AuraWidgetUpdater>(relaxed = true),
        ).also { runCurrent() }

    @Test
    fun `starts ready then runs inhale`() =
        runTest(testDispatcher) {
            val vm = createVm()
            assertTrue(vm.uiState.value is BreathingSessionUiState.Ready)
            testScheduler.advanceTimeBy(150)
            val active = vm.uiState.value as BreathingSessionUiState.Active
            assertEquals(BreathPhase.INHALE, active.phase)
            assertEquals(1, active.round)
            assertEquals(6, active.totalRounds)
        }

    @Test
    fun `ticker advances inhale to exhale`() =
        runTest(testDispatcher) {
            val vm = createVm()
            testScheduler.advanceTimeBy(150)
            assertEquals(BreathPhase.INHALE, (vm.uiState.value as BreathingSessionUiState.Active).phase)
            testScheduler.advanceTimeBy(4_000)
            assertEquals(BreathPhase.EXHALE, (vm.uiState.value as BreathingSessionUiState.Active).phase)
        }

    @Test
    fun `phase changes emit haptic tick events`() =
        runTest(testDispatcher) {
            val vm = createVm()
            vm.events.test {
                testScheduler.advanceTimeBy(150)
                assertEquals(BreathingSessionEvent.PhaseChanged(BreathPhase.INHALE), awaitItem())
                testScheduler.advanceTimeBy(4_000)
                assertEquals(BreathingSessionEvent.PhaseChanged(BreathPhase.EXHALE), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `pause freezes the timer and resume continues`() =
        runTest(testDispatcher) {
            val vm = createVm()
            testScheduler.advanceTimeBy(2_000)
            vm.onPauseResume()
            val paused = vm.uiState.value as BreathingSessionUiState.Active
            assertTrue(paused.isPaused)
            val remainingAtPause = paused.secondsRemainingInPhase
            testScheduler.advanceTimeBy(5_000)
            val stillPaused = vm.uiState.value as BreathingSessionUiState.Active
            assertTrue(stillPaused.isPaused)
            assertEquals(remainingAtPause, stillPaused.secondsRemainingInPhase)
            vm.onPauseResume()
            assertFalse((vm.uiState.value as BreathingSessionUiState.Active).isPaused)
        }

    @Test
    fun `pace change speeds up the session`() =
        runTest(testDispatcher) {
            val vm = createVm()
            testScheduler.advanceTimeBy(150)
            vm.setPace(2.0f)
            assertEquals(2.0f, (vm.uiState.value as BreathingSessionUiState.Active).pace)
            // At 2x the 4s inhale completes in 2s of wall time.
            testScheduler.advanceTimeBy(2_000)
            assertEquals(BreathPhase.EXHALE, (vm.uiState.value as BreathingSessionUiState.Active).phase)
        }

    @Test
    fun `pace is coerced to 0_5x to 2x`() =
        runTest(testDispatcher) {
            val vm = createVm()
            testScheduler.advanceTimeBy(150)
            vm.setPace(9.0f)
            assertEquals(2.0f, (vm.uiState.value as BreathingSessionUiState.Active).pace)
            vm.setPace(0.0f)
            assertEquals(0.5f, (vm.uiState.value as BreathingSessionUiState.Active).pace)
        }

    @Test
    fun `natural finish records a completed session and the program day`() =
        runTest(testDispatcher) {
            val vm = createVm()
            vm.attachProgramContext("calm-reset", 2)
            // 4-4-6 is 6 rounds of 10s = 60s.
            testScheduler.advanceTimeBy(61_000)
            val celebrating = vm.uiState.value as BreathingSessionUiState.Celebrating
            assertEquals(1, celebrating.minutes)
            testScheduler.advanceUntilIdle()
            val recorded = breathingRepo.recordedSessions.single()
            assertEquals("CALM", recorded.goal)
            assertEquals("4-4-6", recorded.patternId)
            assertEquals(60, recorded.durationSec)
            assertTrue(recorded.completed)
            assertEquals(listOf(1), wellnessRepo.breathingMinutesCalls)
            assertEquals(listOf("calm-reset" to 2), breathingRepo.completedDays)
            // Health Connect write-back covers the wall-clock session window.
            val startSlot = slot<Long>()
            val endSlot = slot<Long>()
            coVerify { healthConnect.writeBreathingSession(capture(startSlot), capture(endSlot)) }
            assertEquals(60_000L, endSlot.captured - startSlot.captured)
            vm.events.test {
                var event: BreathingSessionEvent = awaitItem()
                while (event !is BreathingSessionEvent.NavigateBack) {
                    event = awaitItem()
                }
            }
        }

    @Test
    fun `early exit records a partial session and navigates back`() =
        runTest(testDispatcher) {
            val vm = createVm()
            testScheduler.advanceTimeBy(5_000)
            vm.onExitEarly()
            testScheduler.advanceUntilIdle()
            val recorded = breathingRepo.recordedSessions.single()
            assertFalse(recorded.completed)
            assertEquals(5, recorded.durationSec)
            vm.events.test {
                var event: BreathingSessionEvent = awaitItem()
                while (event !is BreathingSessionEvent.NavigateBack) {
                    event = awaitItem()
                }
            }
        }

    @Test
    fun `exit with no elapsed time records nothing but still navigates back`() =
        runTest(testDispatcher) {
            val vm = createVm()
            vm.onExitEarly()
            testScheduler.advanceUntilIdle()
            assertTrue(breathingRepo.recordedSessions.isEmpty())
            vm.events.test {
                assertEquals(BreathingSessionEvent.NavigateBack, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `custom pattern id resolves from the store`() =
        runTest(testDispatcher) {
            val custom =
                CustomPattern(
                    id = "custom-abc123",
                    name = "My Calm",
                    inhaleSec = 6,
                    holdSec = 2,
                    exhaleSec = 6,
                    holdAfterSec = 2,
                    rounds = 5,
                )
            val vm = createVm(patternId = "custom-abc123", customs = listOf(custom))
            val ready = vm.uiState.value as BreathingSessionUiState.Ready
            assertEquals("My Calm", ready.patternName)
            assertEquals(5, ready.totalRounds)
        }

    @Test
    fun `unknown pattern id falls back to 4-4-6`() =
        runTest(testDispatcher) {
            val vm = createVm(patternId = "nope-not-real")
            val ready = vm.uiState.value as BreathingSessionUiState.Ready
            assertEquals("4-4-6 Calm", ready.patternName)
            assertEquals(6, ready.totalRounds)
        }

    @Test
    fun `unknown custom id falls back to 4-4-6`() =
        runTest(testDispatcher) {
            val vm = createVm(patternId = "custom-deleted")
            val ready = vm.uiState.value as BreathingSessionUiState.Ready
            assertEquals("4-4-6 Calm", ready.patternName)
        }
}
