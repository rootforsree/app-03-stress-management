package com.sreecharan.stressrelief.ui.sleep

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/** All sound layers are free; no billing involved. */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var sessionConfig: SleepSessionConfig

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        sessionConfig = SleepSessionConfig()
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
            val viewModel = SleepViewModel(sessionConfig)
            testDispatcher.scheduler.runCurrent()

            val state = contentOf(viewModel)
            val rain = state.layers.first { it.layer.id == "rain" }
            assertTrue(rain.isEnabled)
            assertTrue(state.canBegin)
        }

    @Test
    fun `disabling all layers disables begin`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(sessionConfig)
            testDispatcher.scheduler.runCurrent()

            viewModel.onLayerToggled("rain", false)

            assertFalse(contentOf(viewModel).canBegin)
        }

    @Test
    fun `any layer can be toggled on for free`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(sessionConfig)
            testDispatcher.scheduler.runCurrent()

            viewModel.onLayerToggled("forest", true)

            val forest = contentOf(viewModel).layers.first { it.layer.id == "forest" }
            assertTrue(forest.isEnabled)
        }

    @Test
    fun `volume change is coerced to 0-1`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(sessionConfig)
            testDispatcher.scheduler.runCurrent()

            viewModel.onVolumeChanged("rain", 2.0f)
            assertEquals(1.0f, contentOf(viewModel).layers.first { it.layer.id == "rain" }.volume)

            viewModel.onVolumeChanged("rain", -1.0f)
            assertEquals(0.0f, contentOf(viewModel).layers.first { it.layer.id == "rain" }.volume)
        }

    @Test
    fun `timer selection updates state`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(sessionConfig)
            testDispatcher.scheduler.runCurrent()

            viewModel.onTimerSelected(null)
            assertEquals(null, contentOf(viewModel).timerMinutes)

            viewModel.onTimerSelected(45)
            assertEquals(45, contentOf(viewModel).timerMinutes)
        }

    @Test
    fun `begin hands the mix to the session config and navigates`() =
        runTest(testDispatcher) {
            val viewModel = SleepViewModel(sessionConfig)
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
            val viewModel = SleepViewModel(sessionConfig)
            testDispatcher.scheduler.runCurrent()
            viewModel.onLayerToggled("rain", false)

            viewModel.onBeginClicked()
            testDispatcher.scheduler.runCurrent()

            assertTrue(sessionConfig.layers.isEmpty())
        }
}
