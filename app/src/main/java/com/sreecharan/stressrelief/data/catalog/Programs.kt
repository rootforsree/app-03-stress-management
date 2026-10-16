package com.sreecharan.stressrelief.data.catalog

import com.sreecharan.stressrelief.data.model.ProgramProgress

data class ProgramDay(
    val programId: String,
    val day: Int,
    val patternId: String,
    val minutes: Int,
    val title: String,
)

data class Program(
    val id: String,
    val title: String,
    val description: String,
    val days: List<ProgramDay>,
)

object Programs {
    val all: List<Program> =
        listOf(
            Program(
                id = "calm-reset",
                title = "7-Day Calm Reset",
                description = "A week of gentle breathing to lower your baseline stress.",
                days =
                    listOf(
                        ProgramDay("calm-reset", 1, "4-4-6", 3, "Arrive"),
                        ProgramDay("calm-reset", 2, "coherent", 4, "Find the rhythm"),
                        ProgramDay("calm-reset", 3, "4-4-6", 4, "Slow the exhale"),
                        ProgramDay("calm-reset", 4, "coherent", 5, "Stay with it"),
                        ProgramDay("calm-reset", 5, "4-4-6", 5, "Soften the shoulders"),
                        ProgramDay("calm-reset", 6, "coherent", 6, "Deeper calm"),
                        ProgramDay("calm-reset", 7, "4-4-6", 7, "Carry it forward"),
                    ),
            ),
            Program(
                id = "sleep-better",
                title = "Sleep Better Week",
                description = "Evening breathing rituals that make falling asleep easier.",
                days =
                    listOf(
                        ProgramDay("sleep-better", 1, "deep-rest", 4, "Wind down"),
                        ProgramDay("sleep-better", 2, "4-7-8", 4, "Meet 4-7-8"),
                        ProgramDay("sleep-better", 3, "deep-rest", 5, "Longer exhales"),
                        ProgramDay("sleep-better", 4, "4-7-8", 5, "Settle the mind"),
                        ProgramDay("sleep-better", 5, "deep-rest", 6, "Evening ritual"),
                        ProgramDay("sleep-better", 6, "4-7-8", 6, "Quiet the noise"),
                        ProgramDay("sleep-better", 7, "deep-rest", 8, "Sleep deep"),
                    ),
            ),
            Program(
                id = "focus-builder",
                title = "Focus Builder",
                description = "Short sessions that sharpen attention before demanding work.",
                days =
                    listOf(
                        ProgramDay("focus-builder", 1, "box", 3, "Sharpen"),
                        ProgramDay("focus-builder", 2, "energize", 3, "Wake up"),
                        ProgramDay("focus-builder", 3, "box", 4, "Steady the mind"),
                        ProgramDay("focus-builder", 4, "energize", 4, "Beat the slump"),
                        ProgramDay("focus-builder", 5, "box", 5, "Deep work prep"),
                        ProgramDay("focus-builder", 6, "energize", 5, "Full alert"),
                        ProgramDay("focus-builder", 7, "box", 7, "Locked in"),
                    ),
            ),
        )

    fun byId(id: String): Program? = all.firstOrNull { it.id == id }

    /**
     * Join stored rows with the catalog: every program appears, unstarted
     * programs get a fresh default row. Catalog order is preserved.
     */
    fun withProgress(stored: List<ProgramProgress>): List<ProgramProgress> =
        all.map { program ->
            stored.firstOrNull { it.programId == program.id }
                ?: ProgramProgress(
                    programId = program.id,
                    currentDay = 1,
                    completedDays = emptyList(),
                    startedMs = 0,
                )
        }

    /**
     * Pure day-completion math: dedupes the day, advances the current day
     * (clamped to 7), and stamps startedMs on the first completed day.
     */
    fun afterDayCompleted(
        existing: ProgramProgress?,
        programId: String,
        day: Int,
        nowMs: Long,
    ): ProgramProgress {
        val completed = (existing?.completedDays.orEmpty() + day.coerceIn(1, 7)).distinct().sorted()
        val nextDay = (completed.maxOrNull()?.plus(1) ?: 1).coerceAtMost(7)
        return ProgramProgress(
            programId = programId,
            currentDay = nextDay,
            completedDays = completed,
            startedMs = existing?.startedMs?.takeIf { it > 0 } ?: nowMs,
        )
    }
}
