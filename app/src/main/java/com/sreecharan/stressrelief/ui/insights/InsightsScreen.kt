package com.sreecharan.stressrelief.ui.insights

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Air
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.health.connect.client.HealthConnectClient
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
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val healthPermissionLauncher =
        rememberLauncherForActivityResult(viewModel.healthConnect.permissionContract()) {
            viewModel.onHealthPermissionResult()
        }
    InsightsContent(
        state = state,
        onConnectHealth = {
            // Guarded like the Home banner: never fire the permission contract
            // when the provider is missing (it would throw out of the click
            // handler). Offer the Play Store listing instead when an update
            // or install can fix it.
            scope.launch {
                val hc = viewModel.healthConnect
                when {
                    hc.isAvailable() ->
                        healthPermissionLauncher.launch(
                            hc.requiredPermissions() + hc.writePermissions(),
                        )
                    hc.sdkStatus() ==
                        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        val packageName = "com.google.android.apps.healthdata"
                        val marketIntent =
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=$packageName"),
                            )
                        val webIntent =
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                            )
                        runCatching { context.startActivity(marketIntent) }
                            .onFailure { runCatching { context.startActivity(webIntent) } }
                    }
                    else -> Unit
                }
            }
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
        // Stable keys: the connect banner is inserted/removed as Health
        // Connect state resolves, and without keys every item below it
        // shifts index, resetting StaggerIn's remembered state and replaying
        // the entrance for the whole list.
        if (!insights.healthConnected) {
            item(key = "health_banner") {
                StaggerIn(0) {
                    ConnectHealthBanner(onConnect = onConnectHealth)
                }
            }
        }
        if (insights.recommendations.isNotEmpty()) {
            item(key = "recommendations") {
                StaggerIn(1) {
                    AuraSectionTitle(stringResource(R.string.insights_recommendations_title))
                    RecommendationsList(recommendations = insights.recommendations)
                }
            }
        }
        item(key = "recovery") {
            StaggerIn(2) {
                AuraSectionTitle(stringResource(R.string.insights_recovery_title))
                RecoveryHeroCard(insights = insights)
            }
        }
        item(key = "sleep") {
            StaggerIn(3) {
                AuraSectionTitle(stringResource(R.string.insights_sleep_title))
                SleepSectionCard(insights = insights)
            }
        }
        item(key = "steps") {
            StaggerIn(4) {
                AuraSectionTitle(stringResource(R.string.insights_steps_title))
                StepsSectionCard(insights = insights)
            }
        }
        item(key = "heart") {
            StaggerIn(5) {
                AuraSectionTitle(stringResource(R.string.insights_heart_title))
                HeartSectionCard(insights = insights)
            }
        }
        if (insights.insightCards.isNotEmpty()) {
            item(key = "cards") {
                StaggerIn(6) {
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
    val score = insights.recoveryScore
    val bandColor = recoveryBandColor(insights.recoveryBand)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        if (score == null) {
            // Honest empty state: never a fake number.
            Column(Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.insights_recovery_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.insights_recovery_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            RecoveryScoreCompact(
                insights = insights,
                score = score,
                bandColor = bandColor,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

/**
 * Compact recovery row: small ring, band chip, and the two numbers that
 * drive the score, in plain words. No giant static number.
 */
@Composable
private fun RecoveryScoreCompact(
    insights: InsightsData,
    score: Int,
    bandColor: Color,
    modifier: Modifier = Modifier,
) {
    val ringBrush = AuraGradients.sweep()
    val trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val context = LocalContext.current
    val cd = remember(score) { context.getString(R.string.insights_recovery_score_cd, score) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(76.dp)
                .semantics { contentDescription = cd },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
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
                drawArc(
                    brush = ringBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * (score / 100f),
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Text(
                "$score",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(14.dp))
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
            RecoveryDriverLines(insights = insights)
        }
    }
}

/** The visible numbers behind the score, in plain words. */
@Composable
private fun RecoveryDriverLines(insights: InsightsData) {
    val today = insights.restingHrToday
    val baseline = insights.restingHrBaseline
    if (today != null && baseline != null) {
        val delta = today - baseline.roundToInt()
        val verdict =
            when {
                delta >= 5 -> stringResource(R.string.insights_driver_rhr_high, delta)
                delta <= -5 -> stringResource(R.string.insights_driver_rhr_low, -delta)
                else -> stringResource(R.string.insights_driver_rhr_steady)
            }
        DriverLine(
            label = stringResource(R.string.insights_driver_rhr_label),
            value = "$today ${stringResource(R.string.insights_bpm_unit)}",
            verdict = verdict,
        )
        Spacer(Modifier.height(4.dp))
    }
    val debt = insights.sleepDebtHours
    if (debt != null) {
        DriverLine(
            label = stringResource(R.string.insights_driver_debt_label),
            value = stringResource(R.string.insights_sleep_debt_hours, debt),
            verdict =
                if (debt >= 2f) {
                    stringResource(R.string.insights_driver_debt_high)
                } else {
                    stringResource(R.string.insights_driver_debt_ok)
                },
        )
    }
}

@Composable
private fun DriverLine(
    label: String,
    value: String,
    verdict: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(
        verdict,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
            val nights = insights.sleepNights.takeLast(SLEEP_CHART_NIGHTS)
            if (nights.any { it.totalSleepMin != null }) {
                var selected by remember { mutableStateOf<Int?>(null) }
                SleepInteractiveBars(
                    nights = nights,
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
                val detail = selected?.let { nights.getOrNull(it) }
                if (detail?.totalSleepMin != null) {
                    Spacer(Modifier.height(12.dp))
                    SleepNightDetail(night = detail)
                }
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

/** One bar in an interactive day chart. */
private data class DayBar(
    val dayLabel: String,
    val valueLabel: String,
    /** 0..1 fill of the track height. */
    val fraction: Float,
    val color: Color,
    val hasData: Boolean,
)

@Composable
private fun InteractiveDayBars(
    bars: List<DayBar>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        bars.forEachIndexed { i, bar ->
            val selected = selectedIndex == i
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = bar.hasData) { onSelect(if (selected) null else i) }
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                Color.Transparent
                            },
                        ).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    bar.valueLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color =
                        if (bar.hasData) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier.height(96.dp).fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.6f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(trackColor.copy(alpha = 0.6f)),
                    )
                    if (bar.hasData) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.6f)
                                .fillMaxHeight(bar.fraction.coerceIn(0.04f, 1f))
                                .clip(RoundedCornerShape(4.dp))
                                .background(bar.color.copy(alpha = if (selected || selectedIndex == null) 1f else 0.45f)),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    bar.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SleepInteractiveBars(
    nights: List<SleepNightScore>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    val stops = AuroraBrushes.heroStops(LocalAuroraDark.current)
    val bars =
        nights.map { night ->
            val hours = night.totalSleepMin?.let { it / 60f }
            DayBar(
                dayLabel = shortWeekday(night.epochDay),
                valueLabel = hours?.let { "%.1fh".format(it) } ?: "–",
                fraction = hours?.let { (it / 10f).coerceIn(0f, 1f) } ?: 0f,
                color =
                    night.score?.let { lerp(stops[3], stops[0], (it / 100f).coerceIn(0f, 1f)) }
                        ?: stops[0],
                hasData = hours != null,
            )
        }
    InteractiveDayBars(bars = bars, selectedIndex = selectedIndex, onSelect = onSelect)
}

@Composable
private fun SleepNightDetail(night: SleepNightScore) {
    val zone = remember { java.time.ZoneId.systemDefault() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                fullDate(night.epochDay),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            DetailRow(
                stringResource(R.string.insights_detail_slept),
                night.totalSleepMin?.let { formatHoursMin(it) } ?: "–",
            )
            night.score?.let { DetailRow(stringResource(R.string.insights_detail_score), "$it/100") }
            night.sleepEfficiencyPct?.let { DetailRow(stringResource(R.string.insights_detail_efficiency), "$it%") }
            val bed = night.bedTimeMs?.let { formatClock(it, zone) }
            val wake = night.wakeTimeMs?.let { formatClock(it, zone) }
            if (bed != null && wake != null) {
                DetailRow(stringResource(R.string.insights_detail_bedwake), "$bed – $wake")
            }
            night.timeInBedMin?.let { DetailRow(stringResource(R.string.insights_detail_timeinbed), formatHoursMin(it)) }
            val deep = night.deepPct?.let { "${it.roundToInt()}%" }
            val rem = night.remPct?.let { "${it.roundToInt()}%" }
            if (deep != null || rem != null) {
                DetailRow(
                    stringResource(R.string.insights_detail_stages),
                    listOfNotNull(
                        deep?.let { stringResource(R.string.insights_detail_deep, it) },
                        rem?.let { stringResource(R.string.insights_detail_rem, it) },
                    ).joinToString(" · "),
                )
            }
            night.wasoMin?.let { DetailRow(stringResource(R.string.insights_detail_waso), "$it min") }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun shortWeekday(epochDay: Long): String {
    val dow = java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek.value // 1=Mon
    return when (dow) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        else -> "Sun"
    }
}

private fun fullDate(epochDay: Long): String {
    val date = java.time.LocalDate.ofEpochDay(epochDay)
    val today = java.time.LocalDate.now()
    val label =
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> shortWeekday(epochDay)
        }
    return "$label, ${date.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${date.dayOfMonth}"
}

private fun formatHoursMin(totalMin: Int): String {
    val h = totalMin / 60
    val m = totalMin % 60
    return "${h}h ${"%02d".format(m)}m"
}

private fun formatClock(
    epochMs: Long,
    zone: java.time.ZoneId,
): String {
    val t = java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
    val hour12 =
        when {
            t.hour == 0 -> 12
            t.hour > 12 -> t.hour - 12
            else -> t.hour
        }
    val ampm = if (t.hour < 12) "AM" else "PM"
    return "$hour12:${"%02d".format(t.minute)} $ampm"
}

private const val SLEEP_CHART_NIGHTS = 7

// ---------------------------------------------------------------------------
// Steps section
// ---------------------------------------------------------------------------

@Composable
private fun StepsSectionCard(
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
            val days = insights.stepDays
            if (days.isEmpty()) {
                Text(
                    stringResource(R.string.insights_steps_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            // Header: today big, 7-day average beside it.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    insights.stepsToday?.let { formatStepsLong(it) } ?: "–",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        stringResource(R.string.insights_steps_today),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    insights.steps7DayAvg?.let { avg ->
                        Text(
                            stringResource(R.string.insights_steps_avg, formatStepsLong(avg.roundToInt())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                insights.distanceTodayM?.let { meters ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%.1f".format(meters / 1000),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.insights_steps_km),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            var selected by remember { mutableStateOf<Int?>(null) }
            val maxSteps = days.maxOf { it.steps }.coerceAtLeast(1L)
            val stops = AuroraBrushes.heroStops(LocalAuroraDark.current)
            val avg = insights.steps7DayAvg
            val bars =
                days.map { day ->
                    val vsAvg = avg?.let { if (it > 0) day.steps / it else null }
                    DayBar(
                        dayLabel = shortWeekday(day.epochDay),
                        valueLabel = formatStepsShort(day.steps),
                        fraction = (day.steps.toFloat() / maxSteps).coerceIn(0f, 1f),
                        color =
                            when {
                                vsAvg == null -> stops[0]
                                vsAvg >= 1f -> stops[0]
                                vsAvg >= 0.5f -> lerp(stops[0], stops[2], ((1.0 - vsAvg) * 2).coerceIn(0.0, 1.0).toFloat())
                                else -> stops[3]
                            },
                        hasData = true,
                    )
                }
            InteractiveDayBars(bars = bars, selectedIndex = selected, onSelect = { selected = it })
            val detail = selected?.let { days.getOrNull(it) }
            if (detail != null) {
                Spacer(Modifier.height(12.dp))
                StepsDayDetail(day = detail, avg = avg)
            }
        }
    }
}

@Composable
private fun StepsDayDetail(
    day: com.sreecharan.stressrelief.data.health.StepDay,
    avg: Double?,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                fullDate(day.epochDay),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            DetailRow(stringResource(R.string.insights_detail_steps), formatStepsLong(day.steps))
            day.distanceMeters?.let {
                DetailRow(stringResource(R.string.insights_detail_distance), "%.2f".format(it / 1000) + " km")
            }
            if (avg != null && avg > 0) {
                val pct = ((day.steps - avg) / avg * 100).roundToInt()
                val verdict =
                    when {
                        pct >= 10 -> stringResource(R.string.insights_steps_above_avg, pct)
                        pct <= -10 -> stringResource(R.string.insights_steps_below_avg, -pct)
                        else -> stringResource(R.string.insights_steps_near_avg)
                    }
                DetailRow(stringResource(R.string.insights_detail_vs_avg), verdict)
            }
        }
    }
}

private fun formatStepsLong(steps: Long): String = "%,d".format(steps)

private fun formatStepsShort(steps: Long): String =
    when {
        steps >= 1000 -> "%.1fk".format(steps / 1000f)
        else -> "$steps"
    }

// ---------------------------------------------------------------------------
// Recommendations section
// ---------------------------------------------------------------------------

@Composable
private fun RecommendationsList(
    recommendations: List<com.sreecharan.stressrelief.data.model.Recommendation>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        recommendations.forEach { rec ->
            RecommendationRow(recommendation = rec)
        }
    }
}

@Composable
private fun RecommendationRow(
    recommendation: com.sreecharan.stressrelief.data.model.Recommendation,
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
                    recommendation.kind.icon(),
                    contentDescription = null,
                    tint = if (darkTheme) Color.White else Color(0xFF002021),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    recommendation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    recommendation.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                ) {
                    Text(
                        "${recommendation.metricLabel}: ${recommendation.metricValue}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun com.sreecharan.stressrelief.data.model.RecommendationKind.icon() =
    when (this) {
        com.sreecharan.stressrelief.data.model.RecommendationKind.SLEEP -> Icons.Filled.Bedtime
        com.sreecharan.stressrelief.data.model.RecommendationKind.ACTIVITY ->
            Icons.AutoMirrored.Filled.DirectionsWalk
        com.sreecharan.stressrelief.data.model.RecommendationKind.RECOVERY -> Icons.Filled.Favorite
        com.sreecharan.stressrelief.data.model.RecommendationKind.BREATHING -> Icons.Filled.Air
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
            Spacer(Modifier.height(8.dp))
            Text(
                rhrVerdict(today = today, baseline = baseline, z = insights.restingHrZ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun rhrVerdict(
    today: Int,
    baseline: Float,
    z: Float?,
): String {
    val delta = today - baseline.roundToInt()
    val pct = if (baseline > 0) ((today - baseline) / baseline * 100).roundToInt() else 0
    return when {
        z != null && z >= 3f ->
            stringResource(R.string.insights_rhr_verdict_extreme, pct)
        z != null && z >= 1.5f ->
            stringResource(R.string.insights_rhr_verdict_elevated, pct)
        z != null && z <= -1.5f ->
            stringResource(R.string.insights_rhr_verdict_low, -pct)
        delta >= 3 ->
            stringResource(R.string.insights_rhr_verdict_slightly_high, delta)
        delta <= -3 ->
            stringResource(R.string.insights_rhr_verdict_slightly_low, -delta)
        else -> stringResource(R.string.insights_rhr_verdict_normal)
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
