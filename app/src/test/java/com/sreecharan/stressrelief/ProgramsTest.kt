package com.sreecharan.stressrelief

import com.sreecharan.stressrelief.data.catalog.Programs
import com.sreecharan.stressrelief.data.model.ProgramProgress
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Pure-JVM tests for the program catalog join and day-completion math. */
class ProgramsTest {
    @Test
    fun `catalog holds three seven-day programs`() {
        assertEquals(3, Programs.all.size)
        Programs.all.forEach { assertEquals(7, it.days.size) }
    }

    @Test
    fun `every program day references a real pattern`() {
        val ids =
            com.sreecharan.stressrelief.data.catalog.BreathingPatterns.all
                .map { it.id }
                .toSet()
        Programs.all.flatMap { it.days }.forEach { day ->
            assertTrue(day.patternId in ids, "Unknown pattern ${day.patternId}")
        }
    }

    @Test
    fun `join fills defaults for unstarted programs`() {
        val joined = Programs.withProgress(emptyList())

        assertEquals(3, joined.size)
        joined.forEach {
            assertEquals(1, it.currentDay)
            assertTrue(it.completedDays.isEmpty())
            assertEquals(0, it.startedMs)
        }
        assertEquals(Programs.all.map { it.id }, joined.map { it.programId })
    }

    @Test
    fun `join keeps stored rows for started programs`() {
        val stored = listOf(ProgramProgress("calm-reset", 3, listOf(1, 2), 123L))
        val joined = Programs.withProgress(stored)

        val calm = joined.first { it.programId == "calm-reset" }
        assertEquals(3, calm.currentDay)
        assertEquals(listOf(1, 2), calm.completedDays)
    }

    @Test
    fun `completing a day advances and dedupes`() {
        val existing = ProgramProgress("calm-reset", 3, listOf(1, 2), 100L)
        val updated = Programs.afterDayCompleted(existing, "calm-reset", 3, 999L)

        assertEquals(listOf(1, 2, 3), updated.completedDays)
        assertEquals(4, updated.currentDay)
        assertEquals(100L, updated.startedMs)
    }

    @Test
    fun `first completed day stamps startedMs`() {
        val updated = Programs.afterDayCompleted(null, "sleep-better", 1, 555L)

        assertEquals(listOf(1), updated.completedDays)
        assertEquals(2, updated.currentDay)
        assertEquals(555L, updated.startedMs)
    }

    @Test
    fun `day seven completion clamps current day`() {
        val existing = ProgramProgress("focus-builder", 7, listOf(1, 2, 3, 4, 5, 6), 100L)
        val updated = Programs.afterDayCompleted(existing, "focus-builder", 7, 200L)

        assertEquals(7, updated.completedDays.size)
        assertEquals(7, updated.currentDay)
    }

    @Test
    fun `journal prompts rotate twenty`() {
        assertEquals(20, com.sreecharan.stressrelief.data.catalog.journalPrompts.size)
        assertTrue(
            com.sreecharan.stressrelief.data.catalog.journalPrompts
                .all { it.isNotBlank() },
        )
    }

    @Test
    fun `sleep sounds list the five generated tones`() {
        val sounds = com.sreecharan.stressrelief.data.catalog.sleepSounds
        assertEquals(listOf("rain", "ocean", "white-noise", "brown-noise", "chime-pads"), sounds.map { it.id })
    }
}
