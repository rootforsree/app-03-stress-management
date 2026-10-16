package com.sreecharan.stressrelief.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.navigation.BreatheRoute
import com.sreecharan.stressrelief.navigation.InsightsRoute
import com.sreecharan.stressrelief.navigation.JournalEditorRoute
import com.sreecharan.stressrelief.navigation.MoodCheckInRoute
import com.sreecharan.stressrelief.navigation.ProgramsRoute
import com.sreecharan.stressrelief.navigation.SettingsRoute
import com.sreecharan.stressrelief.navigation.SleepRoute
import com.sreecharan.stressrelief.navigation.SleepSessionRoute
import com.sreecharan.stressrelief.ui.components.AuraGradients
import com.sreecharan.stressrelief.ui.components.AuraSectionTitle
import com.sreecharan.stressrelief.ui.components.StaggerIn
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Home dashboard route (v2). Owns the ViewModel; [HomeContent] is stateless.
 */
@Composable
fun HomeScreen(
    onNavigate: (Any) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Health Connect permission flow: registered once, fired from the slim
    // banner. The result re-checks state and refreshes the score.
    val healthPermissionLauncher =
        rememberLauncherForActivityResult(viewModel.healthConnect.permissionContract()) {
            viewModel.onHealthPermissionResult()
        }
    val context = LocalContext.current
    // Notification permission: ask once, up front, so reminders can fire.
    // (Onboarding only asked when the reminders toggle was flipped, which
    // most users skip.) A denial is final; we never nag.
    var notificationsAsked by remember { mutableStateOf(false) }
    val notificationLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAsked = true
        }
    LaunchedEffect(Unit) {
        if (!notificationsAsked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    HomeContent(
        state = state,
        onNavigate = onNavigate,
        onRetry = viewModel::retry,
        onRefreshScore = viewModel::refreshStressScore,
        onConnectHealth = {
            healthPermissionLauncher.launch(viewModel.healthConnect.requiredPermissions())
        },
        onInstallHealthConnect = {
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
    onInstallHealthConnect: () -> Unit,
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
                        onInstallHealthConnect = onInstallHealthConnect,
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
    onInstallHealthConnect: () -> Unit,
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
            GreetingHeader(
                state = state,
                onNudgeClick = { onNavigate(BreatheRoute) },
            )
        }
        item {
            StaggerIn(0) {
                StressHeroCard(
                    state = state,
                    onRefresh = onRefreshScore,
                    onClick = { onNavigate(InsightsRoute) },
                )
            }
        }
        state.continueAction?.let { action ->
            item {
                StaggerIn(1) {
                    ContinueCard(
                        action = action,
                        state = state,
                        onClick = { onNavigate(continueRoute(action)) },
                    )
                }
            }
        }
        // Health Connect is installed but the stress-signal permissions were
        // never granted: a slim in-context opt-in.
        if (state.healthConnectState == HealthConnectUiState.Available) {
            item {
                HealthConnectBanner(
                    text = stringResource(R.string.home_health_banner_available),
                    cta = stringResource(R.string.home_health_cta),
                    onClick = onConnectHealth,
                )
            }
        }
        // Health Connect itself is missing: guide the user to the Play Store.
        if (state.healthConnectState == HealthConnectUiState.NeedsInstall) {
            item {
                HealthConnectBanner(
                    text = stringResource(R.string.home_health_banner_install),
                    cta = stringResource(R.string.home_health_install_cta),
                    onClick = onInstallHealthConnect,
                )
            }
        }
        item {
            StaggerIn(2) {
                YourWeekCard(
                    state = state,
                    onClick = { onNavigate(InsightsRoute) },
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
                TodayPromptCard(
                    prompt = state.todayPrompt,
                    onClick = { onNavigate(JournalEditorRoute()) },
                )
            }
        }
        item {
            StaggerIn(5) {
                WindDownCard(
                    state = state,
                    onClick = { onNavigate(SleepSessionRoute) },
                )
            }
        }
        item {
            StaggerIn(6) {
                InsightsEntryCard(onClick = { onNavigate(InsightsRoute) })
            }
        }
    }
}

/** 1. Greeting + smart nudge: plain text, no card. The nudge line taps to Breathe. */
@Composable
private fun GreetingHeader(
    state: HomeUiState.Content,
    onNudgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val greetingText =
        when (state.greeting) {
            GreetingTime.MORNING -> stringResource(R.string.home_greeting_morning)
            GreetingTime.AFTERNOON -> stringResource(R.string.home_greeting_afternoon)
            GreetingTime.EVENING -> stringResource(R.string.home_greeting_evening)
        }
    val dateText =
        remember(state.today) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(state.today)
        }
    val nudgeText =
        when (state.nudge) {
            NudgeKind.NO_SCORE -> stringResource(R.string.home_nudge_no_score)
            NudgeKind.HIGH -> stringResource(R.string.home_nudge_high)
            NudgeKind.MODERATE -> stringResource(R.string.home_nudge_moderate)
            NudgeKind.LOW -> stringResource(R.string.home_nudge_low)
        }
    Column(modifier = modifier.padding(horizontal = 4.dp)) {
        Text(
            "$greetingText · $dateText",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            nudgeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.clickable(
                    onClick = onNudgeClick,
                    role = Role.Button,
                ),
        )
    }
}

/**
 * 2. Stress hero: the focal point. 168dp ring, displayLarge score, 7-day mood
 * sparkline, week delta, ONE fully readable top factor. Taps to Insights.
 */
@Composable
private fun StressHeroCard(
    state: HomeUiState.Content,
    onRefresh: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val score = state.stressScore
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick,
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
                        MoodSparkline(moods = state.sparkline)
                        Spacer(Modifier.height(4.dp))
                        WeekDeltaLine(delta = state.weekDelta)
                        Spacer(Modifier.height(8.dp))
                        TopFactor(factor = score.factors.firstOrNull())
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

/** The top factor, fully readable: no truncation of the lead factor. */
@Composable
private fun TopFactor(
    factor: String?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .padding(top = 6.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(AuraGradients.dawn()),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            factor ?: stringResource(R.string.home_no_factors),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                .size(168.dp)
                .semantics {
                    contentDescription = cd
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 18.dp.toPx()
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
                style = MaterialTheme.typography.displayLarge,
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
    Canvas(modifier.size(168.dp)) {
        drawArc(
            color = track,
            startAngle = 135f,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * 7-day mood sparkline: 7 small bars, height and color by average mood
 * (1..5 on the dawn scale), hollow when nothing was logged.
 */
@Composable
private fun MoodSparkline(
    moods: List<Float?>,
    modifier: Modifier = Modifier,
) {
    MoodBars(moods = moods, modifier = modifier, barWidth = 10.dp, maxHeight = 36.dp)
}

@Composable
private fun MoodBars(
    moods: List<Float?>,
    modifier: Modifier = Modifier,
    labels: List<String>? = null,
    barWidth: Dp = 12.dp,
    maxHeight: Dp = 44.dp,
) {
    val stops = AuroraBrushes.heroStops(LocalAuroraDark.current)
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        moods.forEachIndexed { index, mood ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val fraction = mood?.let { ((it - 1f) / 4f).coerceIn(0f, 1f) }
                val barHeight =
                    if (mood == null) {
                        6.dp
                    } else {
                        (maxHeight * (mood / 5f)).coerceAtLeast(6.dp)
                    }
                Box(
                    Modifier
                        .width(barWidth)
                        .height(barHeight)
                        .then(
                            if (fraction == null) {
                                Modifier.border(1.5.dp, outline, RoundedCornerShape(4.dp))
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(dawnColorFor(fraction, stops))
                            },
                        ),
                )
                labels?.getOrNull(index)?.let { label ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Interpolates across the dawn stops (teal -> cyan -> violet -> pink). */
private fun dawnColorFor(
    fraction: Float,
    stops: List<Color>,
): Color {
    if (stops.isEmpty()) return Color.Gray
    if (stops.size == 1) return stops.first()
    val scaled = fraction.coerceIn(0f, 1f) * (stops.size - 1)
    val index = scaled.toInt().coerceAtMost(stops.size - 2)
    return lerp(stops[index], stops[index + 1], scaled - index)
}

/** Week delta line under the sparkline ("Mood down 8 vs last week"). Hidden when unknown. */
@Composable
private fun WeekDeltaLine(
    delta: Int?,
    modifier: Modifier = Modifier,
) {
    if (delta == null) return
    val (icon, text) =
        when {
            delta > 0 ->
                Icons.Filled.ArrowUpward to
                    stringResource(R.string.home_hero_delta_up, delta)
            delta < 0 ->
                Icons.Filled.ArrowDownward to
                    stringResource(R.string.home_hero_delta_down, -delta)
            else -> Icons.Filled.Remove to stringResource(R.string.home_hero_delta_flat)
        }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun continueRoute(action: ContinueAction): Any =
    when (action) {
        ContinueAction.CheckIn -> MoodCheckInRoute
        is ContinueAction.Program -> ProgramsRoute
        ContinueAction.BreatheReset -> BreatheRoute
        ContinueAction.WindDown -> SleepSessionRoute
    }

/**
 * 3. Continue card: the single best next step. THE only gradient-filled card
 * on the screen: full dawn-gradient background, dark ink text, white pill CTA.
 */
@Composable
private fun ContinueCard(
    action: ContinueAction,
    state: HomeUiState.Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalAuroraDark.current
    val ink = if (darkTheme) Color.White else Color(0xFF002021)
    val dateText =
        remember(state.today) {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(state.today)
        }
    val (title, subtitle, ctaText) =
        when (action) {
            ContinueAction.CheckIn ->
                Triple(
                    stringResource(R.string.home_continue_checkin_title),
                    stringResource(R.string.home_continue_checkin_sub, dateText),
                    stringResource(R.string.home_continue_checkin_cta),
                )
            is ContinueAction.Program ->
                Triple(
                    action.title,
                    stringResource(R.string.home_continue_program_sub, action.day, action.minutes),
                    stringResource(R.string.home_continue_cta),
                )
            ContinueAction.BreatheReset ->
                Triple(
                    stringResource(R.string.home_continue_breathe_title),
                    stringResource(R.string.home_continue_breathe_sub),
                    stringResource(R.string.home_continue_cta),
                )
            ContinueAction.WindDown ->
                Triple(
                    stringResource(R.string.home_continue_winddown_title),
                    stringResource(R.string.home_continue_winddown_sub),
                    stringResource(R.string.home_continue_cta),
                )
        }
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(AuraGradients.dawn())
                .clickable(onClick = onClick, role = Role.Button)
                .padding(20.dp),
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ink.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(16.dp))
            // White pill CTA: the one high-contrast action on the screen.
            Button(
                onClick = onClick,
                shape = CircleShape,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF002021),
                    ),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
            ) {
                Text(
                    ctaText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** 4. Slim Health Connect banner (~72dp): icon + one line + text button. */
@Composable
private fun HealthConnectBanner(
    text: String,
    cta: String,
    onClick: () -> Unit,
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
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) {
                Text(cta)
            }
        }
    }
}

/**
 * 5. Your week: merged streak + week card. Bloom and streak label on the left,
 * 7 mood bars plus weekly calm minutes and journal count on the right, then a
 * condensed today checklist. Taps to Insights.
 */
@Composable
private fun YourWeekCard(
    state: HomeUiState.Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        onClick = onClick,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.home_week_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(end = 16.dp),
                ) {
                    BloomVisual(petals = HomeStats.bloomPetals(state.streakDays))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        HomeStats.streakLabel(state.streakDays),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(Modifier.weight(1f)) {
                    MoodBars(
                        moods = state.weekBars.map { it.mood },
                        labels = state.weekBars.map { it.label },
                        barWidth = 14.dp,
                        maxHeight = 52.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.home_week_calm, state.weeklyCalmMinutes) +
                            " · " +
                            stringResource(R.string.home_week_journal, state.weeklyJournalCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WeekChecklistRow(
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
                WeekChecklistRow(
                    icon = Icons.Filled.Air,
                    label = stringResource(R.string.home_today_calm),
                    status = stringResource(R.string.home_today_calm_value_today, state.calmMinutesToday),
                    done = state.calmMinutesToday > 0,
                )
                WeekChecklistRow(
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
}

@Composable
private fun WeekChecklistRow(
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
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            .size(88.dp)
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
    /** Per-tile dawn-spectrum accent chip color (solid, not gradient). */
    val accent: Color,
)

/** 6. Quick actions 2x2, restyled: each tile gets its own dawn-spectrum accent. */
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
                Color(0xFF2DD4BF),
            ),
            QuickAction(
                R.string.home_action_breathe,
                Icons.Filled.Air,
                R.string.home_action_breathe_desc,
                BreatheRoute,
                Color(0xFF22D3EE),
            ),
            QuickAction(
                R.string.home_action_journal,
                Icons.Filled.Book,
                R.string.home_action_journal_desc,
                JournalEditorRoute(),
                Color(0xFFA78BFA),
            ),
            QuickAction(
                R.string.home_action_winddown,
                Icons.Filled.Bedtime,
                R.string.home_action_winddown_desc,
                SleepRoute,
                Color(0xFFF9A8D4),
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
                    .background(action.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    action.icon,
                    contentDescription = null,
                    // Dark ink on the light dawn accents, in both themes.
                    tint = Color(0xFF002021),
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

/** 7. Today's prompt: rotating catalog prompt, taps into a new journal entry. */
@Composable
private fun TodayPromptCard(
    prompt: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        onClick = onClick,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.journal_prompt_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                prompt,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) {
                Text(stringResource(R.string.home_prompt_cta))
            }
        }
    }
}

/**
 * 8. Wind down tonight: last-session summary + Start pill. A slim single row
 * before 18:00, expanded in the evening when it is the timely action.
 */
@Composable
private fun WindDownCard(
    state: HomeUiState.Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = state.lastSleepSession
    val summaryText =
        when {
            summary == null -> stringResource(R.string.home_winddown_none)
            summary.soundNames.isBlank() ->
                stringResource(R.string.home_winddown_last_nosound, summary.durationMin)
            else -> stringResource(R.string.home_winddown_last, summary.durationMin, summary.soundNames)
        }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        onClick = onClick,
    ) {
        if (state.isEvening) {
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
                            Icons.Filled.Bedtime,
                            contentDescription = null,
                            tint = if (darkTheme) Color.White else Color(0xFF002021),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.home_winddown_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                CompactPillButton(
                    text = stringResource(R.string.home_continue_cta),
                    onClick = onClick,
                )
            }
        } else {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.home_winddown_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                CompactPillButton(
                    text = stringResource(R.string.home_continue_cta),
                    onClick = onClick,
                )
            }
        }
    }
}

/** Small pill CTA for non-gradient cards (compact padding, M3 primary). */
@Composable
private fun CompactPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** 9. Insights entry card: free, no lock, no upsell copy. Taps to Insights. */
@Composable
private fun InsightsEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.home_insights_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
