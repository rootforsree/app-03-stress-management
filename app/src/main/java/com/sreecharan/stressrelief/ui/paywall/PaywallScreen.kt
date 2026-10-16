package com.sreecharan.stressrelief.ui.paywall

import android.app.Activity
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.billing.BillingPlan
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark

/**
 * Paywall route content (PaywallRoute). When launched from a premium feature
 * there is no close affordance: convert or dismiss back. From Settings the
 * close button is shown.
 */
@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PaywallEvent.Dismiss -> onDismiss()
            }
        }
    }

    PaywallScreenContent(
        state = state,
        onPlanSelected = viewModel::onPlanSelected,
        onPurchase = viewModel::onPurchaseClicked,
        onRestore = viewModel::onRestoreClicked,
        onRetryPlans = viewModel::onRetryPlans,
        onErrorDismissed = viewModel::onErrorDismissed,
        onClose = onDismiss,
    )
}

@Composable
private fun PaywallScreenContent(
    state: PaywallUiState,
    onPlanSelected: (String) -> Unit,
    onPurchase: (Activity) -> Unit,
    onRestore: () -> Unit,
    onRetryPlans: () -> Unit,
    onErrorDismissed: () -> Unit,
    onClose: () -> Unit,
) {
    when (state) {
        PaywallUiState.Loading -> {
            // Neutral surface while the first plans emission arrives.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        is PaywallUiState.Content -> {
            PaywallContentBody(
                content = state,
                onPlanSelected = onPlanSelected,
                onPurchase = onPurchase,
                onRestore = onRestore,
                onRetryPlans = onRetryPlans,
                onErrorDismissed = onErrorDismissed,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun PaywallContentBody(
    content: PaywallUiState.Content,
    onPlanSelected: (String) -> Unit,
    onPurchase: (Activity) -> Unit,
    onRestore: () -> Unit,
    onRetryPlans: () -> Unit,
    onErrorDismissed: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(content.errorMessage) {
        content.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            onErrorDismissed()
        }
    }
    val restoreText = content.restoreMessageRes?.let { stringResource(it) }
    LaunchedEffect(restoreText) {
        restoreText?.let {
            snackbarHostState.showSnackbar(it)
            onErrorDismissed()
        }
    }
    val darkTheme = LocalAuroraDark.current
    val bandStops = AuroraBrushes.heroStops(darkTheme)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Aurora wash header band: dawn stops in light, night aurora in dark.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    bandStops[0].copy(alpha = 0.35f),
                                    bandStops[2].copy(alpha = 0.18f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
            )
            if (content.allowClose) {
                IconButton(
                    onClick = onClose,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.paywall_close),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
            when {
                content.billingUnavailable -> {
                    PaywallUnavailable(onBack = onClose)
                }

                content.plans == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.paywall_loading_plans),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                else -> {
                    PaywallPlans(
                        state = content,
                        activity = activity,
                        onPlanSelected = onPlanSelected,
                        onPurchase = onPurchase,
                        onRestore = onRestore,
                        onRetryPlans = onRetryPlans,
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun PaywallUnavailable(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Spa,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.paywall_unavailable_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.paywall_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text(text = stringResource(R.string.paywall_unavailable_cta))
        }
    }
}

@Composable
private fun PaywallPlans(
    state: PaywallUiState.Content,
    activity: Activity?,
    onPlanSelected: (String) -> Unit,
    onPurchase: (Activity) -> Unit,
    onRestore: () -> Unit,
    onRetryPlans: () -> Unit,
) {
    val plans = state.plans.orEmpty()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            text = stringResource(R.string.paywall_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.paywall_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        listOf(
            R.string.paywall_feature_insights,
            R.string.paywall_feature_sleep,
            R.string.paywall_feature_ai,
            R.string.paywall_feature_cbt,
        ).forEach { featureRes ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = stringResource(featureRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        if (plans.isEmpty()) {
            Text(
                text = stringResource(R.string.paywall_loading_plans),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetryPlans) {
                Text(text = stringResource(R.string.paywall_retry))
            }
        } else {
            plans.forEach { plan ->
                PlanCard(
                    plan = plan,
                    selected = plan.packageId == state.selectedPlanId,
                    onSelected = { onPlanSelected(plan.packageId) },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        val selectedPlan = plans.firstOrNull { it.packageId == state.selectedPlanId }
        Button(
            onClick = { activity?.let(onPurchase) },
            enabled = selectedPlan != null && !state.isPurchasing,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            if (state.isPurchasing) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.paywall_cta),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (selectedPlan != null && !selectedPlan.isLifetime) {
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        R.string.paywall_trial_note,
                        selectedPlan.pricePerPeriod,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (selectedPlan?.isLifetime == true) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.paywall_lifetime_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onRestore,
            enabled = !state.isRestoring,
        ) {
            Text(text = stringResource(R.string.paywall_restore))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.paywall_terms),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlanCard(
    plan: BillingPlan,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onSelected,
                    role = Role.RadioButton,
                ),
        shape = RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        border =
            BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (plan.isBestValue || plan.isLifetime) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text =
                                stringResource(
                                    if (plan.isLifetime) {
                                        R.string.paywall_lifetime_badge
                                    } else {
                                        R.string.paywall_best_value
                                    },
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = plan.pricePerPeriod,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = plan.price,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
