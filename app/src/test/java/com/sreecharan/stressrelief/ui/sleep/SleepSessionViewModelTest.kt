package com.sreecharan.stressrelief.ui.sleep

import app.cash.turbine.test
import com.sreecharan.stressrelief.ui.FakeSleepRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeSleepAudioPlayer : SleepAudioPlayer {
    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    var lastMix: Map<String, Float> = emptyMap()
    var startCalls = 0
    var stopCalls = 0

    override fun setMix(layers: Map<String, Float>) {
        lastMix = layers
    }

    override fun start() {
        startCalls++
        _isPlaying.value = true
    }

    override fun stop(fadeOutMs: Long) {
        stopCalls++
        _isPlaying.value = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SleepSessionViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var player: FakeSleepAudioPlayer
    private lateinit var repository: FakeSleepRepository
    private lateinit var sessionConfig: SleepSessionConfig

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        player = FakeSleepAudioPlayer()
        repository = FakeSleepRepository()
        sessionConfig = SleepSessionConfig()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun contentOf(viewModel: SleepSessionViewModel): SleepSessionUiState.Content =
        viewModel.uiState.value as SleepSessionUiState.Content

    @Test
    fun `session starts the player with the configured mix`() =
        runTest(testDispatcher) {
            sessionConfig.layers = mapOf("rain" to 0.7f, "ocean" to 0.5f)
            sessionConfig.timerMinutes = 30

            val viewModel = SleepSessionViewModel(sessionConfig, player, repository)
            runCurrent()

            assertEquals(mapOf("rain" to 0.7f, "ocean" to 0.5f), player.lastMix)
            assertEquals(1, player.startCalls)
            assertEquals(1800L, contentOf(viewModel).remainingSec)
            assertEquals(listOf("rain", "ocean"), contentOf(viewModel).layerIds)
        }

    @Test
    fun `timer expiry records the session and finishes`() =
        runTest(testDispatcher) {
            sessionConfig.layers = mapOf("rain" to 0.7f)
            sessionConfig.timerMinutes = 1 // 60 seconds

            val viewModel = SleepSessionViewModel(sessionConfig, player, repository)
            advanceTimeBy(60_000L)
            runCurrent()

            // The Finished event sits buffered in the channel; collect after.
            viewModel.events.test {
                assertEquals(SleepSessionEvent.Finished, awaitItem())
            }

            assertEquals(1, repository.recorded.size)
            assertEquals(1, repository.recorded[0].first)
            assertEquals(listOf("rain"), repository.recorded[0].second)
            assertEquals(1, player.stopCalls)
        }

    @Test
    fun `stopping early records elapsed minutes`() =
        runTest(testDispatcher) {
            sessionConfig.layers = mapOf("brown-noise" to 0.8f)
            sessionConfig.timerMinutes = 30

            val viewModel = SleepSessionViewModel(sessionConfig, player, repository)
            advanceTimeBy(150_000L) // 2.5 minutes in
            runCurrent()

            viewModel.onStopClicked()
            runCurrent()

            viewModel.events.test {
                assertEquals(SleepSessionEvent.Finished, awaitItem())
            }

            assertEquals(1, repository.recorded.size)
            assertEquals(2, repository.recorded[0].first)
            assertTrue(player.stopCalls >= 1)
        }

    @Test
    fun `infinite session never finishes on its own`() =
        runTest(testDispatcher) {
            sessionConfig.layers = mapOf("chime-pads" to 0.6f)
            sessionConfig.timerMinutes = null

            val viewModel = SleepSessionViewModel(sessionConfig, player, repository)
            advanceTimeBy(600_000L) // 10 minutes
            runCurrent()

            assertEquals(null, contentOf(viewModel).remainingSec)
            assertTrue(repository.recorded.isEmpty())
            assertEquals(0, player.stopCalls)

            viewModel.onStopClicked()
            runCurrent()
            assertEquals(10, repository.recorded[0].first)

            viewModel.events.test {
                assertEquals(SleepSessionEvent.Finished, awaitItem())
            }
        }

    @Test
    fun `empty config falls back to rain`() =
        runTest(testDispatcher) {
            // Config untouched: empty mix.
            val viewModel = SleepSessionViewModel(sessionConfig, player, repository)
            runCurrent()

            assertEquals(mapOf("rain" to 0.7f), player.lastMix)
            assertEquals(listOf("rain"), contentOf(viewModel).layerIds)
        }
}
