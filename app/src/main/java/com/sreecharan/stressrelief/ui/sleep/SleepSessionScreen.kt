package com.sreecharan.stressrelief.ui.sleep

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.rememberReduceMotion
import com.sreecharan.stressrelief.ui.theme.AuraTheme
import kotlin.math.PI
import kotlin.math.sin

/**
 * Active wind-down player (SleepSessionRoute): full-screen dark immersive
 * surface with a slow aurora animation, the countdown, and a stop button.
 * Stopping (or timer expiry) records the session and finishes.
 */
@Composable
fun SleepSessionScreen(
    onFinished: () -> Unit,
    viewModel: SleepSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SleepSessionEvent.Finished -> onFinished()
            }
        }
    }

    when (val s = state) {
        SleepSessionUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        is SleepSessionUiState.Content -> {
            // The wind-down is an always-dark immersive surface (the doc's
            // "evening gradient mode"): pin the dark scheme so the roles below
            // stay correct even when the app theme is light.
            AuraTheme(darkTheme = true) {
                SleepSessionContent(
                    state = s,
                    onStop = viewModel::onStopClicked,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepSessionContent(
    state: SleepSessionUiState.Content,
    onStop: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        AuroraBackground()
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.sleep_session_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text =
                    state.remainingSec?.let { formatCountdown(it) }
                        ?: stringResource(R.string.sleep_session_time_placeholder),
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    if (state.remainingSec == null) {
                        stringResource(R.string.sleep_session_no_timer)
                    } else {
                        stringResource(R.string.sleep_session_playing)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.layerIds.forEach { id ->
                    val name = SleepSoundLayers.byId(id)?.nameRes
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                text = if (name != null) stringResource(name) else id,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onStop,
                shape = RoundedCornerShape(32.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color(0x33FFFFFF),
                        contentColor = Color.White,
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
            ) {
                Text(
                    text = stringResource(R.string.sleep_session_stop),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Slow aurora: three large radial blobs in the night-aurora palette
 * (#0D9488, #0891B2, #7C6CF0, #E0558A) drifting on ~20 s loops over the
 * dark surface. With reduced motion on, the drift freezes at a fixed
 * pleasant offset instead of animating.
 */
@Composable
private fun AuroraBackground() {
    val reduceMotion = rememberReduceMotion()
    val drift: Float =
        if (reduceMotion) {
            0.8f
        } else {
            val transition = rememberInfiniteTransition(label = "aurora")
            transition
                .animateFloat(
                    initialValue = 0f,
                    targetValue = (2 * PI).toFloat(),
                    animationSpec =
                        infiniteRepeatable(
                            animation = tween(20000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart,
                        ),
                    label = "drift",
                ).value
        }
    val base = MaterialTheme.colorScheme.background
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(base)
        val w = size.width
        val h = size.height
        val blobs =
            listOf(
                Triple(Color(0xFF0D9488), 0.5f + 0.28f * sin(drift), 0.32f + 0.12f * sin(drift * 1.3f)),
                Triple(Color(0xFF7C6CF0), 0.5f + 0.30f * sin(drift + 2.1f), 0.62f + 0.10f * sin(drift * 0.9f + 1f)),
                Triple(Color(0xFFE0558A), 0.5f + 0.26f * sin(drift + 4.2f), 0.45f + 0.14f * sin(drift * 1.1f + 2f)),
            )
        blobs.forEach { (color, fx, fy) ->
            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(w * fx, h * fy),
                        radius = w * 0.75f,
                    ),
                radius = w * 0.75f,
                center = Offset(w * fx, h * fy),
            )
        }
    }
}

private fun formatCountdown(remainingSec: Long): String {
    val minutes = remainingSec / 60
    val seconds = remainingSec % 60
    return "%02d:%02d".format(minutes, seconds)
}
