package com.sreecharan.stressrelief.ui.mood

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuraGradients
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark

/**
 * Mood check-in bottom sheet content. Builder 1 hosts this in a bottom-sheet
 * destination for MoodCheckInRoute (and the aura://mood deep link).
 *
 * Designed to finish in 10 seconds: one tap picks a mood, tags are optional,
 * the note is optional, one Save button.
 */
@Composable
fun MoodCheckInSheet(
    onDismiss: () -> Unit,
    viewModel: MoodCheckInViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    if (state is MoodCheckInUiState.Saved) {
        LaunchedEffect(viewModel) {
            // Save is a milestone moment: pair it with a light haptic tick.
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDismiss()
        }
    }
    MoodCheckInContent(
        state = state,
        onMoodSelected = viewModel::onMoodSelected,
        onActivityToggled = viewModel::onActivityToggled,
        onNoteChanged = viewModel::onNoteChanged,
        onSave = viewModel::onSave,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodCheckInContent(
    state: MoodCheckInUiState,
    onMoodSelected: (Int) -> Unit,
    onActivityToggled: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editing = state as? MoodCheckInUiState.Editing
    Column(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Drag handle.
        Box(
            Modifier
                .padding(top = 12.dp, bottom = 16.dp)
                .size(width = 40.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)),
        )
        // Gradient header band; text below sits on the sheet surface, not on the gradient.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(AuraGradients.dawn()),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.mood_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.mood_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top,
        ) {
            MoodScale.forEach { score ->
                MoodFaceButton(
                    score = score,
                    selected = editing?.mood == score,
                    enabled = editing != null,
                    onClick = { onMoodSelected(score) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))

        Text(
            stringResource(R.string.mood_activities_label),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MoodActivities.forEach { activity ->
                val selected = editing?.activities?.contains(activity.key) == true
                FilterChip(
                    selected = selected,
                    onClick = { onActivityToggled(activity.key) },
                    label = { Text(stringResource(activity.labelRes)) },
                    enabled = editing != null,
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor =
                                MaterialTheme.colorScheme.primaryContainer,
                        ),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = editing?.note.orEmpty(),
            onValueChange = onNoteChanged,
            enabled = editing != null,
            label = { Text(stringResource(R.string.mood_note_label)) },
            placeholder = { Text(stringResource(R.string.mood_note_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        val saving = state is MoodCheckInUiState.Saving
        Button(
            onClick = onSave,
            enabled = editing?.mood != null && !saving,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            shape = CircleShape,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor =
                        MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                )
            } else {
                Text(
                    stringResource(R.string.mood_save),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (state is MoodCheckInUiState.Error) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Mood selector button. Faces are simple vector drawings (Canvas), not emoji, so
 * they render identically on every device with no emoji-font dependence.
 */
@Composable
private fun MoodFaceButton(
    score: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label =
        stringResource(
            when (score) {
                1 -> R.string.mood_1_label
                2 -> R.string.mood_2_label
                3 -> R.string.mood_3_label
                4 -> R.string.mood_4_label
                else -> R.string.mood_5_label
            },
        )
    val faceCd = stringResource(R.string.mood_face_cd, label, score)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = faceCd }
                .padding(4.dp),
    ) {
        MoodFace(
            score = score,
            selected = selected,
            darkTheme = LocalAuroraDark.current,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

@Composable
private fun MoodFace(
    score: Int,
    selected: Boolean,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val dawn = AuraGradients.dawn()
    val faceFill = MaterialTheme.colorScheme.surfaceVariant
    // Dark ink on the light dawn gradient, white on night aurora.
    val featureSelected = if (darkTheme) Color.White else Color(0xFF002021)
    val featureIdle = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        // Head.
        if (selected) {
            drawCircle(brush = dawn, radius = radius, center = center)
        } else {
            drawCircle(color = faceFill, radius = radius, center = center)
        }
        val feature = if (selected) featureSelected else featureIdle
        // Eyes.
        val eyeY = center.y - radius * 0.28f
        val eyeOffsetX = radius * 0.34f
        val eyeRadius = radius * 0.11f
        drawCircle(feature, eyeRadius, Offset(center.x - eyeOffsetX, eyeY))
        drawCircle(feature, eyeRadius, Offset(center.x + eyeOffsetX, eyeY))
        // Mouth, shaped by the mood score.
        val mouthCenter = Offset(center.x, center.y + radius * 0.18f)
        val mouthRadius = radius * 0.42f
        val stroke = Stroke(width = radius * 0.13f, cap = StrokeCap.Round)
        when (score) {
            1 -> {
                // Deep frown.
                drawArc(
                    color = feature,
                    startAngle = 200f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = mouthCenter - Offset(mouthRadius, mouthRadius),
                    size = Size(mouthRadius * 2f, mouthRadius * 2f),
                    style = stroke,
                )
            }

            2 -> {
                // Slight frown.
                drawArc(
                    color = feature,
                    startAngle = 215f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = mouthCenter - Offset(mouthRadius, mouthRadius * 0.7f),
                    size = Size(mouthRadius * 2f, mouthRadius * 1.4f),
                    style = stroke,
                )
            }

            3 -> {
                // Flat line.
                drawLine(
                    color = feature,
                    start = Offset(center.x - mouthRadius * 0.55f, mouthCenter.y),
                    end = Offset(center.x + mouthRadius * 0.55f, mouthCenter.y),
                    strokeWidth = radius * 0.13f,
                    cap = StrokeCap.Round,
                )
            }

            4 -> {
                // Smile.
                drawArc(
                    color = feature,
                    startAngle = 25f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = mouthCenter - Offset(mouthRadius, mouthRadius),
                    size = Size(mouthRadius * 2f, mouthRadius * 2f),
                    style = stroke,
                )
            }

            else -> {
                // Big grin with cheeks.
                drawArc(
                    color = feature,
                    startAngle = 15f,
                    sweepAngle = 150f,
                    useCenter = false,
                    topLeft = mouthCenter - Offset(mouthRadius, mouthRadius * 1.1f),
                    size = Size(mouthRadius * 2f, mouthRadius * 2.2f),
                    style = stroke,
                )
                val cheekY = center.y + radius * 0.12f
                drawCircle(
                    feature.copy(alpha = 0.5f),
                    radius * 0.12f,
                    Offset(center.x - radius * 0.58f, cheekY),
                )
                drawCircle(
                    feature.copy(alpha = 0.5f),
                    radius * 0.12f,
                    Offset(center.x + radius * 0.58f, cheekY),
                )
            }
        }
    }
}
