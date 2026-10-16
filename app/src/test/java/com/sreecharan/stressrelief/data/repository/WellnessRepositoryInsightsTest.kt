package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.health.HealthConnectClient
import com.sreecharan.stressrelief.data.health.RestingHrDay
import com.sreecharan.stressrelief.data.health.SleepNight
import com.sreecharan.stressrelief.data.health.SleepStageSegment
import com.sreecharan.stressrelief.data.health.SleepStageType
import com.sreecharan.stressrelief.data.local.BreathingSessionDao
import com.sreecharan.stressrelief.data.local.DailyActivityDao
import com.sreecharan.stressrelief.data.local.JournalDao
import com.sreecharan.stressrelief.data.local.MoodCheckInDao
import com.sreecharan.stressrelief.data.model.RecoveryBand
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * computeInsights() graceful degradation: Health Connect down, partial data,
 * and exploding DAOs must all yield a valid InsightsData, never a throw and
 * never a zero scored from missing data.
 */
class WellnessRepositoryInsightsTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone).toEpochDay()

    private fun repo(
        health: HealthConnectClient,
        moodDao: MoodCheckInDao = mockk(),
        activityDao: DailyActivityDao = mockk(),
    ): WellnessRepositoryImpl {
        every { moodDao.observeSince(any()) } returns flowOf(emptyList())
        every { activityDao.observeLastNDays(any()) } returns flowOf(emptyList())
        return WellnessRepositoryImpl(
            moodCheckInDao = moodDao,
            breathingSessionDao = mockk<BreathingSessionDao>(),
            journalDao = mockk<JournalDao>(),
            dailyActivityDao = activityDao,
            healthConnectClient = health,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun healthyClient(): HealthConnectClient {
        val client = mockk<HealthConnectClient>()
        coEvery { client.isAvailable() } returns true
        coEvery { client.hasAllPermissions() } returns true
        coEvery { client.readSleepNights(any()) } returns emptyList()
        coEvery { client.readSleepStrips(any()) } returns emptyList()
        coEvery { client.readRestingHrDays(any()) } returns emptyList()
        coEvery { client.readHrvDays(any()) } returns emptyList()
        return client
    }

    /** One night: 23:00 -> 07:00 with light/deep/REM/awake stages. */
    private fun stagedNight(day: Long): SleepNight {
        val base = day * 86_400_000L
        fun seg(
            startMin: Int,
            endMin: Int,
            type: SleepStageType,
        ) = SleepStageSegment(base + startMin * 60_000L, base + endMin * 60_000L, type)
        // minutes are offsets from 23:00 local on `day`
        return SleepNight(
            epochDay = day,
            sessionStartMs = base,
            sessionEndMs = base + 480 * 60_000L,
            stages =
                listOf(
                    seg(0, 15, SleepStageType.LIGHT), // latency 15
                    seg(15, 105, SleepStageType.LIGHT),
                    seg(105, 195, SleepStageType.DEEP),
                    seg(195, 210, SleepStageType.AWAKE),
                    seg(210, 330, SleepStageType.LIGHT),
                    seg(330, 420, SleepStageType.REM),
                    seg(420, 480, SleepStageType.LIGHT),
                ),
        )
    }

    @Test
    fun `health connect down degrades to calibrating`() =
        runTest {
            val client = mockk<HealthConnectClient>()
            coEvery { client.isAvailable() } returns false
            coEvery { client.hasAllPermissions() } returns false

            val insights = repo(client).computeInsights()

            assertFalse(insights.healthConnected)
            assertTrue(insights.calibrating)
            assertEquals(RecoveryBand.CALIBRATING, insights.recoveryBand)
            assertNull(insights.recoveryScore)
            assertNull(insights.sleepDebtHours)
            assertNull(insights.sri)
            assertNull(insights.restingHrToday)
            assertTrue(insights.sleepNights.isEmpty())
            assertEquals(8f, insights.sleepNeedHours)
        }

    @Test
    fun `sleep only still scores nights and debt without inventing hr`() =
        runTest {
            val client = healthyClient()
            val nights = (0L..9L).map { stagedNight(today - 9 + it) }
            coEvery { client.readSleepNights(any()) } returns nights

            val insights = repo(client).computeInsights()

            assertTrue(insights.healthConnected)
            assertFalse(insights.calibrating) // 10 days of data
            assertEquals(10, insights.sleepNights.size)
            assertTrue(insights.sleepNights.all { it.score != null })
            assertTrue(insights.sleepNights.all { it.score in 0..100 })
            assertNotNull(insights.sleepDebtHours)
            // Missing RHR/HRV surface as nulls, never zeros.
            assertNull(insights.restingHrToday)
            assertNull(insights.restingHrBaseline)
            assertNull(insights.restingHrZ)
            assertNull(insights.hrvRmssdToday)
            assertNull(insights.recoveryScore)
        }

    @Test
    fun `resting hr flows through to today baseline and z`() =
        runTest {
            val client = healthyClient()
            val rhrDays = (0L..20L).map { RestingHrDay(today - 20 + it, 60.0, 1.0, derived = false) }
            coEvery { client.readRestingHrDays(any()) } returns rhrDays

            val insights = repo(client).computeInsights()

            assertFalse(insights.calibrating)
            assertEquals(60, insights.restingHrToday)
            val baseline = assertNotNull(insights.restingHrBaseline)
            assertTrue(Math.abs(baseline - 60f) < 0.01f)
            // std is 0 -> z is null, not infinite or zero
            assertNull(insights.restingHrZ)
        }

    @Test
    fun `dao explosion still returns a valid object`() =
        runTest {
            val client = healthyClient()
            val moodDao = mockk<MoodCheckInDao>()
            val repository = repo(client, moodDao = moodDao)
            // Re-stub after repo() so the explosion wins over the default stub.
            every { moodDao.observeSince(any()) } throws RuntimeException("db gone")

            val insights = repository.computeInsights()

            assertTrue(insights.calibrating)
            assertEquals(RecoveryBand.CALIBRATING, insights.recoveryBand)
            assertTrue(insights.insightCards.isEmpty())
        }
}
