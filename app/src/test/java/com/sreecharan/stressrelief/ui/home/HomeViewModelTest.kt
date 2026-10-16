package com.sreecharan.stressrelief.ui.home

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.sreecharan.stressrelief.data.catalog.journalPrompts
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.ProgramProgress
import com.sreecharan.stressrelief.data.model.SleepSession
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.ui.FakeSleepRepository
import com.sreecharan.stressrelief.ui.FakeWellnessRepository
import com.sreecharan.stressrelief.ui.journal.JournalEditorViewModel
import com.sreecharan.stressrelief.ui.runViewModelTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

    override fun writePermissions(): Set<String> = emptySet()

    override suspend fun writeBreathingSession(
        startMs: Long,
        endMs: Long,
    ): Boolean = true

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
        breathing: FakeBreathingRepository = FakeBreathingRepository(),
        sleep: FakeSleepRepository = FakeSleepRepository(),
        health: FakeHealthConnectStateProvider = FakeHealthConnectStateProvider(),
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ) = HomeViewModel(repo, health, breathing, sleep, dispatcher)

    // ---- Pure helpers ----

    @Test
    fun `greeting follows the time of day`() {
        assertEquals(GreetingTime.MORNING, HomeStats.greetingTime(0))
        assertEquals(GreetingTime.MORNING, HomeStats.greetingTime(11))
        assertEquals(GreetingTime.AFTERNOON, HomeStats.greetingTime(12))
        assertEquals(GreetingTime.AFTERNOON, HomeStats.greetingTime(17))
        assertEquals(GreetingTime.EVENING, HomeStats.greetingTime(18))
        assertEquals(GreetingTime.EVENING, HomeStats.greetingTime(23))
    }

    @Test
    fun `nudge follows the v2 stress bands`() {
        assertEquals(NudgeKind.NO_SCORE, HomeStats.nudgeKind(null))
        assertEquals(NudgeKind.HIGH, HomeStats.nudgeKind(80))
        assertEquals(NudgeKind.HIGH, HomeStats.nudgeKind(51))
        assertEquals(NudgeKind.MODERATE, HomeStats.nudgeKind(50))
        assertEquals(NudgeKind.MODERATE, HomeStats.nudgeKind(26))
        assertEquals(NudgeKind.LOW, HomeStats.nudgeKind(25))
        assertEquals(NudgeKind.LOW, HomeStats.nudgeKind(0))
    }

    @Test
    fun `sparkline is trailing seven days oldest first with nulls`() {
        val today = LocalDate.now(zone)
        val monday = today.with(DayOfWeek.MONDAY)
        val checkIns =
            listOf(
                MoodCheckIn(1, ms(monday), 2, emptyList(), ""),
                MoodCheckIn(2, ms(monday), 4, emptyList(), ""),
                MoodCheckIn(3, ms(monday.plusDays(3)), 5, emptyList(), ""),
            )
        val spark = HomeStats.sparkline(checkIns, monday.plusDays(6), zone)
        assertEquals(7, spark.size)
        assertEquals(3.0f, spark[0], "Monday averages 2 and 4")
        assertNull(spark[1], "Tuesday has no check-in")
        assertNull(spark[2])
        assertEquals(5.0f, spark[3], "Thursday is 5")
        assertNull(spark[4])
        assertNull(spark[5])
        assertNull(spark[6], "Sunday has no check-in")
    }

    @Test
    fun `week delta maps mood swings onto the score scale`() {
        // Older half averages 4.0, newer half averages 3.0: (3-4)*20 = -20.
        val down = listOf(4.0f, 4.0f, 3.0f, 3.0f)
        assertEquals(-20, HomeStats.weekDelta(down))
        val up = listOf(2.0f, 2.0f, 4.0f, 4.0f, 4.0f, 4.0f)
        assertEquals(40, HomeStats.weekDelta(up))
        val flat = listOf(3.0f, 3.0f, 3.0f, 3.0f)
        assertEquals(0, HomeStats.weekDelta(flat))
        assertNull(HomeStats.weekDelta(listOf(3.0f, 4.0f, null, null)), "fewer than 4 days")
        assertNull(HomeStats.weekDelta(emptyList()))
    }

    @Test
    fun `week bars are monday-first with nulls for missing days`() {
        val today = LocalDate.now(zone)
        val monday = today.with(DayOfWeek.MONDAY)
        val bars =
            HomeStats.weekBars(
                listOf(
                    MoodCheckIn(1, ms(monday.minusDays(1)), 3, emptyList(), ""),
                    MoodCheckIn(2, ms(monday.plusDays(6)), 5, emptyList(), ""),
                ),
                today,
                zone,
            )
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), bars.map { it.label })
        assertNull(bars[0].mood, "last Sunday is not this week")
        assertEquals(5.0f, bars[6].mood, "this Sunday is logged")
        assertNull(bars[3].mood)
    }

    @Test
    fun `weekly and daily calm minutes sum session durations`() {
        val today = LocalDate.now(zone)
        val sessions =
            listOf(
                BreathingSession(1, ms(today), 300, "calm", "4-4-6", true),
                BreathingSession(2, ms(today.minusDays(2)), 600, "calm", "box", true),
                BreathingSession(3, ms(today.minusDays(10)), 3600, "calm", "box", true),
            )
        val weekStart = ms(today.with(DayOfWeek.MONDAY))
        assertEquals(15, HomeStats.weeklyCalmMinutes(sessions, weekStart), "(300+600)/60")
        assertEquals(5, HomeStats.calmMinutesToday(sessions, zone), "300/60")
        assertEquals(0, HomeStats.weeklyCalmMinutes(emptyList(), weekStart))
    }

    @Test
    fun `journal count counts this week only`() {
        val today = LocalDate.now(zone)
        val entries =
            listOf(
                JournalEntry(1, ms(today), "p", "today"),
                JournalEntry(2, ms(today.minusDays(3)), "p", "this week"),
                JournalEntry(3, ms(today.minusDays(10)), "p", "old"),
            )
        assertEquals(2, HomeStats.journalCountThisWeek(entries, ms(today.with(DayOfWeek.MONDAY))))
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
    fun `sleep session summary joins catalog sound names`() {
        val summary =
            HomeStats.sleepSessionSummary(
                SleepSession(1, 0L, 32, listOf("rain", "ocean")),
            )
        assertEquals(32, summary.durationMin)
        assertEquals("Rain + Ocean", summary.soundNames)
        val unknown =
            HomeStats.sleepSessionSummary(SleepSession(2, 0L, 10, listOf("nope")))
        assertEquals("nope", unknown.soundNames, "unknown ids fall back to the raw id")
    }

    @Test
    fun `continue first match wins missing check-in before program`() {
        val program = ProgramProgress("calm-reset", 3, listOf(1, 2), System.currentTimeMillis())
        val action =
            HomeStats.resolveContinueAction(
                checkedInToday = false,
                programs = listOf(program),
                breathingToday = false,
                sleptToday = false,
                hourOfDay = 20,
            )
        assertIs<ContinueAction.CheckIn>(action, "check-in is first match")
    }

    @Test
    fun `continue resolves the active program with catalog title and minutes`() {
        val action =
            HomeStats.resolveContinueAction(
                checkedInToday = true,
                programs = listOf(ProgramProgress("calm-reset", 3, listOf(1, 2), System.currentTimeMillis())),
                breathingToday = false,
                sleptToday = false,
                hourOfDay = 10,
            )
        assertIs<ContinueAction.Program>(action)
        assertEquals("7-Day Calm Reset", action.title)
        assertEquals(3, action.day)
        assertEquals(4, action.minutes, "calm-reset day 3 is 4 minutes in the catalog")
    }

    @Test
    fun `unstarted programs do not count as active`() {
        val action =
            HomeStats.resolveContinueAction(
                checkedInToday = true,
                programs = listOf(ProgramProgress("calm-reset", 1, emptyList(), 0)),
                breathingToday = false,
                sleptToday = false,
                hourOfDay = 10,
            )
        assertIs<ContinueAction.BreatheReset>(action, "unstarted program is skipped")
    }

    @Test
    fun `continue falls through to evening wind-down`() {
        val eveningWindDown =
            HomeStats.resolveContinueAction(
                checkedInToday = true,
                programs = emptyList(),
                breathingToday = true,
                sleptToday = false,
                hourOfDay = 19,
            )
        assertIs<ContinueAction.WindDown>(eveningWindDown)

        val afternoonNothing =
            HomeStats.resolveContinueAction(
                checkedInToday = true,
                programs = emptyList(),
                breathingToday = true,
                sleptToday = false,
                hourOfDay = 15,
            )
        assertNull(afternoonNothing, "no wind-down before 18:00")

        val eveningDone =
            HomeStats.resolveContinueAction(
                checkedInToday = true,
                programs = emptyList(),
                breathingToday = true,
                sleptToday = true,
                hourOfDay = 21,
            )
        assertNull(eveningDone, "everything done in the evening hides the card")
    }

    // ---- ViewModel ----

    @Test
    fun `emits loading then full v2 content`() =
        runViewModelTest { dispatcher ->
            val today = LocalDate.now(zone)
            val monday = today.with(DayOfWeek.MONDAY)
            val repo =
                FakeWellnessRepository(
                    moodHistory =
                        listOf(
                            MoodCheckIn(1, ms(monday), 4, listOf("work"), ""),
                            MoodCheckIn(2, ms(monday.plusDays(2)), 5, listOf("exercise"), ""),
                        ),
                    todayCheckIn = MoodCheckIn(9, ms(today), 4, emptyList(), ""),
                    journalEntries =
                        listOf(
                            JournalEntry(1, ms(today), "Prompt", "words"),
                            JournalEntry(2, ms(today.minusDays(10)), "Prompt", "old"),
                        ),
                    streakDays = 5,
                    stressScore = StressScore(72, "High", listOf("Late nights", "3 calm sessions")),
                )
            val breathing =
                FakeBreathingRepository(
                    sessions =
                        listOf(
                            BreathingSession(1, ms(today), 300, "calm", "4-4-6", true),
                            BreathingSession(2, ms(today), 600, "calm", "box", true),
                        ),
                )
            val sleep =
                FakeSleepRepository(
                    sessions = listOf(SleepSession(1, ms(today.minusDays(1)), 32, listOf("rain", "ocean"))),
                )
            val vm = createVm(repo, breathing, sleep, dispatcher = dispatcher)
            assertTrue(vm.uiState.value is HomeUiState.Loading)

            advanceUntilIdle()

            val content = vm.uiState.value
            assertIs<HomeUiState.Content>(content)
            assertEquals(5, content.streakDays)
            assertEquals("5-day streak", HomeStats.streakLabel(content.streakDays))
            assertEquals(7, content.weekBars.size)
            assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), content.weekBars.map { it.label })
            assertEquals(7, content.sparkline.size)
            assertEquals(72, content.stressScore?.score)
            assertEquals(NudgeKind.HIGH, content.nudge, "72 is High in the v2 bands")
            assertTrue(content.checkedInToday)
            assertTrue(content.journalDoneToday)
            assertEquals(15, content.calmMinutesToday)
            assertEquals(15, content.weeklyCalmMinutes, "(300+600)/60")
            assertEquals(1, content.weeklyJournalCount)
            val expectedPrompt =
                journalPrompts[
                    JournalEditorViewModel.promptIndex(today.toEpochDay(), journalPrompts.size),
                ]
            assertEquals(expectedPrompt, content.todayPrompt)
            assertNotNull(content.lastSleepSession)
            assertEquals(32, content.lastSleepSession?.durationMin)
            assertEquals("Rain + Ocean", content.lastSleepSession?.soundNames)
            // Checked in and no program, but breathed today: only the evening
            // wind-down can still fire, which depends on the wall clock.
            assertTrue(
                content.continueAction == null || content.continueAction is ContinueAction.WindDown,
            )
        }

    @Test
    fun `missing check-in drives the continue card regardless of hour`() =
        runViewModelTest { dispatcher ->
            val vm = createVm(FakeWellnessRepository(), dispatcher = dispatcher)
            advanceUntilIdle()

            val content = vm.uiState.value as HomeUiState.Content
            assertFalse(content.checkedInToday)
            assertIs<ContinueAction.CheckIn>(content.continueAction)
            // The fake returns a default 50 ("Steady") score when none is set.
            assertEquals(NudgeKind.MODERATE, content.nudge)
            assertNull(content.weekDelta, "no check-ins means no delta")
        }

    @Test
    fun `active program drives the continue card`() =
        runViewModelTest { dispatcher ->
            val today = LocalDate.now(zone)
            val repo =
                FakeWellnessRepository(
                    todayCheckIn = MoodCheckIn(9, ms(today), 4, emptyList(), ""),
                )
            val breathing =
                FakeBreathingRepository(
                    programs =
                        listOf(
                            ProgramProgress("sleep-better", 2, listOf(1), System.currentTimeMillis()),
                        ),
                )
            val vm = createVm(repo, breathing, dispatcher = dispatcher)
            advanceUntilIdle()

            val content = vm.uiState.value as HomeUiState.Content
            val action = content.continueAction
            assertIs<ContinueAction.Program>(action)
            assertEquals("Sleep Better Week", action.title)
            assertEquals(2, action.day)
            assertEquals(4, action.minutes, "sleep-better day 2 is 4 minutes in the catalog")
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
            val vm = createVm(FakeWellnessRepository(), health = health, dispatcher = dispatcher)
            advanceUntilIdle()

            val content = vm.uiState.value as HomeUiState.Content
            assertEquals(HealthConnectUiState.Available, content.healthConnectState)
        }

    @Test
    fun `granting health permissions flips the state and refreshes the score`() =
        runViewModelTest { dispatcher ->
            val health = FakeHealthConnectStateProvider(available = true, granted = false)
            val repo = FakeWellnessRepository(stressScore = StressScore(60, "High", emptyList()))
            val vm = createVm(repo, health = health, dispatcher = dispatcher)
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
                    health = FakeHealthConnectStateProvider(available = false),
                    dispatcher = dispatcher,
                )
            advanceUntilIdle()

            val content = vm.uiState.value as HomeUiState.Content
            assertEquals(HealthConnectUiState.Unavailable, content.healthConnectState)
        }
}
