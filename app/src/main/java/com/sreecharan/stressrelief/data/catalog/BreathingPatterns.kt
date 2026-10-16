package com.sreecharan.stressrelief.data.catalog

data class BreathingPattern(
    val id: String,
    val name: String,
    val goal: String,
    val inhaleSec: Int,
    val holdSec: Int,
    val exhaleSec: Int,
    val holdAfterSec: Int,
    val rounds: Int,
    val description: String,
)

object BreathingPatterns {
    val all: List<BreathingPattern> =
        listOf(
            BreathingPattern(
                id = "4-4-6",
                name = "4-4-6 Calm",
                goal = "CALM",
                inhaleSec = 4,
                holdSec = 0,
                exhaleSec = 6,
                holdAfterSec = 0,
                rounds = 6,
                description = "Long exhales slow the heart rate. Use anytime tension builds.",
            ),
            BreathingPattern(
                id = "coherent",
                name = "Coherent Breathing",
                goal = "CALM",
                inhaleSec = 5,
                holdSec = 0,
                exhaleSec = 5,
                holdAfterSec = 0,
                rounds = 8,
                description = "Even five-second breaths balance the nervous system.",
            ),
            BreathingPattern(
                id = "4-7-8",
                name = "4-7-8 Sleep",
                goal = "SLEEP",
                inhaleSec = 4,
                holdSec = 7,
                exhaleSec = 8,
                holdAfterSec = 0,
                rounds = 4,
                description = "The classic wind-down pattern. Signals the body that the day is done.",
            ),
            BreathingPattern(
                id = "deep-rest",
                name = "Deep Rest",
                goal = "SLEEP",
                inhaleSec = 4,
                holdSec = 0,
                exhaleSec = 8,
                holdAfterSec = 0,
                rounds = 6,
                description = "Slow double-length exhales melt pre-sleep restlessness.",
            ),
            BreathingPattern(
                id = "box",
                name = "Box Breathing",
                goal = "FOCUS",
                inhaleSec = 4,
                holdSec = 4,
                exhaleSec = 4,
                holdAfterSec = 4,
                rounds = 6,
                description = "Equal sides steady the mind before deep work.",
            ),
            BreathingPattern(
                id = "energize",
                name = "Energize",
                goal = "FOCUS",
                inhaleSec = 6,
                holdSec = 0,
                exhaleSec = 2,
                holdAfterSec = 0,
                rounds = 8,
                description = "Quick exhales wake you up when focus is fading.",
            ),
            BreathingPattern(
                id = "sos-60",
                name = "SOS 60",
                goal = "SOS",
                inhaleSec = 4,
                holdSec = 0,
                exhaleSec = 6,
                holdAfterSec = 0,
                rounds = 5,
                description = "One sixty-second reset for acute stress. Breathe through it.",
            ),
        )

    fun forGoal(goal: String): List<BreathingPattern> =
        all.filter { it.goal == goal }.ifEmpty { all.filter { it.goal == "CALM" } }

    fun byId(id: String): BreathingPattern? = all.firstOrNull { it.id == id }
}
