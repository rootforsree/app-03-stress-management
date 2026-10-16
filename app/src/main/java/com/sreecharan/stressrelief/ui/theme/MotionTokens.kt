package com.sreecharan.stressrelief.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring

/**
 * Central motion tokens (M3 Expressive, physics-based). Springs must feel
 * like breathing, not like a game: gentle settle everywhere, bounce reserved
 * for celebrations.
 */
object MotionTokens {
    /**
     * Default spring: gentle settle, no harsh bounce.
     * spring(stiffness = 250f, dampingRatio = 0.85f).
     */
    val GentleSpring: FiniteAnimationSpec<Float> = spring(stiffness = 250f, dampingRatio = 0.85f)

    /** Celebrations only (streak milestones, achievements). */
    val CelebrationSpring: FiniteAnimationSpec<Float> = spring(stiffness = 300f, dampingRatio = 0.6f)

    /** Generic typed springs for non-Float targets (Dp, Offset, ...). */
    fun <T> gentleSpring(): FiniteAnimationSpec<T> = spring(stiffness = 250f, dampingRatio = 0.85f)

    fun <T> celebrationSpring(): FiniteAnimationSpec<T> = spring(stiffness = 300f, dampingRatio = 0.6f)

    /** Entrance stagger window: 30-50ms per item, fade + 16dp rise. */
    const val STAGGER_MIN_MS = 30
    const val STAGGER_STEP_MS = 40
    const val STAGGER_MAX_TOTAL_MS = 400

    /** Stagger delay for the [index]-th entering item, capped so long lists stay snappy. */
    fun staggerDelayMs(index: Int): Int = (STAGGER_MIN_MS + index * STAGGER_STEP_MS).coerceAtMost(STAGGER_MAX_TOTAL_MS)

    /** Entrance rise distance for staggered cards. */
    const val ENTRANCE_RISE_DP = 16
}
