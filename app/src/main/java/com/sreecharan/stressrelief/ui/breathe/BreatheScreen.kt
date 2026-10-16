package com.sreecharan.stressrelief.ui.breathe

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.model.CustomPattern
import com.sreecharan.stressrelief.ui.components.StaggerIn
import com.sreecharan.stressrelief.ui.theme.AuroraShapeTokens
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import kotlinx.collections.immutable.ImmutableList
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Breathe tab route. The NavHost calls this inside composable<BreatheRoute>;
 * [onNavigateToSession] should navigate to BreathingSessionRoute(goal, patternId),
 * [onCreateCustomPattern] to PatternBuilderRoute (wired by the coordinator in
 * AppNavHost; defaults to a no-op so existing call sites keep compiling).
 */
@Composable
fun BreatheScreen(
    viewModel: BreatheViewModel = hiltViewModel(),
    onNavigateToSession: (goal: String, patternId: String) -> Unit,
    onCreateCustomPattern: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BreatheEvent.OpenSession -> onNavigateToSession(event.goal, event.patternId)
                BreatheEvent.OpenPatternBuilder -> onCreateCustomPattern()
            }
        }
    }
    BreatheScreenContent(
        state = state,
        onGoalClick = viewModel::onGoalClick,
        onPatternClick = viewModel::onPatternClick,
        onCreateCustomClick = viewModel::onCreateCustomClick,
        onPickerDismiss = viewModel::onPickerDismiss,
        onGoalChange = viewModel::onGoalChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BreatheScreenContent(
    state: BreatheUiState,
    onGoalClick: (String) -> Unit,
    onPatternClick: (String) -> Unit,
    onCreateCustomClick: () -> Unit,
    onPickerDismiss: () -> Unit,
    onGoalChange: (Int) -> Unit,
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
            // Fixed top bar like Home/Insights/Journal, so the header pattern
            // and the 8dp top content padding are uniform across all tabs.
            // The diary stays fixed under the bar (it was already fixed); only
            // the goal grid scrolls. Note: no statusBarsPadding() here —
            // MainTabScaffold's Scaffold already applies the system-bar insets,
            // and this inner Scaffold opts out of adding them a second time.
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(R.string.breathe_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                },
            ) { padding ->
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Spacer(Modifier.height(8.dp))
                    DiarySection(
                        days = state.diary,
                        goalMin = state.goalMin,
                        onGoalChange = onGoalChange,
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
            }
            val selected = state.goals.firstOrNull { it.goal == state.selectedGoal }
            if (selected != null) {
                PatternPickerSheet(
                    goal = selected,
                    customPatterns = state.customPatterns,
                    onPatternClick = onPatternClick,
                    onCreateCustomClick = onCreateCustomClick,
                    onDismiss = onPickerDismiss,
                )
            }
        }
    }
}

private const val DIARY_GOAL_STEP_MIN = 5
private const val MIN_DIARY_GOAL_MIN = 5
private const val MAX_DIARY_GOAL_MIN = 120
private const val BAR_WIDTH_FRAC = 0.52f
private const val DIM_BAR_ALPHA = 0.55f
private const val GOAL_LINE_ALPHA = 0.7f

/**
 * 7-day breathing diary: minutes-per-day bars against the daily goal line,
 * plus a goal stepper. Canvas-drawn on the dawn scale; today highlighted.
 */
@Composable
private fun DiarySection(
    days: ImmutableList<DiaryDay>,
    goalMin: Int,
    onGoalChange: (Int) -> Unit,
) {
    val todayMinutes = days.firstOrNull { it.isToday }?.minutes?.toInt() ?: 0
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.diary_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.diary_subtitle, todayMinutes, goalMin),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        DiaryChart(
            days = days,
            goalMin = goalMin,
            modifier = Modifier.fillMaxWidth().height(132.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    text =
                        LocalDate.ofEpochDay(day.epochDay)
                            .dayOfWeek
                            .getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (day.isToday) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        StepperRow(
            label = stringResource(R.string.diary_goal),
            valueText = stringResource(R.string.diary_minutes, goalMin),
            onDecrement = { onGoalChange(goalMin - DIARY_GOAL_STEP_MIN) },
            onIncrement = { onGoalChange(goalMin + DIARY_GOAL_STEP_MIN) },
            decrementEnabled = goalMin > MIN_DIARY_GOAL_MIN,
            incrementEnabled = goalMin < MAX_DIARY_GOAL_MIN,
        )
    }
}

@Composable
private fun DiaryChart(
    days: ImmutableList<DiaryDay>,
    goalMin: Int,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalAuroraDark.current
    val barColor = if (darkTheme) NightTeal else DawnTeal
    val todayColor = if (darkTheme) NightCyan else DawnCyan
    val goalLineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val chartDesc = stringResource(R.string.diary_chart_desc)
    Canvas(
        modifier = modifier.semantics { contentDescription = chartDesc },
    ) {
        val maxMinutes =
            maxOf(
                goalMin.toFloat(),
                days.maxOfOrNull { it.minutes } ?: 0f,
                1f,
            )
        val slotWidth = size.width / days.size.coerceAtLeast(1)
        val barWidth = slotWidth * BAR_WIDTH_FRAC
        days.forEachIndexed { index, day ->
            val frac = (day.minutes / maxMinutes).coerceIn(0f, 1f)
            val barHeight = frac * size.height
            val minHeight = if (day.minutes > 0f) 4.dp.toPx() else 0f
            drawRoundRect(
                color =
                    (if (day.isToday) todayColor else barColor)
                        .copy(alpha = if (day.isToday) 1f else DIM_BAR_ALPHA),
                topLeft = Offset(index * slotWidth + (slotWidth - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight.coerceAtLeast(minHeight)),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
        val goalY = size.height - (goalMin / maxMinutes).coerceIn(0f, 1f) * size.height
        drawLine(
            color = goalLineColor.copy(alpha = GOAL_LINE_ALPHA),
            start = Offset(0f, goalY),
            end = Offset(size.width, goalY),
            strokeWidth = 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())),
        )
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
    customPatterns: ImmutableList<CustomPattern>,
    onPatternClick: (String) -> Unit,
    onCreateCustomClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val customDesc = stringResource(R.string.breathe_custom_pattern_desc)
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
            items(customPatterns, key = { it.id }) { custom ->
                PatternRow(
                    pattern =
                        PatternUi(
                            id = custom.id,
                            name = custom.name,
                            inhaleSec = custom.inhaleSec,
                            holdSec = custom.holdSec,
                            exhaleSec = custom.exhaleSec,
                            holdAfterSec = custom.holdAfterSec,
                            rounds = custom.rounds,
                            description = customDesc,
                        ),
                    onClick = { onPatternClick(custom.id) },
                )
            }
            item(key = "create_custom") {
                CreateCustomCard(onClick = onCreateCustomClick)
            }
        }
    }
}

@Composable
private fun CreateCustomCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.breathe_create_custom),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.breathe_create_custom_sub),
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
