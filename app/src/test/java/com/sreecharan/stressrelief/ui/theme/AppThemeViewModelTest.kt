package com.sreecharan.stressrelief.ui.theme

import app.cash.turbine.test
import com.sreecharan.stressrelief.data.model.ThemeMode
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AppThemeViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SettingsRepository
    private lateinit var viewModel: AppThemeViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository =
            mockk(relaxed = true) {
                every { themeMode } returns flowOf(ThemeMode.DARK)
                every { dynamicColor } returns flowOf(true)
                every { onboardingCompleted } returns flowOf(true)
            }
        viewModel = AppThemeViewModel(repository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme state combines mode and dynamic color`() =
        runTest(testDispatcher) {
            viewModel.uiState.test {
                assertEquals(AppThemeUiState.Loading, awaitItem())
                assertEquals(
                    AppThemeUiState.Ready(themeMode = ThemeMode.DARK, dynamicColor = true),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `onboarding completed mirrors the repository flow`() =
        runTest(testDispatcher) {
            viewModel.onboardingCompleted.test {
                assertEquals(false, awaitItem())
                assertEquals(true, awaitItem())
            }
        }

    @Test
    fun `light mode without dynamic color is reported as-is`() =
        runTest(testDispatcher) {
            val lightRepository =
                mockk<SettingsRepository>(relaxed = true) {
                    every { themeMode } returns flowOf(ThemeMode.LIGHT)
                    every { dynamicColor } returns flowOf(false)
                    every { onboardingCompleted } returns flowOf(false)
                }
            val lightViewModel = AppThemeViewModel(lightRepository)

            lightViewModel.uiState.test {
                assertEquals(AppThemeUiState.Loading, awaitItem())
                assertEquals(
                    AppThemeUiState.Ready(themeMode = ThemeMode.LIGHT, dynamicColor = false),
                    awaitItem(),
                )
            }
        }
}
