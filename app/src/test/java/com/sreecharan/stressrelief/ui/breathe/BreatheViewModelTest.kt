package com.sreecharan.stressrelief.ui.breathe

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BreatheViewModelTest {

    @Test
    fun `emits four goal cards from the catalog`() {
        val vm = BreatheViewModel()
        val content = vm.uiState.value as BreatheUiState.Content
        assertEquals(4, content.goals.size)
        assertEquals(listOf("CALM", "SLEEP", "FOCUS", "SOS"), content.goals.map { it.goal })
        assertNull(content.selectedGoal)
        content.goals.forEach { goal ->
            assertTrue(goal.patterns.isNotEmpty(), "goal ${goal.goal} has no patterns")
        }
    }

    @Test
    fun `goal click selects the goal`() {
        val vm = BreatheViewModel()
        vm.onGoalClick("SLEEP")
        val content = vm.uiState.value as BreatheUiState.Content
        assertEquals("SLEEP", content.selectedGoal)
    }

    @Test
    fun `picker dismiss clears the selection`() {
        val vm = BreatheViewModel()
        vm.onGoalClick("FOCUS")
        vm.onPickerDismiss()
        val content = vm.uiState.value as BreatheUiState.Content
        assertNull(content.selectedGoal)
    }

    @Test
    fun `pattern click emits open session and closes the picker`() = runTest {
        val vm = BreatheViewModel()
        vm.onGoalClick("CALM")
        vm.events.test {
            vm.onPatternClick("4-4-6")
            assertEquals(BreatheEvent.OpenSession("CALM", "4-4-6"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        val content = vm.uiState.value as BreatheUiState.Content
        assertNull(content.selectedGoal)
    }

    @Test
    fun `pattern click without a selected goal is ignored`() = runTest {
        val vm = BreatheViewModel()
        vm.events.test {
            vm.onPatternClick("4-4-6")
            expectNoEvents()
        }
    }
}
