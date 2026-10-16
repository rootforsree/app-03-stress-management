package com.sreecharan.stressrelief.ui.journal

import androidx.lifecycle.SavedStateHandle
import com.sreecharan.stressrelief.data.catalog.journalPrompts
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.ui.FakeWellnessRepository
import com.sreecharan.stressrelief.ui.runViewModelTest
import java.time.LocalDate
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JournalViewModelsTest {
    @Test
    fun `list emits entries newest first as the repo provides`() =
        runViewModelTest { dispatcher ->
            val repo =
                FakeWellnessRepository(
                    journalEntries =
                        listOf(
                            JournalEntry(2, 2000L, "Prompt B", "Second"),
                            JournalEntry(1, 1000L, "Prompt A", "First"),
                        ),
                )
            val vm = JournalListViewModel(repo, dispatcher)
            advanceUntilIdle()
            val content = vm.uiState.value as JournalListUiState.Content
            assertEquals(listOf(2L, 1L), content.entries.map { it.id })
        }

    @Test
    fun `delete removes the entry through the repository`() =
        runViewModelTest { dispatcher ->
            val repo =
                FakeWellnessRepository(
                    journalEntries = listOf(JournalEntry(7, 1000L, "Prompt", "Words")),
                )
            val vm = JournalListViewModel(repo, dispatcher)
            advanceUntilIdle()

            vm.onDeleteEntry(7)
            advanceUntilIdle()

            assertEquals(listOf(7L), repo.deletedJournalIds)
            assertTrue((vm.uiState.value as JournalListUiState.Content).entries.isEmpty())
        }

    @Test
    fun `new editor shows the rotating prompt of the day`() =
        runViewModelTest { dispatcher ->
            val vm =
                JournalEditorViewModel(
                    SavedStateHandle(mapOf("entryId" to -1L)),
                    FakeWellnessRepository(),
                    dispatcher,
                )
            advanceUntilIdle()
            val state = vm.uiState.value as JournalEditorUiState.Editing
            assertTrue(state.isNew)
            val expected =
                journalPrompts[
                    JournalEditorViewModel.promptIndex(LocalDate.now().toEpochDay()),
                ]
            assertEquals(expected, state.prompt)
            assertFalse(state.canSave)
        }

    @Test
    fun `existing editor loads saved prompt and text`() =
        runViewModelTest { dispatcher ->
            val repo =
                FakeWellnessRepository(
                    journalEntries =
                        listOf(JournalEntry(7, 1000L, "Saved prompt", "Saved words")),
                )
            val vm =
                JournalEditorViewModel(
                    SavedStateHandle(mapOf("entryId" to 7L)),
                    repo,
                    dispatcher,
                )
            advanceUntilIdle()
            val state = vm.uiState.value as JournalEditorUiState.Editing
            assertFalse(state.isNew)
            assertEquals("Saved prompt", state.prompt)
            assertEquals("Saved words", state.text)
            assertTrue(state.canSave)
        }

    @Test
    fun `saving a new entry persists and marks journal done`() =
        runViewModelTest { dispatcher ->
            val repo = FakeWellnessRepository()
            val vm =
                JournalEditorViewModel(
                    SavedStateHandle(mapOf("entryId" to -1L)),
                    repo,
                    dispatcher,
                )
            advanceUntilIdle()
            vm.onTextChanged("Today was a lot.")
            assertTrue((vm.uiState.value as JournalEditorUiState.Editing).canSave)

            vm.onSave()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is JournalEditorUiState.Saved)
            assertTrue(repo.journalDoneMarked)
            assertEquals(1, repo.journalEntriesFlow.value.size)
            assertEquals("Today was a lot.", repo.journalEntriesFlow.value.first().text)
        }

    @Test
    fun `saving an edit updates the entry`() =
        runViewModelTest { dispatcher ->
            val repo =
                FakeWellnessRepository(
                    journalEntries =
                        listOf(JournalEntry(7, 1000L, "Prompt", "Old words")),
                )
            val vm =
                JournalEditorViewModel(
                    SavedStateHandle(mapOf("entryId" to 7L)),
                    repo,
                    dispatcher,
                )
            advanceUntilIdle()
            vm.onTextChanged("New words")

            vm.onSave()
            advanceUntilIdle()

            assertTrue(vm.uiState.value is JournalEditorUiState.Saved)
            assertEquals(listOf(7L to "New words"), repo.updatedJournals)
            assertTrue(repo.journalDoneMarked)
        }

    @Test
    fun `missing entry id reports an error`() =
        runViewModelTest { dispatcher ->
            val vm =
                JournalEditorViewModel(
                    SavedStateHandle(mapOf("entryId" to 4242L)),
                    FakeWellnessRepository(),
                    dispatcher,
                )
            advanceUntilIdle()
            assertTrue(vm.uiState.value is JournalEditorUiState.Error)
        }

    @Test
    fun `prompt index wraps around the prompt list`() {
        val count = 20
        assertEquals(0, JournalEditorViewModel.promptIndex(0, count))
        assertEquals(1, JournalEditorViewModel.promptIndex(21, count))
        assertEquals(19, JournalEditorViewModel.promptIndex(-1, count))
        assertEquals(0, JournalEditorViewModel.promptIndex(-20, count))
    }
}
