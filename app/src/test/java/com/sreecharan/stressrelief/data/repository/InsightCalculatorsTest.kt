package com.sreecharan.stressrelief.data.repository

import com.sreecharan.stressrelief.data.health.SleepDayStrip
import com.sreecharan.stressrelief.data.model.InsightKind
import com.sreecharan.stressrelief.data.model.RecoveryBand
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pure-JVM tests for the nine insight algorithms. Fixed inputs, no Android framework. */
class InsightCalculatorsTest {
    private val calc = InsightCalculators

    // ------------------------------------------------------------------
    // 1. Sleep quality score.
    // ------------------------------------------------------------------

    private fun perfectNight(day: Long = 10L) =
        InsightCalculators.SleepNightInput(
            epochDay = day,
            totalSleepMin = 480,
            timeInBedMin = 500,
            deepMin = 96, // 20% of TST
            remMin = 106, // 22% of TST
            wasoMin = 10,
            awakenings = 1,
            latencyMin = 15,
            sleepOnsetMs = 1_000L,
            wakeMs = 2_000L,
        )

    @Test
    fun `perfect night scores one hundred`() {
        val scored = calc.sleepNightScore(perfectNight())

        assertEquals(100, scored.score)
        assertEquals(100, scored.durationScore)
        assertEquals(100, scored.efficiencyScore)
        assertEquals(100, scored.architectureScore)
        assertEquals(100, scored.continuityScore)
        assertEquals(100, scored.latencyScore)
    }

    @Test
    fun `short sleep drags the duration component`() {
        // 5h: trapezoid ramps 240->420, so 100 * (300-240)/180 = 33
        val scored = calc.sleepNightScore(perfectNight().copy(totalSleepMin = 300))

        assertEquals(33, scored.durationScore)
        val shortScore = assertNotNull(scored.score)
        assertTrue(shortScore < 100)
    }

    @Test
    fun `duration bands hit their edges`() {
        assertEquals(100, calc.sleepNightScore(perfectNight().copy(totalSleepMin = 420)).durationScore)
        assertEquals(100, calc.sleepNightScore(perfectNight().copy(totalSleepMin = 540)).durationScore)
        assertEquals(0, calc.sleepNightScore(perfectNight().copy(totalSleepMin = 239)).durationScore)
        assertEquals(0, calc.sleepNightScore(perfectNight().copy(totalSleepMin = 601)).durationScore)
    }

    @Test
    fun `missing stages rescale instead of zeroing`() {
        val scored = calc.sleepNightScore(perfectNight().copy(deepMin = null, remMin = null))

        assertNull(scored.architectureScore)
        assertNull(scored.deepPct)
        // duration 100 * .30 + efficiency 100 * .25 + continuity 100 * .15 + latency 100 * .05 = 75 / .75
        assertEquals(100, scored.score)
    }

    @Test
    fun `fully missing night cannot be scored`() {
        val scored =
            calc.sleepNightScore(
                InsightCalculators.SleepNightInput(
                    epochDay = 1L,
                    totalSleepMin = null,
                    timeInBedMin = null,
                    deepMin = null,
                    remMin = null,
                    wasoMin = null,
                    awakenings = null,
                    latencyMin = null,
                    sleepOnsetMs = null,
                    wakeMs = null,
                ),
            )

        assertNull(scored.score)
    }

    @Test
    fun `latency extremes score zero`() {
        assertEquals(0, calc.sleepNightScore(perfectNight().copy(latencyMin = 4)).latencyScore)
        assertEquals(0, calc.sleepNightScore(perfectNight().copy(latencyMin = 31)).latencyScore)
        assertEquals(100, calc.sleepNightScore(perfectNight().copy(latencyMin = 12)).latencyScore)
    }

    // ------------------------------------------------------------------
    // 2. Sleep debt.
    // ------------------------------------------------------------------

    private fun debtNights(hours: List<Double>): List<InsightCalculators.SleepNightInput> =
        hours.mapIndexed { index, h ->
            InsightCalculators.SleepNightInput(
                epochDay = 100L - index,
                totalSleepMin = (h * 60).toInt(),
                timeInBedMin = (h * 60).toInt() + 20,
                deepMin = null,
                remMin = null,
                wasoMin = null,
                awakenings = null,
                latencyMin = null,
                sleepOnsetMs = 1L,
                wakeMs = 2L,
            )
        }

    @Test
    fun `debt math matches the weighted sum`() {
        // 5 nights of 7h against an 8h need, slots 0..4.
        // w(i) = 1 - 0.75*i/13 -> sum = 5 - 0.75*10/13 = 4.423
        val debt = calc.sleepDebt(debtNights(listOf(7.0, 7.0, 7.0, 7.0, 7.0)), 8f)

        assertNotNull(debt)
        assertTrue(abs(debt - 4.423f) < 0.01f, "expected ~4.42h, got $debt")
    }

    @Test
    fun `full need leaves no debt`() {
        assertEquals(0f, calc.sleepDebt(debtNights(List(14) { 8.0 }), 8f))
    }

    @Test
    fun `debt clamps at ten hours`() {
        assertEquals(10f, calc.sleepDebt(debtNights(List(14) { 4.0 }), 8f))
    }

    @Test
    fun `fewer than five valid nights hides the number`() {
        assertNull(calc.sleepDebt(debtNights(listOf(6.0, 6.0, 6.0, 6.0)), 8f))
    }

    @Test
    fun `missing nights skip but keep their slot weight`() {
        val nights =
            listOf(
                InsightCalculators.SleepNightInput(100L, 360, 380, null, null, null, null, null, 1L, 2L),
                InsightCalculators.SleepNightInput(99L, null, null, null, null, null, null, null, null, null),
                InsightCalculators.SleepNightInput(98L, 360, 380, null, null, null, null, null, 1L, 2L),
                InsightCalculators.SleepNightInput(97L, 360, 380, null, null, null, null, null, 1L, 2L),
                InsightCalculators.SleepNightInput(96L, 360, 380, null, null, null, null, null, 1L, 2L),
                InsightCalculators.SleepNightInput(95L, 360, 380, null, null, null, null, null, 1L, 2L),
            )
        // 5 valid nights x 2h shortfall, slots 0,2,3,4,5
        val weights = listOf(0, 2, 3, 4, 5).map { i -> 1 - 0.75 * i / 13 }
        val expected = 2.0 * weights.sum()
        val debt = calc.sleepDebt(nights, 8f)

        assertNotNull(debt)
        assertTrue(abs(debt - expected) < 0.01, "expected $expected, got $debt")
    }

    // ------------------------------------------------------------------
    // 3. SRI.
    // ------------------------------------------------------------------

    private fun strip(
        day: Long,
        asleepRanges: List<IntRange>,
    ): SleepDayStrip {
        val asleep = BooleanArray(1440)
        for (range in asleepRanges) {
            for (minute in range) asleep[minute] = true
        }
        return SleepDayStrip(day, asleep)
    }

    @Test
    fun `identical week scores sri one hundred`() {
        val night = listOf(1380..1439, 0..419) // 23:00 -> 06:59
        val strips = (0L..6L).map { strip(100L + it, night) }

        assertEquals(100, calc.sleepRegularityIndex(strips))
    }

    @Test
    fun `alternating days score sri zero`() {
        // Blocks of two identical days mixing a strip with its exact
        // complement: 3 fully-matching pairs and 3 zero-match pairs ->
        // agreement 0.5 -> SRI 0.
        val night = listOf(1380..1439, 0..419) // 23:00 -> 06:59
        val day = listOf(420..1379) // 07:00 -> 22:59, the complement
        val strips =
            listOf(night, night, day, day, night, night, day)
                .mapIndexed { index, ranges -> strip(100L + index, ranges) }

        assertEquals(0, calc.sleepRegularityIndex(strips))
    }

    @Test
    fun `fewer than five day pairs returns null`() {
        val night = listOf(1380..1439, 0..419)
        val strips = (0L..4L).map { strip(100L + it, night) } // 4 pairs

        assertNull(calc.sleepRegularityIndex(strips))
    }

    @Test
    fun `non adjacent days do not pair`() {
        val night = listOf(1380..1439, 0..419)
        // 6 strips but with a gap: only 4 adjacent pairs
        val strips = listOf(100L, 101L, 102L, 104L, 105L, 106L).map { strip(it, night) }

        assertNull(calc.sleepRegularityIndex(strips))
    }

    // ------------------------------------------------------------------
    // 4. Social jetlag.
    // ------------------------------------------------------------------

    private fun jetlagNight(
        day: Long,
        onsetHour: Int,
        wakeHour: Int,
    ): InsightCalculators.SleepNightInput {
        // epochDay 100 = pick days so Mon-Fri vs Sat-Sun split is known.
        // dayOfWeek = floorMod(epochDay + 3, 7): 100 -> 103 % 7 = 5 = Saturday.
        val base = day * 86_400_000L
        return InsightCalculators.SleepNightInput(
            epochDay = day,
            totalSleepMin = 480,
            timeInBedMin = 500,
            deepMin = null,
            remMin = null,
            wasoMin = null,
            awakenings = null,
            latencyMin = null,
            sleepOnsetMs = base + onsetHour * 3_600_000L,
            wakeMs = base + wakeHour * 3_600_000L,
        )
    }

    @Test
    fun `social jetlag measures the weekday weekend gap`() {
        // 100=Sat, 101=Sun, 102=Mon, 103=Tue, 104=Wed, 105=Thu, 106=Fri
        val nights =
            listOf(
                jetlagNight(100L, 0, 9), // weekend: mid 04:30
                jetlagNight(101L, 0, 9), // weekend: mid 04:30
                jetlagNight(102L, 23, 31), // weekday: mid 03:00 (23:00 -> 07:00)
                jetlagNight(103L, 23, 31),
            )
        assertEquals(90, calc.socialJetlagMin(nights))
    }

    @Test
    fun `social jetlag needs enough data`() {
        assertNull(calc.socialJetlagMin(listOf(jetlagNight(102L, 23, 31))))
        assertNull(calc.socialJetlagMin(listOf(jetlagNight(100L, 0, 9), jetlagNight(102L, 23, 31))))
    }

    // ------------------------------------------------------------------
    // 5. Resting-HR baseline and z-score.
    // ------------------------------------------------------------------

    private fun rhrDays(
        bpms: List<Double>,
        completeness: Double = 1.0,
        endDay: Long = 121L,
    ): List<InsightCalculators.RestingHrDayInput> =
        bpms.mapIndexed { index, bpm ->
            InsightCalculators.RestingHrDayInput(endDay - (bpms.size - 1 - index), bpm, completeness)
        }

    @Test
    fun `z thresholds flag elevated and extreme`() {
        val days = rhrDays(List(21) { 60.0 })
        val baseline = calc.restingHrBaseline(days, todayEpochDay = 121L)

        assertNotNull(baseline)
        assertTrue(abs(baseline.mean - 60.0) < 0.001)

        // std is 0 here -> z is null, not infinite
        assertNull(calc.restingHrZ(70.0, baseline))

        val noisy = rhrDays(List(21) { i -> 60.0 + (i % 2) }) // 60/61 alternating, std = 0.5
        val noisyBaseline = calc.restingHrBaseline(noisy, todayEpochDay = 121L)
        assertNotNull(noisyBaseline)
        val z = assertNotNull(calc.restingHrZ(63.0, noisyBaseline)) // (63-60.5)/0.5 = 5
        assertTrue(z >= 3.0f)
    }

    @Test
    fun `low completeness days are excluded from the baseline`() {
        val days = rhrDays(List(21) { 60.0 }, completeness = 0.5)
        assertNull(calc.restingHrBaseline(days, todayEpochDay = 121L))
    }

    @Test
    fun `baseline excludes today`() {
        val days = rhrDays(List(21) { 60.0 } + 90.0, endDay = 121L) // last entry is "today"
        val baseline = calc.restingHrBaseline(days, todayEpochDay = 121L)
        assertNotNull(baseline)
        assertTrue(abs(baseline.mean - 60.0) < 0.001)
        assertEquals(21, baseline.days)
    }

    // ------------------------------------------------------------------
    // 6. Recovery score.
    // ------------------------------------------------------------------

    @Test
    fun `recovery squash maps driver z to bands`() {
        val good =
            calc.recoveryScore(
                InsightCalculators.RecoveryInput(rhrZ = 1.0, debtZ = 1.0, hrvZ = 1.0, breathingZ = 1.0),
            )
        assertNotNull(good)
        // 100 / (1 + e^(-1.6 * 1.2)) = 87.2
        assertEquals(87, good)
        assertEquals(RecoveryBand.CHARGED, calc.recoveryBand(good, 10))

        val bad =
            calc.recoveryScore(
                InsightCalculators.RecoveryInput(rhrZ = -1.0, debtZ = -1.0, hrvZ = -1.0, breathingZ = -1.0),
            )
        assertNotNull(bad)
        assertEquals(22, bad)
        assertEquals(RecoveryBand.DEPLETED, calc.recoveryBand(bad, 10))

        val mid =
            calc.recoveryScore(
                InsightCalculators.RecoveryInput(rhrZ = 0.0, debtZ = 0.0, hrvZ = 0.0, breathingZ = 0.0),
            )
        assertNotNull(mid)
        // 100 / (1 + e^(-0.32)) = 57.9 -> COASTING
        assertEquals(58, mid)
        assertEquals(RecoveryBand.COASTING, calc.recoveryBand(mid, 10))
    }

    @Test
    fun `missing hrv driver rescales the remaining weights`() {
        val withHrv =
            calc.recoveryScore(
                InsightCalculators.RecoveryInput(rhrZ = 1.0, debtZ = 1.0, hrvZ = 1.0, breathingZ = 1.0),
            )
        val withoutHrv =
            calc.recoveryScore(
                InsightCalculators.RecoveryInput(rhrZ = 1.0, debtZ = 1.0, hrvZ = null, breathingZ = 1.0),
            )
        // z is still 1.0 after rescaling, so the score is identical
        assertEquals(withHrv, withoutHrv)
    }

    @Test
    fun `no drivers means no score`() {
        assertNull(
            calc.recoveryScore(
                InsightCalculators.RecoveryInput(rhrZ = null, debtZ = null, hrvZ = null, breathingZ = null),
            ),
        )
    }

    @Test
    fun `under seven days calibrates`() {
        assertEquals(RecoveryBand.CALIBRATING, calc.recoveryBand(90, 6))
        assertEquals(RecoveryBand.CALIBRATING, calc.recoveryBand(null, 30))
    }

    // ------------------------------------------------------------------
    // 7. OLS slopes.
    // ------------------------------------------------------------------

    @Test
    fun `ols slope finds the trend`() {
        val up = listOf(0.0 to 60.0, 1.0 to 61.0, 2.0 to 62.0).map { InsightCalculators.OlsPoint(it.first, it.second) }
        val upSlope = assertNotNull(calc.olsSlope(up))
        assertTrue(abs(upSlope - 1.0f) < 0.001f)

        val flat = listOf(0.0 to 60.0, 5.0 to 60.0).map { InsightCalculators.OlsPoint(it.first, it.second) }
        val flatSlope = assertNotNull(calc.olsSlope(flat))
        assertTrue(abs(flatSlope) < 0.001f)

        assertNull(calc.olsSlope(listOf(InsightCalculators.OlsPoint(0.0, 1.0))))
        assertNull(calc.olsSlope(emptyList()))
    }

    // ------------------------------------------------------------------
    // 8. Correlations.
    // ------------------------------------------------------------------

    @Test
    fun `pearson finds strong relationships`() {
        val xs = (1..12).map { it.toDouble() }
        val pos = assertNotNull(calc.pearson(xs, xs.map { it * 2 }))
        assertTrue(abs(pos - 1.0) < 0.001)
        val neg = assertNotNull(calc.pearson(xs, xs.map { -it }))
        assertTrue(abs(neg + 1.0) < 0.001)
        assertNull(calc.pearson(List(12) { 5.0 }, xs)) // no variance in x
        assertNull(calc.pearson(listOf(1.0), listOf(2.0)))
    }

    @Test
    fun `correlation cards need strength and sample size`() {
        assertNotNull(calc.correlationCard(0.55, 12, "T", "B"))
        assertNotNull(calc.correlationCard(-0.8, 30, "T", "B"))
        assertNull(calc.correlationCard(0.39, 30, "T", "B"))
        assertNull(calc.correlationCard(0.9, 9, "T", "B"))
        val card = calc.correlationCard(0.6, 15, "T", "B")
        assertEquals(InsightKind.CORRELATION, card?.kind)
    }

    @Test
    fun `pair series aligns with a day offset`() {
        val x = mapOf(1L to 8.0, 2L to 6.0, 3L to 7.0)
        val y = mapOf(2L to 50.0, 3L to 70.0, 4L to 60.0)
        val (xs, ys) = calc.pairSeries(x, y, yOffsetDays = 1)
        assertEquals(listOf(8.0, 6.0, 7.0), xs)
        assertEquals(listOf(50.0, 70.0, 60.0), ys)
    }

    // ------------------------------------------------------------------
    // 9. Rule-based cards.
    // ------------------------------------------------------------------

    private fun habitContext() =
        InsightCalculators.HabitContext(
            rhrZ = null,
            rhrStreakNights = 0,
            rhrStreakPct = null,
            recoveryBand = RecoveryBand.COASTING,
            recoveryScore = 55,
            sleepDebtHours = null,
            lastNightScore = 80,
            sri = 75,
            socialJetlagMin = 30,
            breathingMinutesToday = 10,
        )

    @Test
    fun `rhr z bands emit recovery cards`() {
        val elevated = calc.habitCards(habitContext().copy(rhrZ = 2.0f))
        assertTrue(elevated.any { it.kind == InsightKind.RECOVERY && it.title.contains("elevated") })

        val extreme = calc.habitCards(habitContext().copy(rhrZ = 3.5f))
        assertTrue(extreme.any { it.title.contains("unusually high") })

        val calm = calc.habitCards(habitContext().copy(rhrZ = 0.5f))
        assertTrue(calm.none { it.kind == InsightKind.RECOVERY })
    }

    @Test
    fun `rhr streak card uses the contract example`() {
        val cards = calc.habitCards(habitContext().copy(rhrStreakNights = 3, rhrStreakPct = 8.0))
        val card = cards.first { it.kind == InsightKind.RECOVERY }
        assertTrue(card.body.contains("8%"), card.body)
        assertTrue(card.body.contains("3 nights running"), card.body)
        assertTrue(card.body.contains("early wind-down"), card.body)
    }

    @Test
    fun `sleep debt card carries the number`() {
        val cards = calc.habitCards(habitContext().copy(sleepDebtHours = 4.2f))
        val card = cards.first { it.kind == InsightKind.SLEEP }
        assertTrue(card.body.contains("4.2"), card.body)
    }

    @Test
    fun `cards sort recovery first then sleep correlation trend habit`() {
        val cards =
            listOf(
                calc.habitCards(habitContext().copy(breathingMinutesToday = 0)).first { it.kind == InsightKind.HABIT },
                calc.habitCards(habitContext().copy(sleepDebtHours = 5f)).first { it.kind == InsightKind.SLEEP },
                calc.habitCards(habitContext().copy(rhrZ = 2f)).first { it.kind == InsightKind.RECOVERY },
            ) + calc.trendCards(0.8f, 10, null, 0)
        val sorted = calc.sortCardsByPriority(cards).map { it.kind }
        assertEquals(
            listOf(InsightKind.RECOVERY, InsightKind.SLEEP, InsightKind.TREND, InsightKind.HABIT),
            sorted,
        )
    }
}
