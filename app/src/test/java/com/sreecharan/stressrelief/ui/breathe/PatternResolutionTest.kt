package com.sreecharan.stressrelief.ui.breathe

import com.sreecharan.stressrelief.data.model.BreathingSession
import com.sreecharan.stressrelief.data.model.CustomPattern
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pattern id resolution for the breathing session: custom ids resolve from
 * the user's saved patterns, catalog ids from the static catalog, and
 * anything unknown falls back to "4-4-6" without throwing.
 */
class PatternResolutionTest {
    private val custom =
        CustomPattern(
            id = "custom-abc123",
            name = "My Calm",
            inhaleSec = 6,
            holdSec = 2,
            exhaleSec = 6,
            holdAfterSec = 2,
            rounds = 5,
        )

    @Test
    fun `custom id resolves from saved customs`() {
        val spec = resolvePatternSpec("custom-abc123", listOf(custom))

        assertEquals("custom-abc123", spec.id)
        assertEquals("My Calm", spec.name)
        assertEquals(6, spec.inhaleSec)
        assertEquals(2, spec.holdSec)
        assertEquals(6, spec.exhaleSec)
        assertEquals(2, spec.holdAfterSec)
        assertEquals(5, spec.rounds)
    }

    @Test
    fun `unknown custom id falls back to 4-4-6`() {
        val spec = resolvePatternSpec("custom-deleted", listOf(custom))

        assertEquals("4-4-6", spec.id)
        assertEquals(4, spec.inhaleSec)
        assertEquals(6, spec.exhaleSec)
    }

    @Test
    fun `catalog id resolves from the catalog`() {
        val spec = resolvePatternSpec("box", listOf(custom))

        assertEquals("box", spec.id)
        assertEquals(4, spec.inhaleSec)
        assertEquals(4, spec.holdSec)
        assertEquals(4, spec.exhaleSec)
        assertEquals(4, spec.holdAfterSec)
    }

    @Test
    fun `unknown id falls back to 4-4-6`() {
        val spec = resolvePatternSpec("nope", emptyList())

        assertEquals("4-4-6", spec.id)
    }

    @Test
    fun `empty custom prefix still falls back safely`() {
        val spec = resolvePatternSpec("custom-", emptyList())

        assertEquals("4-4-6", spec.id)
    }
}

/**
 * The 7-day diary buckets sessions into calendar days, oldest first, ending
 * today. Days without sessions report zero.
 */
class DiaryDaysTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now(zone)

    private fun session(
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

    @Test
    fun `sessions group into the last seven days ending today`() {
        val sessions =
            listOf(
                session(1, 0, 600), // today: 10 min
                session(2, 0, 300), // today: +5 min
                session(3, 1, 120), // yesterday: 2 min
                session(4, 6, 60), // six days ago: 1 min
                session(5, 7, 3_600), // seven days ago: outside the window
            )

        val days = diaryDays(sessions)

        assertEquals(7, days.size)
        assertEquals(today.toEpochDay(), days.last().epochDay)
        assertTrue(days.last().isToday)
        assertTrue(days.dropLast(1).none { it.isToday })
        assertEquals(15f, days.last().minutes)
        assertEquals(2f, days[5].minutes)
        assertEquals(1f, days[0].minutes)
        assertEquals(0f, days[3].minutes)
    }

    @Test
    fun `empty sessions yield seven zero days`() {
        val days = diaryDays(emptyList())

        assertEquals(7, days.size)
        assertTrue(days.all { it.minutes == 0f })
        assertEquals(1, days.count { it.isToday })
    }
}
