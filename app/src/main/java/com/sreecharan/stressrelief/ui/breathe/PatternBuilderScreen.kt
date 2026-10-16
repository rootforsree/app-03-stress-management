package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuroraButton
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark

private const val MAX_NAME_CHARS = 40
private val BUILDER_GOALS = listOf("CALM", "SLEEP", "FOCUS", "SOS")

/**
 * Custom breathing pattern builder. Signature is frozen by the builder
 * contract; the NavHost wires onBack to popBackStack and onStartSession to
 * navigate(BreathingSessionRoute(goal, patternId)).
 */
@Composable
fun PatternBuilderScreen(
    onBack: () -> Unit,
    onStartSession: (goal: String, patternId: String) -> Unit,
    viewModel: PatternBuilderViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fallbackName = stringResource(R.string.builder_default_name)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PatternBuilderEvent.Saved -> {
                    if (event.startSession) {
                        onStartSession(event.goal, event.patternId)
                    } else {
                        onBack()
                    }
                }
            }
        }
    }
    PatternBuilderContent(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onGoalChange = viewModel::onGoalChange,
        onInhaleChange = viewModel::onInhaleChange,
        onHoldChange = viewModel::onHoldChange,
        onExhaleChange = viewModel::onExhaleChange,
        onHoldAfterChange = viewModel::onHoldAfterChange,
        onRoundsChange = viewModel::onRoundsChange,
        onSave = { viewModel.onSave(fallbackName, startSession = false) },
        onSaveAndStart = { viewModel.onSave(fallbackName, startSession = true) },
    )
}

@Composable
private fun PatternBuilderContent(
    state: PatternBuilderUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onGoalChange: (String) -> Unit,
    onInhaleChange: (Int) -> Unit,
    onHoldChange: (Int) -> Unit,
    onExhaleChange: (Int) -> Unit,
    onHoldAfterChange: (Int) -> Unit,
    onRoundsChange: (Int) -> Unit,
    onSave: () -> Unit,
    onSaveAndStart: () -> Unit,
) {
    val darkTheme = LocalAuroraDark.current
    val editing = state as? PatternBuilderUiState.Editing ?: return
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(AuroraBrushes.calmWash(darkTheme))
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.builder_close),
                )
            }
            Text(
                text = stringResource(R.string.builder_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = editing.name,
                onValueChange = { if (it.length <= MAX_NAME_CHARS) onNameChange(it) },
                label = { Text(stringResource(R.string.builder_name_label)) },
                placeholder = { Text(stringResource(R.string.builder_name_placeholder)) },
                singleLine = true,
                supportingText = {
                    Text(
                        stringResource(
                            R.string.builder_name_counter,
                            editing.name.length,
                            MAX_NAME_CHARS,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.builder_goal_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BUILDER_GOALS.forEach { goal ->
                    FilterChip(
                        selected = editing.goal == goal,
                        onClick = { onGoalChange(goal) },
                        label = { Text(goalLabel(goal)) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            StepperRow(
                label = stringResource(R.string.builder_inhale),
                valueText = stringResource(R.string.builder_seconds, editing.inhaleSec),
                onDecrement = { onInhaleChange(editing.inhaleSec - 1) },
                onIncrement = { onInhaleChange(editing.inhaleSec + 1) },
                decrementEnabled = editing.inhaleSec > 1,
                incrementEnabled = editing.inhaleSec < 30,
            )
            StepperRow(
                label = stringResource(R.string.builder_hold),
                valueText = stringResource(R.string.builder_seconds, editing.holdSec),
                onDecrement = { onHoldChange(editing.holdSec - 1) },
                onIncrement = { onHoldChange(editing.holdSec + 1) },
                decrementEnabled = editing.holdSec > 0,
                incrementEnabled = editing.holdSec < 30,
            )
            StepperRow(
                label = stringResource(R.string.builder_exhale),
                valueText = stringResource(R.string.builder_seconds, editing.exhaleSec),
                onDecrement = { onExhaleChange(editing.exhaleSec - 1) },
                onIncrement = { onExhaleChange(editing.exhaleSec + 1) },
                decrementEnabled = editing.exhaleSec > 1,
                incrementEnabled = editing.exhaleSec < 30,
            )
            StepperRow(
                label = stringResource(R.string.builder_hold_after),
                valueText = stringResource(R.string.builder_seconds, editing.holdAfterSec),
                onDecrement = { onHoldAfterChange(editing.holdAfterSec - 1) },
                onIncrement = { onHoldAfterChange(editing.holdAfterSec + 1) },
                decrementEnabled = editing.holdAfterSec > 0,
                incrementEnabled = editing.holdAfterSec < 30,
            )
            StepperRow(
                label = stringResource(R.string.builder_rounds),
                valueText = editing.rounds.toString(),
                onDecrement = { onRoundsChange(editing.rounds - 1) },
                onIncrement = { onRoundsChange(editing.rounds + 1) },
                decrementEnabled = editing.rounds > 1,
                incrementEnabled = editing.rounds < 30,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = patternSummary(editing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(16.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSave, enabled = !editing.isSaving) {
                Text(stringResource(R.string.builder_save))
            }
            Spacer(Modifier.width(8.dp))
            AuroraButton(
                text = stringResource(R.string.builder_save_and_start),
                onClick = onSaveAndStart,
                enabled = !editing.isSaving,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun patternSummary(editing: PatternBuilderUiState.Editing): String {
    val parts = mutableListOf(stringResource(R.string.builder_phase_in, editing.inhaleSec))
    if (editing.holdSec > 0) {
        parts.add(stringResource(R.string.builder_phase_hold, editing.holdSec))
    }
    parts.add(stringResource(R.string.builder_phase_exhale, editing.exhaleSec))
    if (editing.holdAfterSec > 0) {
        parts.add(stringResource(R.string.builder_phase_rest, editing.holdAfterSec))
    }
    return parts.joinToString(" · ") + " · " + stringResource(R.string.breathe_pattern_rounds, editing.rounds)
}

/**
 * Goal title for the session screen. Kept as a plain function (no resources)
 * because the session ViewModel needs it off the UI thread.
 */
internal fun goalTitle(goal: String): String =
    when (goal.uppercase()) {
        "CALM" -> "Calm"
        "SLEEP" -> "Sleep"
        "FOCUS" -> "Focus"
        "SOS" -> "SOS"
        else -> goal
    }

@Composable
private fun goalLabel(goal: String): String =
    stringResource(
        when (goal) {
            "CALM" -> R.string.breathe_goal_calm
            "SLEEP" -> R.string.breathe_goal_sleep
            "FOCUS" -> R.string.breathe_goal_focus
            "SOS" -> R.string.breathe_goal_sos
            else -> R.string.breathe_goal_calm
        },
    )
