package com.sreecharan.stressrelief.ui.breathe

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.sreecharan.stressrelief.widget.AuraWidgetUpdater
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        breathingRepo = FakeBreathingRepository()
        wellnessRepo = FakeWellnessRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(
        patternId: String = "4-4-6",
        goal: String = "CALM",
    ): BreathingSessionViewModel =
        BreathingSessionViewModel(
            breathingRepository = breathingRepo,
            wellnessRepository = wellnessRepo,
            ioDispatcher = testDispatcher,
            savedStateHandle = SavedStateHandle(mapOf("goal" to goal, "patternId" to patternId)),
            widgetUpdater = mockk<AuraWidgetUpdater>(relaxed = true),
        )

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
}
