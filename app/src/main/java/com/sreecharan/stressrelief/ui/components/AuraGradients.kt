package com.sreecharan.stressrelief.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import com.sreecharan.stressrelief.ui.theme.MotionTokens
import kotlinx.coroutines.delay

/**
 * Shared gradient helpers. The hero and wash gradients delegate to
 * [AuroraBrushes] so every screen uses identical stops; sweep/fade variants
 * reuse the same hex stops. Dark-mode resolution comes from
 * [LocalAuroraDark] (the Settings override), never the raw OS setting.
 */
object AuraGradients {
    /** Hero gradient: dawn in light mode, night aurora in dark mode. */
    @Composable
    @ReadOnlyComposable
    fun dawn(): Brush = AuroraBrushes.hero(LocalAuroraDark.current)

    /** Sweep gradient for progress rings. Same stops as the hero gradient. */
    @Composable
    @ReadOnlyComposable
    fun sweep(): Brush = Brush.sweepGradient(AuroraBrushes.heroStops(LocalAuroraDark.current))

    /**
     * Faint background wash. Alpha stays in the 0.12-0.18 band per the design doc
     * so body text contrast stays safe.
     */
    @Composable
    @ReadOnlyComposable
    fun calmWash(alpha: Float = 0.15f): Brush = AuroraBrushes.calmWash(LocalAuroraDark.current, alpha)

    /** Vertical fade used behind chart area fills. */
    @Composable
    @ReadOnlyComposable
    fun verticalFade(alpha: Float = 0.35f): Brush {
        val stops = AuroraBrushes.heroStops(LocalAuroraDark.current)
        return Brush.verticalGradient(
            listOf(
                stops[0].copy(alpha = alpha),
                stops[2].copy(alpha = alpha * 0.5f),
                stops[3].copy(alpha = 0f),
            ),
        )
    }
}

/**
 * True when the OS "remove animations" accessibility setting is on
 * (Settings.Global.ANIMATOR_DURATION_SCALE == 0). Screens fall back to
 * static content plus the text/timer in that case.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

/**
 * Staggered card entrance: fade plus a small rise, delayed per index.
 * Uses the shared [MotionTokens] stagger window (30-50ms per item).
 * With reduced motion on, content renders immediately with no animation.
 *
 * Implemented as alpha/translation animations on a full-size box, never
 * AnimatedVisibility: the content is always laid out at its final size, so
 * staggered items never collapse to zero height (which yanked LazyColumn
 * scroll position when conditional items were inserted above them) and no
 * slide clip bounds ever cut the content (which produced the clipped
 * section-title artifacts: only the first letter rendering at the card's
 * left edge).
 */
@Composable
fun StaggerIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (rememberReduceMotion()) {
        Box(modifier) { content() }
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(MotionTokens.staggerDelayMs(index).toLong())
        visible = true
    }
    val risePx = with(LocalDensity.current) { MotionTokens.ENTRANCE_RISE_DP.dp.roundToPx() }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = MotionTokens.GentleSpring,
        label = "staggerFade",
    )
    val offsetYPx by animateIntAsState(
        targetValue = if (visible) 0 else risePx,
        animationSpec = MotionTokens.gentleSpring(),
        label = "staggerRise",
    )
    Box(
        modifier.graphicsLayer {
            this.alpha = alpha
            this.translationY = offsetYPx.toFloat()
        },
    ) {
        content()
    }
}

/** Small section heading used across screens. */
@Composable
fun AuraSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
    }
}
