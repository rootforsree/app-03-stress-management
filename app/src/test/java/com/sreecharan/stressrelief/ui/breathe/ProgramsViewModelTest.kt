package com.sreecharan.stressrelief.ui.breathe

import app.cash.turbine.test
import com.sreecharan.stressrelief.data.catalog.BreathingPatterns
import com.sreecharan.stressrelief.data.catalog.Programs
import com.sreecharan.stressrelief.data.model.ProgramProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var breathingRepo: FakeBreathingRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        breathingRepo = FakeBreathingRepository()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createVm(): ProgramsViewModel = ProgramsViewModel(breathingRepo)

    private suspend fun awaitContent(vm: ProgramsViewModel): ProgramsUiState.Content {
        var latest: ProgramsUiState = ProgramsUiState.Loading
        vm.uiState.test {
            while (latest !is ProgramsUiState.Content) {
                latest = awaitItem()
            }
            cancelAndIgnoreRemainingEvents()
        }
        return latest as ProgramsUiState.Content
    }

    @Test
    fun `unstarted programs show day one as current`() =
        runTest(testDispatcher) {
            breathingRepo.programsFlow.value = emptyList()
            val content = awaitContent(createVm())
            assertEquals(3, content.programs.size)
            content.programs.forEach { program ->
                assertEquals(7, program.days.size)
                assertEquals(DayStatus.CURRENT, program.days[0].status)
                assertTrue(program.days.drop(1).all { it.status == DayStatus.LOCKED })
            }
        }

    @Test
    fun `stored progress marks done and current days`() =
        runTest(testDispatcher) {
            val id = Programs.all.first().id
            breathingRepo.programsFlow.value =
                listOf(ProgramProgress(id, 3, listOf(1, 2), 123L))
            val content = awaitContent(createVm())
            val program = content.programs.first { it.id == id }
            assertEquals(DayStatus.DONE, program.days[0].status)
            assertEquals(DayStatus.DONE, program.days[1].status)
            assertEquals(DayStatus.CURRENT, program.days[2].status)
            assertTrue(program.days.drop(3).all { it.status == DayStatus.LOCKED })
        }

    @Test
    fun `clicking the current day emits a start event`() =
        runTest(testDispatcher) {
            val vm = createVm()
            val content = awaitContent(vm)
            val program = content.programs.first()
            val day = program.days.first { it.status == DayStatus.CURRENT }
            vm.events.test {
                vm.onDayClick(program.id, day.day, day.patternId, day.status)
                val event = awaitItem() as ProgramsEvent.StartProgramSession
                assertEquals(program.id, event.programId)
                assertEquals(day.day, event.day)
                assertEquals(day.patternId, event.patternId)
                assertEquals(BreathingPatterns.byId(day.patternId)?.goal, event.goal)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `clicking locked or done days emits nothing`() =
        runTest(testDispatcher) {
            val vm = createVm()
            val content = awaitContent(vm)
            val program = content.programs.first()
            val locked = program.days.first { it.status == DayStatus.LOCKED }
            vm.events.test {
                vm.onDayClick(program.id, locked.day, locked.patternId, locked.status)
                expectNoEvents()
            }
        }
}
