package com.sreecharan.stressrelief.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Signature Aurora gradients. These are brand constants: they never change
 * with dynamic color and never sit behind body text (contrast first).
 */
object AuroraBrushes {
    private val dawnStops =
        listOf(
            Color(0xFF2DD4BF),
            Color(0xFF22D3EE),
            Color(0xFFA78BFA),
            Color(0xFFF9A8D4),
        )
    private val nightStops =
        listOf(
            Color(0xFF0D9488),
            Color(0xFF0891B2),
            Color(0xFF7C6CF0),
            Color(0xFFE0558A),
        )

    /** Dawn gradient, light mode: teal -> cyan -> violet -> pink. */
    val dawn: Brush = Brush.linearGradient(colors = dawnStops)

    /** Night aurora, dark mode: jewel tones that glow on deep surfaces. */
    val nightAurora: Brush = Brush.linearGradient(colors = nightStops)

    /** Raw hero stops for building custom brushes (chart fills, header bands). */
    fun heroStops(darkTheme: Boolean): List<Color> = if (darkTheme) nightStops else dawnStops

    /**
     * Calm wash: the signature gradient at 12-18% opacity, for tinting
     * screen backgrounds without hurting readability.
     */
    fun calmWash(
        darkTheme: Boolean,
        alpha: Float = 0.15f,
    ): Brush {
        val stops =
            if (darkTheme) {
                listOf(Color(0xFF0D9488), Color(0xFF0891B2), Color(0xFF7C6CF0), Color(0xFFE0558A))
            } else {
                listOf(Color(0xFF2DD4BF), Color(0xFF22D3EE), Color(0xFFA78BFA), Color(0xFFF9A8D4))
            }
        return Brush.linearGradient(
            colors = stops.map { it.copy(alpha = alpha) },
            start = Offset.Zero,
            end = Offset.Infinite,
        )
    }

    /** Picks the mode-appropriate hero gradient. */
    fun hero(darkTheme: Boolean): Brush = if (darkTheme) nightAurora else dawn
}
