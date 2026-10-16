package com.sreecharan.stressrelief.ui.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.ui.components.AuroraButton
import com.sreecharan.stressrelief.ui.components.AuroraCard
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark

private data class OnboardingPage(
    val icon: ImageVector,
    val titleRes: Int,
    val bodyRes: Int,
)

private val PAGES =
    listOf(
        OnboardingPage(
            Icons.Filled.Air,
            R.string.onboarding_page1_title,
            R.string.onboarding_page1_body,
        ),
        OnboardingPage(
            Icons.Filled.SentimentSatisfied,
            R.string.onboarding_page2_title,
            R.string.onboarding_page2_body,
        ),
        OnboardingPage(
            Icons.Filled.Bedtime,
            R.string.onboarding_page3_title,
            R.string.onboarding_page3_body,
        ),
    )

/** Stateful route: owns the ViewModel, collects state and one-shot events. */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                OnboardingEvent.NavigateHome -> onFinished()
            }
        }
    }

    OnboardingContent(
        state = state,
        onNext = viewModel::onNext,
        onSkip = viewModel::onSkip,
        onPageChanged = viewModel::onPageChanged,
        onRemindersOptInChanged = viewModel::onRemindersOptInChanged,
    )
}

/** Stateless content: state in, events out. */
@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onRemindersOptInChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paging = state as? OnboardingUiState.Paging ?: return
    val pagerState = rememberPagerState(pageCount = { paging.totalPages })
    val darkTheme = LocalAuroraDark.current
    val context = LocalContext.current

    // POST_NOTIFICATIONS is requested in context here (with the explanation
    // card below), never as a launch barrage: only when the user opts in.
    fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // A denial flips the opt-in back off so the toggle reflects reality.
            onRemindersOptInChanged(granted)
        }

    LaunchedEffect(paging.page) {
        if (pagerState.currentPage != paging.page) {
            pagerState.animateScrollToPage(paging.page)
        }
    }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != paging.page) {
            onPageChanged(pagerState.currentPage)
        }
    }

    val isLastPage = paging.page == paging.totalPages - 1

    Scaffold(modifier = modifier) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(AuroraBrushes.calmWash(darkTheme)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (!isLastPage) {
                        TextButton(onClick = onSkip) {
                            Text(
                                stringResource(R.string.onboarding_skip),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) { page ->
                    OnboardingPageContent(page = PAGES[page], darkTheme = darkTheme)
                }

                PageIndicator(
                    page = paging.page,
                    totalPages = paging.totalPages,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(24.dp))

                if (isLastPage) {
                    ReminderOptInCard(
                        optIn = paging.remindersOptIn,
                        onOptInChanged = { optIn ->
                            if (optIn && !notificationsGranted()) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                onRemindersOptInChanged(optIn)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }

                AuroraButton(
                    text =
                        stringResource(
                            if (isLastPage) R.string.onboarding_get_started else R.string.onboarding_next,
                        ),
                    onClick = onNext,
                    gradient = true,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Last-page opt-in: explains reminders first, requests the permission only on opt-in. */
@Composable
private fun ReminderOptInCard(
    optIn: Boolean,
    onOptInChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuroraCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.onboarding_reminders_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.onboarding_reminders_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = optIn, onCheckedChange = onOptInChanged)
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    darkTheme: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier =
                Modifier
                    .size(168.dp)
                    .clip(CircleShape)
                    .background(AuroraBrushes.hero(darkTheme)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = if (darkTheme) Color.White else Color(0xFF002021),
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun PageIndicator(
    page: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    val darkTheme = LocalAuroraDark.current
    val cd = stringResource(R.string.onboarding_page_indicator_cd, page + 1, totalPages)
    Row(
        modifier = modifier.semantics { contentDescription = cd },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(totalPages) { index ->
            val selected = index == page
            Box(
                modifier =
                    Modifier
                        .width(if (selected) 28.dp else 8.dp)
                        .height(8.dp)
                        .clip(CircleShape)
                        .then(
                            if (selected) {
                                Modifier.background(AuroraBrushes.hero(darkTheme))
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            },
                        ),
            )
        }
    }
}
