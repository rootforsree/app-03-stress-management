package com.sreecharan.stressrelief.ui.theme

import androidx.compose.runtime.compositionLocalOf

/**
 * The resolved dark-theme value (Settings override applied), as a
 * CompositionLocal. Screens must read this instead of
 * `isSystemInDarkTheme()`: the manual Light/Dark override in Settings would
 * otherwise render the wrong gradient family and icon tints.
 *
 * Provided by MainActivity inside [AuraTheme]; defaults to false so previews
 * render the light dawn gradient.
 */
val LocalAuroraDark = compositionLocalOf { false }
