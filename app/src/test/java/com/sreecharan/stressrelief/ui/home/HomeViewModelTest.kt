package com.sreecharan.stressrelief.ui.home

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.ui.FakeWellnessRepository
import com.sreecharan.stressrelief.ui.runViewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class FakeHealthConnectStateProvider(
    var available: Boolean = false,
    var granted: Boolean = false,
) : HealthConnectStateProvider {
    override suspend fun isAvailable(): Boolean = available

    override suspend fun sdkStatus(): Int =
        if (available) {
            androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE
        } else {
            androidx.health.connect.client.HealthConnectClient.SDK_UNAVAILABLE
        }

    override suspend fun hasAllPermissions(): Boolean = granted

    override fun requiredPermissions(): Set<String> = emptySet()

    override fun permissionContract(): ActivityResultContract<Set<String>, Set<String>> =
        object : ActivityResultContract<Set<String>, Set<String>>() {
            override fun createIntent(
                context: Context,
                input: Set<String>,
            ): Intent = Intent()

            override fun parseResult(
                resultCode: Int,
                intent: Intent?,
            ): Set<String> = emptySet()
        }
}

class HomeViewModelTest {
    private val zone = ZoneId.systemDefault()

    private fun ms(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun createVm(
        repo: FakeWellnessRepository,
        health: FakeHealthConnectStateProvider = FakeHealthConnectStateProvider(),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = HomeViewModel(repo, health, dispatcher)

    @Test
    fun `emits loading then content with streak dots and score`() =
        runViewModelTest { dispatcher ->
            val monday = LocalDate.now(zone).with(DayOfWeek.MONDAY)
            val repo =
                FakeWellnessRepository(
                    moodHistory =
                        listOf(
                            MoodCheckIn(1, ms(monday), 4, listOf("work"), ""),
                            MoodCheckIn(2, ms(monday.plusDays(2)), 5, listOf("exercise"), ""),
                        ),
                    streakDays = 5,
                    calmMinutes = 42,
                    stressScore = StressScore(72, "Balanced", listOf("Late nights", "3 calm sessions")),
                )
            val vm = createVm(repo, dispatcher = dispatcher)
            assertTrue(vm.uiState.value is HomeUiState.Loading)

            advanceUntilIdle()

            val content = vm.uiState.value
            assertTrue(content is HomeUiState.Content)
            assertEquals(5, content.streakDays)
            assertEquals("5-day streak", HomeStats.streakLabel(content.streakDays))
            assertEquals(7, content.weekDots.size)
            assertTrue(content.weekDots[0].active, "Monday dot should be active")
            assertFalse(content.weekDots[1].active, "Tuesday dot should be inactive")
            assertTrue(content.weekDots[2].active, "Wednesday dot should be active")
            assertEquals(42, content.totalCalmMinutes)
            assertEquals(72, content.stressScore?.score)
            assertEquals("Balanced", content.stressScore?.label)
            assertEquals(2, content.stressScore?.factors?.size)
        }

    @Test
    fun `streak label copy is correct for zero one and many days`() {
        assertEquals("Start your streak", HomeStats.streakLabel(0))
        assertEquals("1-day streak", HomeStats.streakLabel(1))
        assertEquals("21-day streak", HomeStats.streakLabel(21))
    }

    @Test
    fun `bloom petals grow with streak and stay in bounds`() {
        assertEquals(3, HomeStats.bloomPetals(0))
        assertEquals(3, HomeStats.bloomPetals(2))
        assertEquals(7, HomeStats.bloomPetals(7))
        assertEquals(12, HomeStats.bloomPetals(99))
    }

    @Test
    fun `week dots use monday-first labels and ignore other weeks`() {
        val today = LocalDate.now(zone)
        val monday = today.with(DayOfWeek.MONDAY)
        val dots =
            HomeStats.weekDots(
                listOf(
                    MoodCheckIn(1, ms(monday.minusDays(1)), 3, emptyList(), ""),
                    MoodCheckIn(2, ms(monday.plusDays(6)), 5, emptyList(), ""),
                ),
                today,
                zone,
            )
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), dots.map { it.label })
        assertFalse(dots[0].active, "last Sunday is not this week")
        assertTrue(dots[6].active, "this Sunday is active")
    }

    @Test
    fun `journal done today reflects todays entries only`() =
        runViewModelTest { dispatcher ->
            val repo =
                FakeWellnessRepository(
                    journalEntries =
                        listOf(
                            JournalEntry(
                                1,
                                ms(LocalDate.now(zone).minusDays(2)),
                                "Prompt",
                                "Old words",
                            ),
                        ),
                )
            val vm = createVm(repo, dispatcher = dispatcher)
            advanceUntilIdle()
            val content = vm.uiState.value as HomeUiState.Content
            assertFalse(content.journalDoneToday)
        }

    @Test
    fun `retry reloads after an error`() =
        runViewModelTest { dispatcher ->
            val repo = FakeWellnessRepository(streakDays = 3)
            val vm = createVm(repo, dispatcher = dispatcher)
            advanceUntilIdle()
            assertTrue(vm.uiState.value is HomeUiState.Content)
            vm.retry()
            assertTrue(vm.uiState.value is HomeUiState.Loading)
            advanceUntilIdle()
            assertEquals(3, (vm.uiState.value as HomeUiState.Content).streakDays)
        }

    @Test
    fun `health connect available but not granted surfaces the opt-in state`() =
        runViewModelTest { dispatcher ->
            val health = FakeHealthConnectStateProvider(available = true, granted = false)
            val vm = createVm(FakeWellnessRepository(), health, dispatcher)
            advanceUntilIdle()

            val content = vm.uiState.value as HomeUiState.Content
            assertEquals(HealthConnectUiState.Available, content.healthConnectState)
        }

    @Test
    fun `granting health permissions flips the state and refreshes the score`() =
        runViewModelTest { dispatcher ->
            val health = FakeHealthConnectStateProvider(available = true, granted = false)
            val repo = FakeWellnessRepository(stressScore = StressScore(60, "Steady", emptyList()))
            val vm = createVm(repo, health, dispatcher)
            advanceUntilIdle()

            health.granted = true
            vm.onHealthPermissionResult()
            advanceUntilIdle()

            val content = vm.uiState.value as HomeUiState.Content
            assertEquals(HealthConnectUiState.Granted, content.healthConnectState)
            assertEquals(60, content.stressScore?.score)
        }

    @Test
    fun `health connect missing stays unavailable`() =
        runViewModelTest { dispatcher ->
            val vm =
                createVm(
                    FakeWellnessRepository(),
                    FakeHealthConnectStateProvider(available = false),
                    dispatcher,
                )
            advanceUntilIdle()

            val content = vm.uiState.value as HomeUiState.Content
            assertEquals(HealthConnectUiState.Unavailable, content.healthConnectState)
        }
}
