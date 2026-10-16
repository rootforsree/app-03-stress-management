package com.sreecharan.stressrelief.ui.onboarding

import com.sreecharan.stressrelief.data.repository.SettingsRepository
import com.sreecharan.stressrelief.notifications.AuraReminderScheduler
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class OnboardingViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SettingsRepository
    private lateinit var scheduler: AuraReminderScheduler
    private lateinit var viewModel: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        viewModel = OnboardingViewModel(repository, testDispatcher, scheduler)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial page is zero of three`() {
        assertEquals(
            OnboardingUiState.Paging(page = 0, totalPages = 3),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `next advances the page`() {
        viewModel.onNext()

        assertEquals(
            OnboardingUiState.Paging(page = 1, totalPages = 3),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `page changes update state`() {
        viewModel.onPageChanged(2)

        assertEquals(
            OnboardingUiState.Paging(page = 2, totalPages = 3),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `next on the last page persists completion and navigates home`() =
        runTest(testDispatcher) {
            viewModel.onNext() // page 1
            viewModel.onNext() // page 2 (last)
            viewModel.onNext() // finish
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setOnboardingCompleted() }
            coVerify(exactly = 1) { scheduler.scheduleAll() }
            assertEquals(OnboardingEvent.NavigateHome, viewModel.events.first())
        }

    @Test
    fun `skip persists completion just like the complete path`() =
        runTest(testDispatcher) {
            // Regression test for the app-02 bug: the skip path must persist too.
            viewModel.onSkip()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setOnboardingCompleted() }
            coVerify(exactly = 1) { scheduler.scheduleAll() }
            assertEquals(OnboardingEvent.NavigateHome, viewModel.events.first())
        }

    @Test
    fun `finish persists the reminders opt-in choice`() =
        runTest(testDispatcher) {
            viewModel.onRemindersOptInChanged(false)
            viewModel.onSkip()
            advanceUntilIdle()

            coVerify(exactly = 1) { repository.setRemindersEnabled(false) }
            coVerify(exactly = 1) { scheduler.scheduleAll() }
        }
}
