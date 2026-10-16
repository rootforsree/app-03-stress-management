package com.sreecharan.stressrelief.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Aurora Calm color schemes. Hex values are verbatim from
 * research/design-direction.md section 1. Do not "improve" them.
 */
val AuraLightColors =
    lightColorScheme(
        primary = Color(0xFF00838F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFA6F3F5),
        onPrimaryContainer = Color(0xFF002021),
        secondary = Color(0xFF5B5BD6),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE3E0FF),
        onSecondaryContainer = Color(0xFF17144D),
        tertiary = Color(0xFFC2185B),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFD9E3),
        onTertiaryContainer = Color(0xFF3F001D),
        background = Color(0xFFF7FDFD),
        surface = Color(0xFFF7FDFD),
        surfaceVariant = Color(0xFFE4F2F2),
        surfaceContainerHigh = Color(0xFFEAF6F6),
        onSurface = Color(0xFF171D1E),
        onSurfaceVariant = Color(0xFF3F494B),
        outline = Color(0xFF6F797B),
        error = Color(0xFFBA1A1A),
    )

/**
 * Night aurora: a separately designed dark scheme, not an inverted light
 * scheme. Accents sit at tone 80 so they glow instead of vibrating.
 */
val AuraDarkColors =
    darkColorScheme(
        primary = Color(0xFF4DD0E1),
        onPrimary = Color(0xFF00363D),
        primaryContainer = Color(0xFF004F58),
        onPrimaryContainer = Color(0xFFA6F3F5),
        secondary = Color(0xFFC2C3FF),
        onSecondary = Color(0xFF2E2E6E),
        secondaryContainer = Color(0xFF43437A),
        onSecondaryContainer = Color(0xFFE3E0FF),
        tertiary = Color(0xFFFFB1C8),
        onTertiary = Color(0xFF65002E),
        tertiaryContainer = Color(0xFF7A2950),
        onTertiaryContainer = Color(0xFFFFD9E3),
        background = Color(0xFF0E1516),
        surface = Color(0xFF0E1516),
        surfaceVariant = Color(0xFF1E2A2B),
        surfaceContainerHigh = Color(0xFF263434),
        onSurface = Color(0xFFDDE4E4),
        onSurfaceVariant = Color(0xFFB0BCBD),
        outline = Color(0xFF8A9698),
    )

/**
 * Root app theme. The branded Aurora scheme is the default; dynamic color is
 * strictly opt-in via Settings ("Match my wallpaper") and never overrides
 * the signature gradients in [AuroraBrushes].
 */
@Composable
fun AuraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            darkTheme -> {
                AuraDarkColors
            }

            else -> {
                AuraLightColors
            }
        }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuroraTypography,
        shapes = AuroraShapes,
        content = content,
    )
}
