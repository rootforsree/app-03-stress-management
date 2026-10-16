package com.sreecharan.stressrelief.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Aurora shape language (M3 Expressive): corner-radius contrast is the focus
 * tool. Hero moments get oversized radii, CTAs are pills so the eye lands
 * on them against squarer cards.
 */
object AuroraShapeTokens {
    /** Hero moments: breathing orb cards, mood check-in header. */
    val hero = RoundedCornerShape(28.dp)

    /** Standard cards. */
    val card = RoundedCornerShape(22.dp)

    /** Primary CTAs: always a pill, never the card radius. */
    val pill = CircleShape

    /** Chips and small tags. */
    val chip = RoundedCornerShape(12.dp)
}

val AuroraShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
