package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuroraCard
import com.sreecharan.stressrelief.ui.components.EmptyState
import com.sreecharan.stressrelief.ui.components.StaggerIn
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.AuroraShapeTokens
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark

/**
 * Programs route. The NavHost calls this inside composable<ProgramsRoute>.
 * [onStartProgramSession] should stash (programId, day) on the current
 * entry's savedStateHandle, then navigate to BreathingSessionRoute(goal,
 * patternId); the session screen reads them back and completes the day.
 */
@Composable
fun ProgramsScreen(
    viewModel: ProgramsViewModel = hiltViewModel(),
    onStartProgramSession: (goal: String, patternId: String, programId: String, day: Int) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProgramsEvent.StartProgramSession -> {
                    onStartProgramSession(
                        event.goal,
                        event.patternId,
                        event.programId,
                        event.day,
                    )
                }
            }
        }
    }
    ProgramsScreenContent(
        state = state,
        onDayClick = viewModel::onDayClick,
        onRetry = viewModel::onRetry,
    )
}

@Composable
private fun ProgramsScreenContent(
    state: ProgramsUiState,
    onDayClick: (programId: String, day: Int, patternId: String, status: DayStatus) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        ProgramsUiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        ProgramsUiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize().statusBarsPadding(),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.SelfImprovement,
                    title = stringResource(R.string.programs_error_title),
                    message = stringResource(R.string.programs_error_message),
                    actionLabel = stringResource(R.string.breathe_retry),
                    onActionClick = onRetry,
                )
            }
        }

        is ProgramsUiState.Content -> {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.programs_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.programs_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(state.programs, key = { _, program -> program.id }) { index, program ->
                        ProgramCard(
                            program = program,
                            index = index,
                            onDayClick = onDayClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgramCard(
    program: ProgramUi,
    index: Int,
    onDayClick: (programId: String, day: Int, patternId: String, status: DayStatus) -> Unit,
) {
    StaggerIn(index = index) {
        AuroraCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = program.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = program.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val done = program.days.count { it.status == DayStatus.DONE }
            Row(verticalAlignment = Alignment.CenterVertically) {
                GradientProgressBar(
                    progress = done / 7f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.programs_days_done, done),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            program.days.chunked(2).forEach { rowDays ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowDays.forEach { day ->
                        DayCard(
                            programId = program.id,
                            day = day,
                            onDayClick = onDayClick,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowDays.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Progress bar filled with the hero gradient, never a flat color (doc: motion section). */
@Composable
private fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalAuroraDark.current
    Box(
        modifier =
            modifier
                .height(10.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(Brush.horizontalGradient(AuroraBrushes.heroStops(darkTheme))),
        )
    }
}

@Composable
private fun DayCard(
    programId: String,
    day: ProgramDayUi,
    onDayClick: (programId: String, day: Int, patternId: String, status: DayStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        when (day.status) {
            DayStatus.DONE -> MaterialTheme.colorScheme.tertiaryContainer
            DayStatus.CURRENT -> MaterialTheme.colorScheme.primaryContainer
            DayStatus.LOCKED -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when (day.status) {
            DayStatus.DONE -> MaterialTheme.colorScheme.onTertiaryContainer
            DayStatus.CURRENT -> MaterialTheme.colorScheme.onPrimaryContainer
            DayStatus.LOCKED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        }
    val colors =
        CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        )
    val statusIcon =
        when (day.status) {
            DayStatus.DONE -> Icons.Rounded.Check
            DayStatus.CURRENT -> Icons.Rounded.PlayArrow
            DayStatus.LOCKED -> Icons.Rounded.Lock
        }
    val statusLabelRes =
        when (day.status) {
            DayStatus.DONE -> R.string.program_day_done
            DayStatus.CURRENT -> R.string.program_day_start
            DayStatus.LOCKED -> R.string.program_day_locked
        }
    val clickable = day.status == DayStatus.CURRENT
    val cardContent: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = stringResource(R.string.program_day, day.day),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = day.title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.program_day_minutes, day.minutes),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(statusLabelRes),
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Start,
                )
            }
        }
    }
    if (clickable) {
        Card(
            onClick = { onDayClick(programId, day.day, day.patternId, day.status) },
            shape = AuroraShapeTokens.card,
            colors = colors,
            modifier = modifier,
            content = { cardContent() },
        )
    } else {
        Card(
            shape = AuroraShapeTokens.card,
            colors = colors,
            modifier = modifier,
            content = { cardContent() },
        )
    }
}
