package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.ui.graphics.Color

/**
 * Dawn gradient stops as plain colors (mirrors AuroraBrushes.dawn).
 * The breathing orb crossfades between these with the breath phase:
 * teal on inhale, violet on hold, pink on exhale. Night-aurora twins below
 * are used when [com.sreecharan.stressrelief.ui.theme.LocalAuroraDark] is set.
 */
val DawnTeal = Color(0xFF2DD4BF)
val DawnCyan = Color(0xFF22D3EE)
val DawnViolet = Color(0xFFA78BFA)
val DawnPink = Color(0xFFF9A8D4)

/** Night-aurora stops: jewel tones that glow on deep surfaces. */
val NightTeal = Color(0xFF0D9488)
val NightCyan = Color(0xFF0891B2)
val NightViolet = Color(0xFF7C6CF0)
val NightPink = Color(0xFFE0558A)
