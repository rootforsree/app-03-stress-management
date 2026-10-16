package com.sreecharan.stressrelief.ui.settings

import app.cash.turbine.test
import com.sreecharan.stressrelief.data.model.QuietHours
import com.sreecharan.stressrelief.data.model.ThemeMode
import com.sreecharan.stressrelief.data.repository.SettingsRepository
import com.sreecharan.stressrelief.notifications.AuraReminderScheduler
import io.mockk.coVerify
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SettingsRepository
    private lateinit var scheduler: AuraReminderScheduler
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true) {
            every { themeMode } returns flowOf(ThemeMode.SYSTEM)
            every { dynamicColor } returns flowOf(false)
            every { remindersEnabled } returns flowOf(true)
            every { quietHours } returns flowOf(QuietHours(23, 30, 6, 0))
        }
        scheduler = mockk(relaxed = true)
        viewModel = SettingsViewModel(repository, testDispatcher, scheduler)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState combines all four preference flows`() = runTest(testDispatcher) {
        viewModel.uiState.test {
            assertEquals(SettingsUiState.Loading, awaitItem())
            assertEquals(
                SettingsUiState.Ready(
                    themeMode = ThemeMode.SYSTEM,
                    dynamicColor = false,
                    remindersEnabled = true,
                    quietHours = QuietHours(23, 30, 6, 0),
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `setThemeMode delegates to the repository`() = runTest(testDispatcher) {
        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun `setDynamicColor delegates to the repository`() = runTest(testDispatcher) {
        viewModel.setDynamicColor(true)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setDynamicColor(true) }
    }

    @Test
    fun `setRemindersEnabled delegates to the repository`() = runTest(testDispatcher) {
        viewModel.setRemindersEnabled(false)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setRemindersEnabled(false) }
        coVerify(exactly = 1) { scheduler.scheduleAll() }
    }

    @Test
    fun `setQuietHours delegates to the repository`() = runTest(testDispatcher) {
        val quiet = QuietHours(22, 0, 7, 0)
        viewModel.setQuietHours(quiet)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setQuietHours(quiet) }
        coVerify(exactly = 1) { scheduler.scheduleAll() }
    }

    @Test
    fun `resetAllData restores every default`() = runTest(testDispatcher) {
        viewModel.resetAllData()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.SYSTEM) }
        coVerify(exactly = 1) { repository.setDynamicColor(false) }
        coVerify(exactly = 1) { repository.setRemindersEnabled(false) }
        coVerify(exactly = 1) { repository.setQuietHours(QuietHours(22, 0, 7, 0)) }
    }
}
