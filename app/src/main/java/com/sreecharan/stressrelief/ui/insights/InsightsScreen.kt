package com.sreecharan.stressrelief.ui.insights

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.model.InsightCard
import com.sreecharan.stressrelief.data.model.InsightKind
import com.sreecharan.stressrelief.data.model.InsightsData
import com.sreecharan.stressrelief.data.model.RecoveryBand
import com.sreecharan.stressrelief.data.model.SleepNightScore
import com.sreecharan.stressrelief.ui.components.AuraGradients
import com.sreecharan.stressrelief.ui.components.AuraSectionTitle
import com.sreecharan.stressrelief.ui.components.StaggerIn
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import kotlin.math.abs

/**
 * Insights screen (v2). Free for everyone: no premium gate, no upsell copy.
 * Renders [InsightsData] from [InsightsViewModel]: recovery hero, sleep
 * section, heart section, and the ordered insight cards. Charts are hand-drawn
 * Compose Canvas on the dawn scale; no chart library.
 */
@Composable
fun InsightsScreen(
    onNavigate: (Any) -> Unit,
    viewModel: InsightsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val healthPermissionLauncher =
        rememberLauncherForActivityResult(viewModel.healthConnect.permissionContract()) {
            viewModel.onHealthPermissionResult()
        }
    InsightsContent(
        state = state,
        onConnectHealth = {
            healthPermissionLauncher.launch(viewModel.healthConnect.requiredPermissions())
        },
        onRetry = viewModel::retry,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InsightsContent(
    state: InsightsUiState,
    onConnectHealth: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        // Nested inside MainTabScaffold's Scaffold: it already applies the
        // system-bar insets, so this one must not add them a second time.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                InsightsUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.insights_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                InsightsUiState.Error -> {
                    InsightsError(onRetry = onRetry)
                }

                is InsightsUiState.Content -> {
                    InsightsDashboard(
                        insights = state.insights,
                        onConnectHealth = onConnectHealth,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightsDashboard(
    insights: InsightsData,
    onConnectHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!insights.healthConnected) {
            item {
                StaggerIn(0) {
                    ConnectHealthBanner(onConnect = onConnectHealth)
                }
            }
        }
        item {
            StaggerIn(1) {
                AuraSectionTitle(stringResource(R.string.insights_recovery_title))
                RecoveryHeroCard(insights = insights)
            }
        }
        item {
            StaggerIn(2) {
                AuraSectionTitle(stringResource(R.string.insights_sleep_title))
                SleepSectionCard(insights = insights)
            }
        }
        item {
            StaggerIn(3) {
                AuraSectionTitle(stringResource(R.string.insights_heart_title))
                HeartSectionCard(insights = insights)
            }
        }
        if (insights.insightCards.isNotEmpty()) {
            item {
                StaggerIn(4) {
                    AuraSectionTitle(stringResource(R.string.insights_cards_title))
                    InsightCardsList(cards = insights.insightCards)
                }
            }
        }
    }
}

/**
 * Slim Connect Health banner (~72dp). Compact sibling of the home banner:
 * it only knows the [InsightsData.healthConnected] flag and fires the
 * Health Connect permission request.
 */
@Composable
private fun ConnectHealthBanner(
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AuraGradients.dawn()),
                contentAlignment = Alignment.Center,
            ) {
                val darkTheme = LocalAuroraDark.current
                Icon(
                    Icons.Filled.MonitorHeart,
                    contentDescription = null,
                    tint = if (darkTheme) Color.White else Color(0xFF002021),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.insights_connect_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.insights_connect_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                )
            }
            TextButton(onClick = onConnect) {
                Text(stringResource(R.string.insights_connect_cta))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Recovery hero
// ---------------------------------------------------------------------------

/** Band colors on the dawn spectrum: teal (charged) to pink (depleted). */
@Composable
private fun recoveryBandColor(band: RecoveryBand): Color {
    val darkTheme = LocalAuroraDark.current
    val stops = AuroraBrushes.heroStops(darkTheme)
    return when (band) {
        RecoveryBand.CHARGED -> stops[0]
        RecoveryBand.COASTING -> stops[2]
        RecoveryBand.DEPLETED -> stops[3]
        RecoveryBand.CALIBRATING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun RecoveryBand.labelRes(): Int =
    when (this) {
        RecoveryBand.CHARGED -> R.string.insights_band_charged
        RecoveryBand.COASTING -> R.string.insights_band_coasting
        RecoveryBand.DEPLETED -> R.string.insights_band_depleted
        RecoveryBand.CALIBRATING -> R.string.insights_band_calibrating
    }

@Composable
private fun RecoveryHeroCard(
    insights: InsightsData,
    modifier: Modifier = Modifier,
) {
    val calibrating = insights.calibrating || insights.recoveryBand == RecoveryBand.CALIBRATING
    val score = insights.recoveryScore
    val bandColor = recoveryBandColor(insights.recoveryBand)
    val ringBrush = AuraGradients.sweep()
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val cd =
                if (calibrating || score == null) {
                    context.getString(R.string.insights_band_calibrating)
                } else {
                    context.getString(R.string.insights_recovery_score_cd, score)
                }
            Box(
                Modifier
                    .size(132.dp)
                    .semantics { contentDescription = cd },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 14.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val arcTopLeft = Offset(stroke / 2, stroke / 2)
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    val fraction = if (calibrating || score == null) 0f else score / 100f
                    if (fraction > 0f) {
                        drawArc(
                            brush = ringBrush,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (calibrating || score == null) {
                        Text(
                            stringResource(R.string.insights_band_calibrating),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            "$score",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.home_score_out_of_100),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = bandColor.copy(alpha = 0.18f),
                ) {
                    Text(
                        stringResource(insights.recoveryBand.labelRes()),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = bandColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (calibrating) {
                        stringResource(R.string.insights_recovery_calibrating_body)
                    } else {
                        stringResource(R.string.insights_recovery_body)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sleep section
// ---------------------------------------------------------------------------

@Composable
private fun SleepSectionCard(
    insights: InsightsData,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            val nights = insights.sleepNights
            if (nights.any { it.score != null }) {
                SleepScoreBars(
                    nights = nights,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                Spacer(Modifier.height(16.dp))
            } else {
                Text(
                    stringResource(R.string.insights_sleep_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
            InsightRow(
                label = stringResource(R.string.insights_sleep_debt_label),
                value =
                    insights.sleepDebtHours?.let {
                        stringResource(R.string.insights_sleep_debt_hours, it)
                    } ?: stringResource(R.string.insights_sleep_debt_empty),
                sub =
                    stringResource(
                        R.string.insights_sleep_need_sub,
                        insights.sleepNeedHours,
                    ),
            )
            val sri = insights.sri
            if (sri != null) {
                Spacer(Modifier.height(12.dp))
                InsightRow(
                    label = stringResource(R.string.insights_sri_label),
                    value = "$sri",
                )
            }
            val jetlag = insights.socialJetlagMin
            if (jetlag != null) {
                Spacer(Modifier.height(12.dp))
                InsightRow(
                    label = stringResource(R.string.insights_jetlag_label),
                    value = stringResource(R.string.insights_jetlag_min, jetlag),
                )
            }
        }
    }
}

@Composable
private fun InsightRow(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Last-14-night sleep score bars on the dawn scale: teal (high) to pink
 * (low). Nights with no score draw a faint stub; missing data is never
 * scored as zero.
 */
@Composable
private fun SleepScoreBars(
    nights: List<SleepNightScore>,
    modifier: Modifier = Modifier,
) {
    val stops = AuroraBrushes.heroStops(LocalAuroraDark.current)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val context = LocalContext.current
    val cd = remember { context.getString(R.string.insights_sleep_bars_cd) }
    Canvas(
        modifier
            .semantics { contentDescription = cd },
    ) {
        val width = size.width
        val height = size.height
        val count = nights.size.coerceAtLeast(1)
        val slot = width / count
        val barW = (slot * 0.55f).coerceAtLeast(4.dp.toPx())
        nights.forEachIndexed { i, night ->
            val cx = slot * i + slot / 2
            val score = night.score
            if (score == null) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(cx - barW / 2, height - 6.dp.toPx()),
                    size = Size(barW, 6.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                )
            } else {
                val fraction = (score / 100f).coerceIn(0f, 1f)
                val barH = (height * fraction).coerceAtLeast(6.dp.toPx())
                drawRoundRect(
                    color = lerp(stops[3], stops[0], fraction),
                    topLeft = Offset(cx - barW / 2, height - barH),
                    size = Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                )
            }
        }
    }
}

@Composable
private fun HeartSectionCard(
    insights: InsightsData,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(Modifier.padding(20.dp)) {
            val today = insights.restingHrToday
            val baseline = insights.restingHrBaseline
            if (today == null || baseline == null) {
                Text(
                    stringResource(R.string.insights_rhr_no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$today",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Text(
                        stringResource(R.string.insights_bpm_unit),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.insights_rhr_baseline, baseline),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                val z = insights.restingHrZ
                if (z != null && abs(z) >= 1.5f) {
                    ZBadge(z = z)
                }
            }
            val slope = insights.restingHrTrendBpmPerDay
            if (slope != null) {
                Spacer(Modifier.height(12.dp))
                HeartTrendLine(
                    baseline = baseline,
                    slopeBpmPerDay = slope,
                    modifier = Modifier.fillMaxWidth().height(110.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    trendLabel(slope),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val hrv = insights.hrvRmssdToday
            if (hrv != null) {
                Spacer(Modifier.height(12.dp))
                InsightRow(
                    label = stringResource(R.string.insights_hrv_label),
                    value = stringResource(R.string.insights_hrv_ms, hrv),
                )
            }
        }
    }
}

@Composable
private fun trendLabel(slopeBpmPerDay: Float): String =
    when {
        slopeBpmPerDay > 0.05f ->
            stringResource(R.string.insights_trend_climbing, slopeBpmPerDay)
        slopeBpmPerDay < -0.05f ->
            stringResource(R.string.insights_trend_falling, abs(slopeBpmPerDay))
        else -> stringResource(R.string.insights_trend_flat)
    }

@Composable
private fun ZBadge(
    z: Float,
    modifier: Modifier = Modifier,
) {
    val (textRes, container, onContainer) =
        when {
            z >= 3f ->
                Triple(
                    R.string.insights_rhr_badge_extreme,
                    MaterialTheme.colorScheme.tertiaryContainer,
                    MaterialTheme.colorScheme.onTertiaryContainer,
                )
            z >= 1.5f ->
                Triple(
                    R.string.insights_rhr_badge_elevated,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            else ->
                Triple(
                    R.string.insights_rhr_badge_low,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
        }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container,
    ) {
        Text(
            stringResource(textRes, abs(z)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = onContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * 14-day resting-HR trend drawn as the OLS fit line: baseline +
 * slope * (day - 6.5). The model only ships the slope, not the nightly
 * series, so this renders the fit itself with the baseline dashed.
 */
@Composable
private fun HeartTrendLine(
    baseline: Float,
    slopeBpmPerDay: Float,
    modifier: Modifier = Modifier,
) {
    val lineBrush = AuraGradients.dawn()
    val baselineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val dotColor = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val padX = 8.dp.toPx()
        val padY = 10.dp.toPx()
        val values = (0..13).map { i -> baseline + slopeBpmPerDay * (i - 6.5f) }
        val min = minOf(values.min(), baseline) - 1f
        val max = maxOf(values.max(), baseline) + 1f
        val span = (max - min).coerceAtLeast(0.5f)

        fun x(i: Int): Float = padX + (width - padX * 2) * i / 13f

        fun y(v: Float): Float = padY + (height - padY * 2) * (1 - (v - min) / span)

        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        drawLine(
            color = baselineColor,
            start = Offset(padX, y(baseline)),
            end = Offset(width - padX, y(baseline)),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = dash,
        )
        var prev: Offset? = null
        values.forEachIndexed { i, v ->
            val point = Offset(x(i), y(v))
            prev?.let {
                drawLine(
                    brush = lineBrush,
                    start = it,
                    end = point,
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            prev = point
        }
        drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(x(13), y(values[13])))
    }
}

// ---------------------------------------------------------------------------
// Insight cards
// ---------------------------------------------------------------------------

private fun InsightKind.icon() =
    when (this) {
        InsightKind.SLEEP -> Icons.Filled.Bedtime
        InsightKind.RECOVERY -> Icons.Filled.Favorite
        InsightKind.TREND -> Icons.AutoMirrored.Filled.TrendingUp
        InsightKind.CORRELATION -> Icons.Filled.Insights
        InsightKind.HABIT -> Icons.Filled.Loop
    }

@Composable
private fun InsightCardsList(
    cards: List<InsightCard>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.forEach { card ->
            InsightCardRow(card = card)
        }
    }
}

@Composable
private fun InsightCardRow(
    card: InsightCard,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AuraGradients.dawn()),
                contentAlignment = Alignment.Center,
            ) {
                val darkTheme = LocalAuroraDark.current
                Icon(
                    card.kind.icon(),
                    contentDescription = null,
                    tint = if (darkTheme) Color.White else Color(0xFF002021),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    card.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Error
// ---------------------------------------------------------------------------

@Composable
private fun InsightsError(
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
        Text(
            stringResource(R.string.insights_error),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.b4_action_retry))
        }
    }
}
