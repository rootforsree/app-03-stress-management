package com.sreecharan.stressrelief.ui.insights

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import com.sreecharan.stressrelief.data.health.HealthConnectStateProvider
import com.sreecharan.stressrelief.data.model.InsightCard
import com.sreecharan.stressrelief.data.model.InsightKind
import com.sreecharan.stressrelief.data.model.InsightsData
import com.sreecharan.stressrelief.data.model.JournalEntry
import com.sreecharan.stressrelief.data.model.MoodCheckIn
import com.sreecharan.stressrelief.data.model.RecoveryBand
import com.sreecharan.stressrelief.data.model.SleepNightScore
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.data.repository.WellnessRepository
import com.sreecharan.stressrelief.ui.runViewModelTest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * ViewModel test for the v2 Insights screen. The fake returns one fixed
 * [InsightsData] fixture; the ViewModel must pass it through untouched and
 * surface the Error state when computeInsights fails.
 *
 * NOTE: WellnessRepository.computeInsights() and the data.model insight types
 * come from Builder 1's contract section 4/5 work. This test compiles once
 * those land; the coordinator should merge Builder 1's model + repository
 * additions before running it.
 */
class InsightsViewModelTest {
    private val fixture =
        InsightsData(
            sleepNights =
                (0L until 14L).map { back ->
                    SleepNightScore(
                        epochDay = 20373L - back,
                        totalSleepMin = if (back == 3L) null else 420,
                        timeInBedMin = 480,
                        sleepEfficiencyPct = 88,
                        deepPct = 18f,
                        remPct = 22f,
                        wasoMin = 15,
                        awakenings = 1,
                        latencyMin = 12,
                        score = if (back == 3L) null else 70 + back.toInt(),
                        durationScore = 75,
                        efficiencyScore = 80,
                        architectureScore = 70,
                        continuityScore = 85,
                        latencyScore = 90,
                    )
                },
            sleepDebtHours = 2.5f,
            sleepNeedHours = 8.0f,
            sri = 65,
            socialJetlagMin = 42,
            restingHrToday = 64,
            restingHrBaseline = 60.0f,
            restingHrZ = 2.1f,
            hrvRmssdToday = null,
            recoveryScore = 72,
            recoveryBand = RecoveryBand.CHARGED,
            restingHrTrendBpmPerDay = 0.8f,
            sleepTrendMinPerDay = -3.0f,
            insightCards =
                listOf(
                    InsightCard(
                        title = "Sleep debt is climbing",
                        body = "You are 2.5 h short of your 8.0 h need over the last 2 weeks.",
                        kind = InsightKind.SLEEP,
                    ),
                    InsightCard(
                        title = "Resting HR is elevated",
                        body = "Today 64 bpm sits 2.1σ above your 21-day average of 60 bpm.",
                        kind = InsightKind.RECOVERY,
                    ),
                ),
            calibrating = false,
            healthConnected = false,
        )

    @Test
    fun `emits Content with the repository InsightsData`() =
        runViewModelTest { dispatcher ->
            val viewModel =
                InsightsViewModel(
                    FakeInsightsRepository(fixture),
                    FakeHealthConnectStateProvider(),
                    dispatcher,
                )

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertIs<InsightsUiState.Content>(state)
            assertEquals(72, state.insights.recoveryScore)
            assertEquals(RecoveryBand.CHARGED, state.insights.recoveryBand)
            assertEquals(14, state.insights.sleepNights.size)
            assertEquals(null, state.insights.sleepNights[3].score)
            assertEquals(2.5f, state.insights.sleepDebtHours)
            assertEquals(65, state.insights.sri)
            assertEquals(42, state.insights.socialJetlagMin)
            assertEquals(64, state.insights.restingHrToday)
            assertEquals(2.1f, state.insights.restingHrZ)
            assertEquals(0.8f, state.insights.restingHrTrendBpmPerDay)
            assertEquals(2, state.insights.insightCards.size)
            assertEquals("Sleep debt is climbing", state.insights.insightCards[0].title)
            assertEquals(InsightKind.RECOVERY, state.insights.insightCards[1].kind)
            assertEquals(false, state.insights.healthConnected)
        }

    @Test
    fun `emits Error when computeInsights fails`() =
        runViewModelTest { dispatcher ->
            val viewModel =
                InsightsViewModel(
                    FakeInsightsRepository(result = Result.failure(IllegalStateException("boom"))),
                    FakeHealthConnectStateProvider(),
                    dispatcher,
                )

            advanceUntilIdle()

            assertEquals(InsightsUiState.Error, viewModel.uiState.value)
        }

    @Test
    fun `retry reloads the latest insights`() =
        runViewModelTest { dispatcher ->
            val repo = FakeInsightsRepository(fixture)
            val viewModel =
                InsightsViewModel(repo, FakeHealthConnectStateProvider(), dispatcher)
            advanceUntilIdle()
            assertEquals(
                72,
                (viewModel.uiState.value as InsightsUiState.Content).insights.recoveryScore,
            )

            repo.result =
                Result.success(
                    fixture.copy(recoveryScore = 41, recoveryBand = RecoveryBand.DEPLETED),
                )
            viewModel.retry()
            advanceUntilIdle()

            val content = viewModel.uiState.value as InsightsUiState.Content
            assertEquals(41, content.insights.recoveryScore)
            assertEquals(RecoveryBand.DEPLETED, content.insights.recoveryBand)
        }
}

/** Minimal WellnessRepository fake that only varies computeInsights. */
private class FakeInsightsRepository(
    var result: Result<InsightsData>,
) : WellnessRepository {
    constructor(fixture: InsightsData) : this(Result.success(fixture))

    override fun observeMoodHistory(days: Int): Flow<List<MoodCheckIn>> = flowOf(emptyList())

    override fun observeTodayCheckIn(): Flow<MoodCheckIn?> = flowOf(null)

    override suspend fun saveMoodCheckIn(
        score: Int,
        activities: List<String>,
        note: String,
    ) = Unit

    override fun observeJournalEntries(): Flow<List<JournalEntry>> = flowOf(emptyList())

    override fun observeJournalEntry(id: Long): Flow<JournalEntry?> = flowOf(null)

    override suspend fun saveJournalEntry(
        prompt: String,
        text: String,
    ): Long = -1L

    override suspend fun updateJournalEntry(
        id: Long,
        text: String,
    ) = Unit

    override suspend fun deleteJournalEntry(id: Long) = Unit

    override fun observeStreakDays(): Flow<Int> = flowOf(0)

    override fun observeTotalCalmMinutes(): Flow<Int> = flowOf(0)

    override suspend fun computeStressScore(): StressScore = StressScore(50, "Steady", emptyList())

    override suspend fun markBreathingDone(minutes: Int) = Unit

    override suspend fun markJournalDone() = Unit

    override suspend fun computeInsights(): InsightsData = result.getOrThrow()
}

private class FakeHealthConnectStateProvider : HealthConnectStateProvider {
    override suspend fun isAvailable(): Boolean = true

    override suspend fun sdkStatus(): Int =
        androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE

    override suspend fun hasAllPermissions(): Boolean = false

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

    override fun writePermissions(): Set<String> = emptySet()

    override suspend fun writeBreathingSession(
        startMs: Long,
        endMs: Long,
    ): Boolean = true
}
