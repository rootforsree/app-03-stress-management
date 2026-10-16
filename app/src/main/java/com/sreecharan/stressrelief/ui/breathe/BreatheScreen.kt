package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.StaggerIn
import com.sreecharan.stressrelief.ui.theme.AuroraShapeTokens
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark

/**
 * Breathe tab route. The NavHost calls this inside composable<BreatheRoute>;
 * [onNavigateToSession] should navigate to BreathingSessionRoute(goal, patternId).
 */
@Composable
fun BreatheScreen(
    viewModel: BreatheViewModel = hiltViewModel(),
    onNavigateToSession: (goal: String, patternId: String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BreatheEvent.OpenSession -> onNavigateToSession(event.goal, event.patternId)
            }
        }
    }
    BreatheScreenContent(
        state = state,
        onGoalClick = viewModel::onGoalClick,
        onPatternClick = viewModel::onPatternClick,
        onPickerDismiss = viewModel::onPickerDismiss,
    )
}

@Composable
private fun BreatheScreenContent(
    state: BreatheUiState,
    onGoalClick: (String) -> Unit,
    onPatternClick: (String) -> Unit,
    onPickerDismiss: () -> Unit,
) {
    when (state) {
        BreatheUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is BreatheUiState.Content -> {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.breathe_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.breathe_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(state.goals, key = { _, goal -> goal.goal }) { index, goal ->
                        GoalCard(goal = goal, index = index, onClick = { onGoalClick(goal.goal) })
                    }
                }
            }
            val selected = state.goals.firstOrNull { it.goal == state.selectedGoal }
            if (selected != null) {
                PatternPickerSheet(
                    goal = selected,
                    onPatternClick = onPatternClick,
                    onDismiss = onPickerDismiss,
                )
            }
        }
    }
}

private fun goalGradient(theme: GoalTheme): Brush =
    when (theme) {
        GoalTheme.CALM -> Brush.linearGradient(listOf(DawnTeal, DawnCyan))
        GoalTheme.SLEEP -> Brush.linearGradient(listOf(Color(0xFF6366F1), DawnViolet))
        GoalTheme.FOCUS -> Brush.linearGradient(listOf(DawnViolet, DawnPink))
        GoalTheme.SOS -> Brush.linearGradient(listOf(Color(0xFF0D9488), DawnViolet))
    }

@Composable
private fun GoalCard(
    goal: GoalCardUi,
    index: Int,
    onClick: () -> Unit,
) {
    StaggerIn(index = index) {
        Card(
            onClick = onClick,
            shape = AuroraShapeTokens.hero,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(104.dp)
                            .background(goalGradient(goal.theme)),
                    contentAlignment = Alignment.Center,
                ) {
                    val darkTheme = LocalAuroraDark.current
                    Icon(
                        imageVector = goal.icon,
                        contentDescription = null,
                        tint = if (darkTheme) Color.White else Color(0xFF002021),
                        modifier = Modifier.size(44.dp),
                    )
                }
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(goal.titleRes),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(goal.subtitleRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.breathe_recommended),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        goal.patterns.take(2).forEach { pattern ->
                            PatternChip(name = pattern.name)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PatternChip(name: String) {
    Box(
        modifier =
            Modifier
                .background(
                    MaterialTheme.colorScheme.primaryContainer,
                    AuroraShapeTokens.chip,
                ).padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun timingSummary(pattern: PatternUi): String {
    val timing =
        if (pattern.holdSec > 0) {
            stringResource(R.string.breathe_pattern_timing_full, pattern.inhaleSec, pattern.holdSec, pattern.exhaleSec)
        } else {
            stringResource(R.string.breathe_pattern_timing_no_hold, pattern.inhaleSec, pattern.exhaleSec)
        }
    return timing + " · " + stringResource(R.string.breathe_pattern_rounds, pattern.rounds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PatternPickerSheet(
    goal: GoalCardUi,
    onPatternClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.breathe_picker_title, stringResource(goal.titleRes)),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(goal.patterns, key = { it.id }) { pattern ->
                PatternRow(
                    pattern = pattern,
                    onClick = { onPatternClick(pattern.id) },
                )
            }
        }
    }
}

@Composable
private fun PatternRow(
    pattern: PatternUi,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pattern.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timingSummary(pattern),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = pattern.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
