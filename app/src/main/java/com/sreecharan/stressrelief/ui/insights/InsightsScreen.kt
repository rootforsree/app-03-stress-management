package com.sreecharan.stressrelief.ui.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.navigation.PaywallRoute
import com.sreecharan.stressrelief.ui.components.AuraGradients
import com.sreecharan.stressrelief.ui.components.AuraSectionTitle
import com.sreecharan.stressrelief.ui.components.StaggerIn
import com.sreecharan.stressrelief.ui.paywall.ProGate
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import java.util.Locale

/**
 * Insights screen. Premium-gated through Builder 5's ProGate: free users see the
 * upsell card that routes to the paywall; premium users see the mood trend chart
 * (Canvas), the year-in-pixels month grid, and simple correlation cards.
 */
@Composable
fun InsightsScreen(
    onNavigate: (Any) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InsightsContent(
        state = state,
        onNavigate = onNavigate,
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightsContent(
    state: InsightsUiState,
    onNavigate: (Any) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.insights_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(Modifier.fillMaxSize().background(AuraGradients.calmWash()))
            when (state) {
                InsightsUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.insights_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                is InsightsUiState.Error ->
                    InsightsError(message = state.message, onRetry = onRetry)
                is InsightsUiState.Content ->
                    ProGate(
                        onShowPaywall = { onNavigate(PaywallRoute) },
                        lockedTitle = stringResource(R.string.insights_upsell_title),
                        lockedBody = stringResource(R.string.insights_upsell_sub),
                    ) {
                        InsightsDashboard(state = state)
                    }
            }
        }
    }
}

@Composable
private fun InsightsDashboard(state: InsightsUiState.Content, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StaggerIn(0) {
                AuraSectionTitle(stringResource(R.string.insights_trend_title))
                TrendCard(trend = state.trend, delta = state.trendDelta)
            }
        }
        item {
            StaggerIn(1) {
                AuraSectionTitle(
                    stringResource(R.string.insights_pixels_title, state.monthLabel),
                )
                PixelsCard(cells = state.monthCells)
            }
        }
        item {
            StaggerIn(2) {
                AuraSectionTitle(stringResource(R.string.insights_correlations_title))
                CorrelationCards(state = state)
            }
        }
    }
}

/** 30-day mood trend: gradient line chart drawn with Canvas. */
@Composable
private fun TrendCard(
    trend: List<DailyMood?>,
    delta: Double?,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) =
                    when {
                        delta == null -> Icons.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
                        delta > 0.05 -> Icons.Filled.TrendingUp to MaterialTheme.colorScheme.primary
                        delta < -0.05 -> Icons.Filled.TrendingDown to MaterialTheme.colorScheme.tertiary
                        else -> Icons.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    deltaText(delta),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            if (trend.any { it != null }) {
                MoodTrendChart(trend = trend, modifier = Modifier.fillMaxWidth().height(220.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.insights_trend_start),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.insights_trend_end),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    stringResource(R.string.insights_trend_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun deltaText(delta: Double?): String =
    when {
        delta == null -> stringResource(R.string.insights_delta_empty)
        delta > 0.05 -> stringResource(R.string.insights_delta_up, delta)
        delta < -0.05 -> stringResource(R.string.insights_delta_down, delta)
        else -> stringResource(R.string.insights_delta_flat)
    }

@Composable
private fun MoodTrendChart(trend: List<DailyMood?>, modifier: Modifier = Modifier) {
    val lineBrush = AuraGradients.dawn()
    val fillBrush = AuraGradients.verticalFade()
    val dotColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val padX = 8.dp.toPx()
        val padY = 12.dp.toPx()
        val plotW = width - padX * 2
        val plotH = height - padY * 2
        fun x(i: Int): Float =
            if (trend.size <= 1) padX else padX + plotW * i / (trend.size - 1)
        fun y(avg: Double): Float =
            (padY + plotH * (1 - ((avg - 1) / 4.0).toFloat().coerceIn(0f, 1f)))

        // Faint gridlines for moods 1..5.
        (1..5).forEach { mood ->
            val gy = padY + plotH * (1 - (mood - 1) / 4f)
            drawLine(gridColor, Offset(padX, gy), Offset(width - padX, gy), strokeWidth = 1.dp.toPx())
        }

        // Segments break across days with no check-in.
        var segment = mutableListOf<Pair<Float, Float>>()
        val segments = mutableListOf<List<Pair<Float, Float>>>()
        trend.forEachIndexed { i, point ->
            if (point == null) {
                if (segment.isNotEmpty()) segments.add(segment.toList())
                segment = mutableListOf()
            } else {
                segment.add(x(i) to y(point.average))
            }
        }
        if (segment.isNotEmpty()) segments.add(segment)

        segments.forEach { pts ->
            if (pts.size == 1) return@forEach
            val linePath =
                Path().apply {
                    moveTo(pts.first().first, pts.first().second)
                    pts.drop(1).forEach { (px, py) -> lineTo(px, py) }
                }
            val fillPath =
                Path().apply {
                    addPath(linePath)
                    lineTo(pts.last().first, height - padY)
                    lineTo(pts.first().first, height - padY)
                    close()
                }
            drawPath(fillPath, brush = fillBrush)
            drawPath(
                linePath,
                brush = lineBrush,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        trend.forEachIndexed { i, point ->
            if (point != null) {
                drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(x(i), y(point.average)))
                drawCircle(Color.White, radius = 1.8.dp.toPx(), center = Offset(x(i), y(point.average)))
            }
        }
    }
}

/** Year-in-Pixels month grid: one rounded cell per day, colored by average mood. */
@Composable
private fun PixelsCard(cells: List<MonthCell>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val weekdayLabels =
        remember { context.resources.getStringArray(R.array.insights_weekday_labels).toList() }
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val low = MaterialTheme.colorScheme.secondaryContainer
    val high = MaterialTheme.colorScheme.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            cells.chunked(7).forEach { week ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    week.forEach { cell ->
                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            cell.dayOfMonth == null -> Color.Transparent
                                            cell.moodFraction == null -> empty
                                            else -> lerp(low, high, cell.moodFraction.toFloat())
                                        },
                                    ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (cell.dayOfMonth != null) {
                                Text(
                                    "${cell.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = onSurface.copy(alpha = 0.75f),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CorrelationCards(state: InsightsUiState.Content, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActivitiesCard(activities = state.topActivities)
        SleepCard(sleep = state.sleep)
        ConsistencyCard(pct = state.consistencyPct)
    }
}

private fun displayTag(tag: String): String =
    tag.replaceFirstChar { it.uppercaseChar() }

@Composable
private fun ActivitiesCard(activities: List<ActivityMood>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.insights_activities_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            if (activities.isEmpty()) {
                Text(
                    stringResource(R.string.insights_activities_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                activities.forEach { item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 6.dp),
                    ) {
                        Text(
                            displayTag(item.tag),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(110.dp),
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(((item.average - 1) / 4).toFloat().coerceIn(0f, 1f))
                                    .height(10.dp)
                                    .background(AuraGradients.dawn()),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "%.1f".format(Locale.US, item.average),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepCard(sleep: SleepMoodInsight, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AuraGradients.dawn()),
                contentAlignment = Alignment.Center,
            ) {
                val darkTheme = LocalAuroraDark.current
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = if (darkTheme) Color.White else Color(0xFF002021),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.insights_sleep_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                if (sleep.sessions == 0 || (sleep.averageWithSleep == null && sleep.averageWithoutSleep == null)) {
                    Text(
                        stringResource(R.string.insights_sleep_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        stringResource(
                            R.string.insights_sleep_values,
                            sleep.averageWithSleep?.let { "%.1f".format(Locale.US, it) } ?: "-",
                            sleep.averageWithoutSleep?.let { "%.1f".format(Locale.US, it) } ?: "-",
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsistencyCard(pct: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$pct%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(16.dp))
            Text(
                stringResource(R.string.insights_consistency_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun InsightsError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.b4_action_retry))
        }
    }
}
