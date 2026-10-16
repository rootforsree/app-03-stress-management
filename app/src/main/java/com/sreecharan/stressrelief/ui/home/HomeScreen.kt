package com.sreecharan.stressrelief.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.model.StressScore
import com.sreecharan.stressrelief.navigation.BreatheRoute
import com.sreecharan.stressrelief.navigation.InsightsRoute
import com.sreecharan.stressrelief.navigation.JournalEditorRoute
import com.sreecharan.stressrelief.navigation.MoodCheckInRoute
import com.sreecharan.stressrelief.navigation.SettingsRoute
import com.sreecharan.stressrelief.navigation.SleepRoute
import com.sreecharan.stressrelief.ui.components.AuraGradients
import com.sreecharan.stressrelief.ui.components.AuraSectionTitle
import com.sreecharan.stressrelief.ui.components.AuroraButton
import com.sreecharan.stressrelief.ui.components.StaggerIn
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import kotlinx.collections.immutable.ImmutableList

/**
 * Home dashboard route. Owns the ViewModel; [HomeContent] is stateless.
 * Builder 1 wires this into the Home tab's NavHost.
 */
@Composable
fun HomeScreen(
    onNavigate: (Any) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Health Connect permission flow: registered once, fired from the
    // "Connect Health" card. The result re-checks state and refreshes the score.
    val healthPermissionLauncher =
        rememberLauncherForActivityResult(viewModel.healthConnect.permissionContract()) {
            viewModel.onHealthPermissionResult()
        }
    HomeContent(
        state = state,
        onNavigate = onNavigate,
        onRetry = viewModel::retry,
        onRefreshScore = viewModel::refreshStressScore,
        onConnectHealth = {
            healthPermissionLauncher.launch(viewModel.healthConnect.requiredPermissions())
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onNavigate: (Any) -> Unit,
    onRetry: () -> Unit,
    onRefreshScore: () -> Unit,
    onConnectHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigate(SettingsRoute) }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.home_settings_desc),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Calm wash: faint aurora tint over the background. Body text below always
            // sits on solid card surfaces, never directly on the gradient.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AuraGradients.calmWash()),
            )
            when (state) {
                HomeUiState.Loading -> {
                    HomeLoading()
                }

                is HomeUiState.Error -> {
                    HomeError(message = state.message, onRetry = onRetry)
                }

                is HomeUiState.Content -> {
                    HomeDashboard(
                        state = state,
                        onNavigate = onNavigate,
                        onRefreshScore = onRefreshScore,
                        onConnectHealth = onConnectHealth,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDashboard(
    state: HomeUiState.Content,
    onNavigate: (Any) -> Unit,
    onRefreshScore: () -> Unit,
    onConnectHealth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            StaggerIn(0) {
                StressHeroCard(
                    score = state.stressScore,
                    onRefresh = onRefreshScore,
                )
            }
        }
        // Health Connect is installed but the stress-signal permissions were
        // never granted: the in-context opt-in that makes computeStressScore's
        // Health Connect half actually work.
        if (state.healthConnectState == HealthConnectUiState.Available) {
            item {
                StaggerIn(1) {
                    ConnectHealthCard(onConnect = onConnectHealth)
                }
            }
        }
        item {
            StaggerIn(2) {
                StreakCard(
                    streakDays = state.streakDays,
                    weekDots = state.weekDots,
                )
            }
        }
        item {
            StaggerIn(3) {
                AuraSectionTitle(stringResource(R.string.home_quick_actions))
                QuickActionsGrid(onNavigate = onNavigate)
            }
        }
        item {
            StaggerIn(4) {
                AuraSectionTitle(stringResource(R.string.home_today))
                TodayTimeline(state = state)
            }
        }
        item {
            StaggerIn(5) {
                InsightsPromoCard(onClick = { onNavigate(InsightsRoute) })
            }
        }
    }
}

/** Opt-in card: explains why Aura wants Health Connect, then requests it. */
@Composable
private fun ConnectHealthCard(
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
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AuraGradients.dawn()),
                    contentAlignment = Alignment.Center,
                ) {
                    val darkTheme = LocalAuroraDark.current
                    Icon(
                        Icons.Filled.MonitorHeart,
                        contentDescription = null,
                        tint = if (darkTheme) Color.White else Color(0xFF002021),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.home_health_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.home_health_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            AuroraButton(
                text = stringResource(R.string.home_health_cta),
                onClick = onConnect,
                gradient = true,
            )
        }
    }
}

/** Hero card: stress score ring filled with the dawn gradient, label, top factors. */
@Composable
private fun StressHeroCard(
    score: StressScore?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.home_stress_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Subtle refresh affordance; the score loads async from Health Connect.
                IconButton(onClick = onRefresh) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.home_refresh_score_desc),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (score != null) {
                    StressRing(score = score.score)
                    Spacer(Modifier.width(20.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            score.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        score.factors.take(3).forEach { factor ->
                            FactorRow(factor = factor)
                        }
                        if (score.factors.isEmpty()) {
                            Text(
                                stringResource(R.string.home_no_factors),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    StressRingPlaceholder()
                    Spacer(Modifier.width(20.dp))
                    Text(
                        stringResource(R.string.home_score_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FactorRow(
    factor: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 3.dp),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(AuraGradients.dawn()),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            factor,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 0-100 ring; the progress sweep uses the dawn gradient, never a flat color. */
@Composable
private fun StressRing(
    score: Int,
    modifier: Modifier = Modifier,
) {
    val sweep = AuraGradients.sweep()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val cd = stringResource(R.string.home_stress_score_cd, score)
    Box(
        modifier =
            modifier
                .size(148.dp)
                .semantics {
                    contentDescription = cd
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val sweepAngle = 270f * (score.coerceIn(0, 100) / 100f)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = sweep,
                startAngle = 135f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$score",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.home_score_out_of_100),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StressRingPlaceholder(modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier.size(148.dp)) {
        drawArc(
            color = track,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/** Streak card: growing bloom in tertiary pink (flame-free), N-day streak, weekly dots. */
@Composable
private fun StreakCard(
    streakDays: Int,
    weekDots: ImmutableList<WeekDot>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            ),
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BloomVisual(petals = HomeStats.bloomPetals(streakDays))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    HomeStats.streakLabel(streakDays),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Text(
                    stringResource(R.string.home_streak_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    weekDots.forEach { dot ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (dot.active) {
                                            Modifier.background(AuraGradients.dawn())
                                        } else {
                                            Modifier.background(
                                                MaterialTheme.colorScheme.onTertiaryContainer
                                                    .copy(alpha = 0.18f),
                                            )
                                        },
                                    ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                dot.label,
                                style = MaterialTheme.typography.labelSmall,
                                color =
                                    MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                        alpha = 0.7f,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Bloom: concentric petals that multiply with the streak. Drawn in tertiary pink,
 * the warm accent the design doc reserves for streak highlights.
 */
@Composable
private fun BloomVisual(
    petals: Int,
    modifier: Modifier = Modifier,
) {
    val tertiary = MaterialTheme.colorScheme.tertiary
    val onTertiary = MaterialTheme.colorScheme.onTertiary
    val cd = stringResource(R.string.home_bloom_cd, petals)
    Canvas(
        modifier
            .size(104.dp)
            .semantics { contentDescription = cd },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        repeat(petals) { i ->
            rotate(degrees = 360f / petals * i, pivot = center) {
                drawOval(
                    color = tertiary.copy(alpha = 0.5f),
                    topLeft = Offset(center.x - radius * 0.15f, center.y - radius * 0.94f),
                    size = Size(radius * 0.3f, radius * 0.52f),
                )
            }
        }
        drawCircle(color = tertiary.copy(alpha = 0.85f), radius = radius * 0.34f, center = center)
        drawCircle(color = onTertiary, radius = radius * 0.16f, center = center)
    }
}

private data class QuickAction(
    val labelRes: Int,
    val icon: ImageVector,
    val descRes: Int,
    val route: Any,
)

/** 2x2 quick-action grid: Check in, Breathe, Journal, Wind down. */
@Composable
private fun QuickActionsGrid(
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions =
        listOf(
            QuickAction(
                R.string.home_action_checkin,
                Icons.Filled.Favorite,
                R.string.home_action_checkin_desc,
                MoodCheckInRoute,
            ),
            QuickAction(
                R.string.home_action_breathe,
                Icons.Filled.Air,
                R.string.home_action_breathe_desc,
                BreatheRoute,
            ),
            QuickAction(
                R.string.home_action_journal,
                Icons.Filled.Book,
                R.string.home_action_journal_desc,
                JournalEditorRoute(),
            ),
            QuickAction(
                R.string.home_action_winddown,
                Icons.Filled.Bedtime,
                R.string.home_action_winddown_desc,
                SleepRoute,
            ),
        )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        actions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    QuickActionCard(
                        action = action,
                        onClick = { onNavigate(action.route) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    action: QuickAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AuraGradients.dawn()),
                contentAlignment = Alignment.Center,
            ) {
                val darkTheme = LocalAuroraDark.current
                Icon(
                    action.icon,
                    contentDescription = null,
                    // Dark ink on the light dawn gradient, white on night aurora.
                    tint = if (darkTheme) Color.White else Color(0xFF002021),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(action.labelRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** "Today" timeline: check-in status, calm minutes, journal status. */
@Composable
private fun TodayTimeline(
    state: HomeUiState.Content,
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TimelineRow(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.home_today_checkin),
                status =
                    if (state.checkedInToday) {
                        stringResource(R.string.home_today_done)
                    } else {
                        stringResource(R.string.home_today_pending)
                    },
                done = state.checkedInToday,
            )
            TimelineRow(
                icon = Icons.Filled.Air,
                label = stringResource(R.string.home_today_calm),
                status =
                    stringResource(
                        R.string.home_today_calm_value,
                        state.totalCalmMinutes,
                    ),
                done = state.totalCalmMinutes > 0,
            )
            TimelineRow(
                icon = Icons.Filled.Book,
                label = stringResource(R.string.home_today_journal),
                status =
                    if (state.journalDoneToday) {
                        stringResource(R.string.home_today_done)
                    } else {
                        stringResource(R.string.home_today_pending)
                    },
                done = state.journalDoneToday,
            )
        }
    }
}

@Composable
private fun TimelineRow(
    icon: ImageVector,
    label: String,
    status: String,
    done: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint =
                if (done) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Premium teaser that routes to the gated Insights screen. */
@Composable
private fun InsightsPromoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AuraGradients.dawn()),
                contentAlignment = Alignment.Center,
            ) {
                val darkTheme = LocalAuroraDark.current
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = if (darkTheme) Color.White else Color(0xFF002021),
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_insights_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    stringResource(R.string.home_insights_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            Icon(
                Icons.Filled.Lock,
                contentDescription = stringResource(R.string.home_insights_premium_desc),
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun HomeLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.home_loading),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeError(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(24.dp)
            .clickable(onClick = onRetry, role = Role.Button),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.b4_action_retry),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
