package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuroraButton
import com.sreecharan.stressrelief.ui.components.rememberReduceMotion
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import com.sreecharan.stressrelief.ui.theme.MotionTokens
import kotlin.random.Random

private const val ORB_INHALE_SCALE = 1.35f
private const val ORB_REST_SCALE = 1.12f
private const val HALO_ALPHA = 0.35f
private const val ORB_SHEEN_ALPHA = 0.22f
private const val CONFETTI_COUNT = 64
private const val CONFETTI_SEED = 7
private const val CONFETTI_DURATION_MS = 2_200
private const val CONFETTI_START_BAND = 0.4f
private const val CONFETTI_MIN_SIZE_DP = 4f
private const val CONFETTI_SIZE_RANGE_DP = 7f
private const val CONFETTI_DRIFT = 0.25f
private const val CONFETTI_MIN_ALPHA = 0.55f
private const val CONFETTI_ALPHA_RANGE = 0.45f
private const val CONFETTI_FALL_SCREENS = 1.2f
private const val CONFETTI_RECT_SQUASH = 1.4f

/**
 * Breathing session route. The NavHost calls this inside
 * composable<BreathingSessionRoute>.
 *
 * @param programId optional program context: when the session was started
 * from a program day, the NavHost passes the previous entry's
 * savedStateHandle extras through here so the day is completed on finish.
 * @param onFinished called after the celebration (or an early exit).
 */
@Composable
fun BreathingSessionScreen(
    viewModel: BreathingSessionViewModel = hiltViewModel(),
    programId: String? = null,
    programDay: Int? = null,
    onFinished: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(programId, programDay) {
        viewModel.attachProgramContext(programId, programDay ?: -1)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BreathingSessionEvent.PhaseChanged -> {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }

                BreathingSessionEvent.NavigateBack -> {
                    onFinished()
                }
            }
        }
    }
    BreathingSessionContent(
        state = state,
        onPauseResume = viewModel::onPauseResume,
        onExitEarly = viewModel::onExitEarly,
    )
}

@Composable
private fun BreathingSessionContent(
    state: BreathingSessionUiState,
    onPauseResume: () -> Unit,
    onExitEarly: () -> Unit,
) {
    when (state) {
        BreathingSessionUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is BreathingSessionUiState.Ready -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.session_get_ready),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = state.patternName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        is BreathingSessionUiState.Active -> {
            ActiveSession(
                state = state,
                onPauseResume = onPauseResume,
                onExitEarly = onExitEarly,
            )
        }

        is BreathingSessionUiState.Celebrating -> {
            Celebration(state = state)
        }
    }
}

@Composable
private fun ActiveSession(
    state: BreathingSessionUiState.Active,
    onPauseResume: () -> Unit,
    onExitEarly: () -> Unit,
) {
    val darkTheme = LocalAuroraDark.current
    val reduceMotion = rememberReduceMotion()
    var showExitConfirm by remember { mutableStateOf(false) }

    // Orb choreography: one Animatable driven by a per-phase LaunchedEffect.
    // The high-churn scale value is read in graphicsLayer, never in
    // composition, so the orb stays at 60fps.
    val orbScale = remember { Animatable(1f) }
    LaunchedEffect(state.phase, state.round, state.isPaused, reduceMotion) {
        if (reduceMotion) {
            orbScale.snapTo(ORB_REST_SCALE)
            return@LaunchedEffect
        }
        if (state.isPaused) return@LaunchedEffect
        val target =
            if (state.phase == BreathPhase.INHALE || state.phase == BreathPhase.HOLD) {
                ORB_INHALE_SCALE
            } else {
                1f
            }
        val remainingMs = (state.phaseDurationMs - state.phaseElapsedMs).coerceAtLeast(1L).toInt()
        orbScale.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = remainingMs, easing = FastOutSlowInEasing),
        )
    }

    // Gradient crossfade with the phase: teal -> violet -> pink.
    // Night-aurora jewel tones in dark mode, dawn stops in light mode.
    val (teal, cyan, violet, pink) =
        if (darkTheme) {
            listOf(NightTeal, NightCyan, NightViolet, NightPink)
        } else {
            listOf(DawnTeal, DawnCyan, DawnViolet, DawnPink)
        }
    val colorTargets =
        when (state.phase) {
            BreathPhase.INHALE -> teal to cyan
            BreathPhase.HOLD -> cyan to violet
            BreathPhase.EXHALE -> violet to pink
            BreathPhase.HOLD_AFTER -> pink to teal
        }
    val orbColor1 by animateColorAsState(
        targetValue = colorTargets.first,
        animationSpec = MotionTokens.gentleSpring(),
        label = "orbColor1",
    )
    val orbColor2 by animateColorAsState(
        targetValue = colorTargets.second,
        animationSpec = MotionTokens.gentleSpring(),
        label = "orbColor2",
    )

    val phaseLabelRes =
        when (state.phase) {
            BreathPhase.INHALE -> R.string.phase_inhale
            BreathPhase.HOLD -> R.string.phase_hold
            BreathPhase.EXHALE -> R.string.phase_exhale
            BreathPhase.HOLD_AFTER -> R.string.phase_hold_after
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AuroraBrushes.calmWash(darkTheme)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { showExitConfirm = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.session_close),
                    )
                }
                Text(
                    text = state.patternName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(48.dp))
            }
            Spacer(Modifier.weight(1f))
            BreathingOrb(
                state = state,
                orbScale = orbScale,
                orbColor1 = orbColor1,
                orbColor2 = orbColor2,
                halo = AuroraBrushes.hero(darkTheme),
                darkTheme = darkTheme,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(phaseLabelRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.secondsRemainingInPhase.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.session_round, state.round, state.totalRounds),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                AuroraButton(
                    text =
                        stringResource(
                            if (state.isPaused) R.string.session_resume else R.string.session_pause,
                        ),
                    onClick = onPauseResume,
                    leadingIcon = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { showExitConfirm = true }) {
                    Text(stringResource(R.string.session_end))
                }
            }
        }
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text(stringResource(R.string.session_end_early_title)) },
            text = { Text(stringResource(R.string.session_end_early_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirm = false
                        onExitEarly()
                    },
                ) {
                    Text(stringResource(R.string.session_end_early_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) {
                    Text(stringResource(R.string.session_end_early_cancel))
                }
            },
        )
    }
}

@Composable
private fun BreathingOrb(
    state: BreathingSessionUiState.Active,
    orbScale: Animatable<Float, AnimationVector1D>,
    orbColor1: Color,
    orbColor2: Color,
    halo: Brush,
    darkTheme: Boolean,
) {
    val orbDesc = stringResource(R.string.session_orb_desc)
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(300.dp)
                .semantics { contentDescription = orbDesc },
    ) {
        // Aurora halo breathing with the orb.
        Box(
            modifier =
                Modifier
                    .size(280.dp)
                    .graphicsLayer {
                        val s = orbScale.value
                        scaleX = s
                        scaleY = s
                        alpha = HALO_ALPHA
                    }.background(halo, CircleShape),
        )
        // The living gradient creature.
        Box(
            modifier =
                Modifier
                    .size(200.dp)
                    .graphicsLayer {
                        scaleX = orbScale.value
                        scaleY = orbScale.value
                    }.background(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = ORB_SHEEN_ALPHA), orbColor1, orbColor2),
                        ),
                        CircleShape,
                    ),
        )
        // Gradient progress ring: round x of n. Night-aurora stops in dark mode.
        val trackColor = MaterialTheme.colorScheme.surfaceVariant
        val sweep =
            remember(darkTheme) {
                val stops =
                    if (darkTheme) {
                        listOf(NightTeal, NightCyan, NightViolet, NightPink, NightTeal)
                    } else {
                        listOf(DawnTeal, DawnCyan, DawnViolet, DawnPink, DawnTeal)
                    }
                Brush.sweepGradient(stops)
            }
        Canvas(modifier = Modifier.size(280.dp)) {
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                brush = sweep,
                startAngle = -90f,
                sweepAngle = 360f * state.totalProgress,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun Celebration(state: BreathingSessionUiState.Celebrating) {
    val pop by animateFloatAsState(
        targetValue = 1f,
        animationSpec = MotionTokens.CelebrationSpring,
        label = "celebrationPop",
    )
    // Milestone moment: pair the celebration pop with a light haptic tick.
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ConfettiOverlay()
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(120.dp)
                        .graphicsLayer {
                            scaleX = pop
                            scaleY = pop
                        }.background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.session_celebration_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.session_celebration_sub, state.minutes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private data class ConfettiParticle(
    val xFrac: Float,
    val yStartFrac: Float,
    val sizeDp: Float,
    val driftFrac: Float,
    val alpha: Float,
    val round: Boolean,
)

/** Confetti in tertiary pink only, per the design direction. */
@Composable
private fun ConfettiOverlay() {
    val pink = MaterialTheme.colorScheme.tertiary
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = CONFETTI_DURATION_MS))
    }
    val particles =
        remember {
            val rnd = Random(CONFETTI_SEED)
            List(CONFETTI_COUNT) {
                ConfettiParticle(
                    xFrac = rnd.nextFloat(),
                    yStartFrac = rnd.nextFloat() * CONFETTI_START_BAND,
                    sizeDp = rnd.nextFloat() * CONFETTI_SIZE_RANGE_DP + CONFETTI_MIN_SIZE_DP,
                    driftFrac = rnd.nextFloat() * CONFETTI_DRIFT - CONFETTI_DRIFT / 2f,
                    alpha = rnd.nextFloat() * CONFETTI_ALPHA_RANGE + CONFETTI_MIN_ALPHA,
                    round = rnd.nextBoolean(),
                )
            }
        }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val p = progress.value
        val fade = (1f - p).coerceIn(0f, 1f)
        particles.forEach { particle ->
            val x = (((particle.xFrac + particle.driftFrac * p) % 1f + 1f) % 1f) * size.width
            val y = ((particle.yStartFrac + p * CONFETTI_FALL_SCREENS) % 1f) * size.height
            val radius = particle.sizeDp.dp.toPx() / 2f
            val color = pink.copy(alpha = particle.alpha * fade)
            if (particle.round) {
                drawCircle(color = color, radius = radius, center = Offset(x, y))
            } else {
                drawRect(
                    color = color,
                    topLeft = Offset(x - radius, y - radius),
                    size = Size(radius * 2f, radius * CONFETTI_RECT_SQUASH),
                )
            }
        }
    }
}
