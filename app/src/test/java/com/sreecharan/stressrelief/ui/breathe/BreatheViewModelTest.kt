package com.sreecharan.stressrelief.ui.breathe

import app.cash.turbine.test
import com.sreecharan.stressrelief.data.datastore.CustomPatternStore
import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.CustomPattern
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class BreatheViewModelTest {
    private val breathingRepo = FakeBreathingRepository()

    private fun fakeCustomStore(
        customs: List<CustomPattern> = emptyList(),
        goalMin: Int = 10,
    ): CustomPatternStore {
        val store = mockk<CustomPatternStore>()
        every { store.patterns } returns flowOf(customs)
        every { store.breathingGoalMin } returns flowOf(goalMin)
        return store
    }

    private fun TestScope.createVm(
        customs: List<CustomPattern> = emptyList(),
        goalMin: Int = 10,
    ): BreatheViewModel =
        BreatheViewModel(breathingRepo, fakeCustomStore(customs, goalMin)).also { runCurrent() }

    private fun contentOf(vm: BreatheViewModel): BreatheUiState.Content =
        vm.uiState.value as BreatheUiState.Content

    @Test
    fun `emits four goal cards from the catalog`() =
        runTest {
            val vm = createVm()
            val content = contentOf(vm)
            assertEquals(4, content.goals.size)
            assertEquals(listOf("CALM", "SLEEP", "FOCUS", "SOS"), content.goals.map { it.goal })
            assertNull(content.selectedGoal)
            content.goals.forEach { goal ->
                assertTrue(goal.patterns.isNotEmpty(), "goal ${goal.goal} has no patterns")
            }
        }

    @Test
    fun `goal click selects the goal`() =
        runTest {
            val vm = createVm()
            vm.onGoalClick("SLEEP")
            assertEquals("SLEEP", contentOf(vm).selectedGoal)
        }

    @Test
    fun `picker dismiss clears the selection`() =
        runTest {
            val vm = createVm()
            vm.onGoalClick("FOCUS")
            vm.onPickerDismiss()
            assertNull(contentOf(vm).selectedGoal)
        }

    @Test
    fun `pattern click emits open session and closes the picker`() =
        runTest {
            val vm = createVm()
            vm.onGoalClick("CALM")
            vm.events.test {
                vm.onPatternClick("4-4-6")
                assertEquals(BreatheEvent.OpenSession("CALM", "4-4-6"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(contentOf(vm).selectedGoal)
        }

    @Test
    fun `pattern click without a selected goal is ignored`() =
        runTest {
            val vm = createVm()
            vm.events.test {
                vm.onPatternClick("4-4-6")
                expectNoEvents()
            }
        }

    @Test
    fun `create custom emits open pattern builder and closes the picker`() =
        runTest {
            val vm = createVm()
            vm.onGoalClick("CALM")
            vm.events.test {
                vm.onCreateCustomClick()
                assertEquals(BreatheEvent.OpenPatternBuilder, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertNull(contentOf(vm).selectedGoal)
        }

    @Test
    fun `custom patterns appear in state`() =
        runTest {
            val custom =
                CustomPattern(
                    id = "custom-1",
                    name = "Mine",
                    inhaleSec = 4,
                    holdSec = 0,
                    exhaleSec = 6,
                    holdAfterSec = 0,
                    rounds = 4,
                )
            val vm = createVm(customs = listOf(custom))
            assertEquals(listOf(custom), contentOf(vm).customPatterns)
        }

    @Test
    fun `goal change writes through to the store`() =
        runTest {
            val store = fakeCustomStore()
            val vm = BreatheViewModel(breathingRepo, store).also { runCurrent() }
            vm.onGoalChange(25)
            runCurrent()
            coVerify { store.setBreathingGoalMin(25) }
        }

    @Test
    fun `diary groups sessions into the last seven days`() =
        runTest {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            fun session(
                id: Long,
                daysBack: Long,
                durationSec: Int,
            ) = BreathingSession(
                id = id,
                startMs = today.minusDays(daysBack).atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000,
                durationSec = durationSec,
                goal = "CALM",
                patternId = "4-4-6",
                completed = true,
            )
            breathingRepo.sessionsFlow.value =
                listOf(
                    session(1, 0, 600),
                    session(2, 1, 120),
                )
            val vm = createVm()
            val diary = contentOf(vm).diary
            assertEquals(7, diary.size)
            assertTrue(diary.last().isToday)
            assertEquals(10f, diary.last().minutes)
            assertEquals(2f, diary[5].minutes)
            assertEquals(10, contentOf(vm).goalMin)
        }
}
