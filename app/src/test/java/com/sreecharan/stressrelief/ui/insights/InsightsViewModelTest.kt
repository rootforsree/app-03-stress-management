package com.sreecharan.stressrelief.ui.insights

import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.ui.FakeSleepRepository
import com.sreecharan.stressrelief.ui.FakeWellnessRepository
import com.sreecharan.stressrelief.ui.runViewModelTest
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InsightsViewModelTest {
    private val zone = ZoneId.systemDefault()

    private fun ms(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test
    fun `loads computed content for premium users`() =
        runViewModelTest { dispatcher ->
            val today = LocalDate.now(zone)
            val repo =
                FakeWellnessRepository(
                    moodHistory =
                        listOf(
                            MoodCheckIn(1, ms(today), 5, listOf("exercise"), ""),
                            MoodCheckIn(2, ms(today.minusDays(1)), 4, listOf("exercise"), ""),
                            MoodCheckIn(3, ms(today.minusDays(2)), 2, listOf("work"), ""),
                        ),
                )
            val vm = InsightsViewModel(repo, FakeSleepRepository(), dispatcher)
            assertTrue(vm.uiState.value is InsightsUiState.Loading)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is InsightsUiState.Content)
            assertEquals(InsightsStats.TREND_DAYS, state.trend.size)
            assertEquals(42, state.monthCells.size)
            assertEquals("exercise", state.topActivities.first().tag)
            assertEquals(10, state.consistencyPct, "3 of 30 days")
            assertTrue(state.monthLabel.isNotBlank())
        }

    @Test
    fun `empty history still produces a valid content state`() =
        runViewModelTest { dispatcher ->
            val vm = InsightsViewModel(FakeWellnessRepository(), FakeSleepRepository(), dispatcher)
            advanceUntilIdle()
            val state = vm.uiState.value
            assertTrue(state is InsightsUiState.Content)
            assertTrue(state.trend.all { it == null })
            assertTrue(state.topActivities.isEmpty())
            assertEquals(0, state.consistencyPct)
        }

    @Test
    fun `retry moves back through loading`() =
        runViewModelTest { dispatcher ->
            val vm = InsightsViewModel(FakeWellnessRepository(), FakeSleepRepository(), dispatcher)
            advanceUntilIdle()
            assertTrue(vm.uiState.value is InsightsUiState.Content)
            vm.retry()
            assertTrue(vm.uiState.value is InsightsUiState.Loading)
            advanceUntilIdle()
            assertTrue(vm.uiState.value is InsightsUiState.Content)
        }
}
