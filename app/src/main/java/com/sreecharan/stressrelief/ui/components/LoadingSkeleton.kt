package com.sreecharan.stressrelief.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sreecharan.stressrelief.ui.theme.AuroraShapeTokens

/**
 * Shimmer placeholder rows while a screen's UiState is Loading.
 */
@Composable
fun LoadingSkeleton(
    modifier: Modifier = Modifier,
    lines: Int = 3,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "shimmer",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(lines) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == lines - 1) 0.6f else 1f)
                    .height(56.dp)
                    .clip(AuroraShapeTokens.card)
                    .background(base.copy(alpha = alpha)),
            )
        }
    }
}
