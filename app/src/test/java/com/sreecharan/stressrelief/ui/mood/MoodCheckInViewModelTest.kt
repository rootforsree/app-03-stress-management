package com.sreecharan.stressrelief.ui.mood

import com.sreecharan.stressrelief.ui.FakeWellnessRepository
import com.sreecharan.stressrelief.ui.runViewModelTest
import com.sreecharan.stressrelief.widget.AuraWidgetUpdater
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MoodCheckInViewModelTest {
    private val widgetUpdater: AuraWidgetUpdater = mockk(relaxed = true)

    @Test
    fun `starts with nothing selected`() =
        runViewModelTest { dispatcher ->
            val vm =
                MoodCheckInViewModel(FakeWellnessRepository(), dispatcher, mockk<AuraWidgetUpdater>(relaxed = true))
            val state = vm.uiState.value
            assertTrue(state is MoodCheckInUiState.Editing)
            assertEquals(null, state.mood)
            assertTrue(state.activities.isEmpty())
            assertEquals("", state.note)
        }

    @Test
    fun `mood selection clamps to the 1 to 5 scale`() =
        runViewModelTest { dispatcher ->
            val vm =
                MoodCheckInViewModel(FakeWellnessRepository(), dispatcher, mockk<AuraWidgetUpdater>(relaxed = true))
            vm.onMoodSelected(4)
            assertEquals(4, (vm.uiState.value as MoodCheckInUiState.Editing).mood)
            vm.onMoodSelected(99)
            assertEquals(5, (vm.uiState.value as MoodCheckInUiState.Editing).mood)
        }

    @Test
    fun `activity chips toggle on and off`() =
        runViewModelTest { dispatcher ->
            val vm =
                MoodCheckInViewModel(FakeWellnessRepository(), dispatcher, mockk<AuraWidgetUpdater>(relaxed = true))
            vm.onActivityToggled("work")
            vm.onActivityToggled("sleep")
            assertEquals(
                setOf("work", "sleep"),
                (vm.uiState.value as MoodCheckInUiState.Editing).activities,
            )
            vm.onActivityToggled("work")
            assertEquals(
                setOf("sleep"),
                (vm.uiState.value as MoodCheckInUiState.Editing).activities,
            )
        }

    @Test
    fun `save without a mood does nothing`() =
        runViewModelTest { dispatcher ->
            val repo = FakeWellnessRepository()
            val vm = MoodCheckInViewModel(repo, dispatcher, widgetUpdater)
            vm.onSave()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is MoodCheckInUiState.Editing)
            assertTrue(repo.savedMoods.isEmpty())
        }

    @Test
    fun `save persists mood tags and trimmed note then reports saved`() =
        runViewModelTest { dispatcher ->
            val repo = FakeWellnessRepository()
            val vm = MoodCheckInViewModel(repo, dispatcher, widgetUpdater)
            vm.onMoodSelected(4)
            vm.onActivityToggled("exercise")
            vm.onNoteChanged("  quick breather helped  ")

            vm.onSave()
            assertTrue(
                vm.uiState.value is MoodCheckInUiState.Saving,
                "Saving is set synchronously before the repo call",
            )
            advanceUntilIdle()
            assertTrue(vm.uiState.value is MoodCheckInUiState.Saved)

            assertEquals(1, repo.savedMoods.size)
            val (score, activities, note) = repo.savedMoods.first()
            assertEquals(4, score)
            assertEquals(listOf("exercise"), activities)
            assertEquals("quick breather helped", note)
            coVerify(exactly = 1) { widgetUpdater.refresh() }
        }
}
