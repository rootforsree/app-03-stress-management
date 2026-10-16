package com.sreecharan.stressrelief.ui.paywall

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sreecharan.stressrelief.R
import com.sreecharan.stressrelief.data.billing.BillingRepository
import com.sreecharan.stressrelief.data.billing.EntitlementState
import com.sreecharan.stressrelief.ui.components.AuroraButton
import com.sreecharan.stressrelief.ui.theme.AuroraBrushes
import com.sreecharan.stressrelief.ui.theme.LocalAuroraDark
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class BillingGateViewModel
    @Inject
    constructor(
        billing: BillingRepository,
    ) : ViewModel() {
        val entitlement = billing.entitlement
    }

/**
 * Cold-start gate. Shows a neutral loading surface (no branding flash, no
 * free-tier flash) until RevenueCat reports the real entitlement, then calls
 * [content] with the premium flag. Paying users never see the free tier.
 */
@Composable
fun BillingGate(
    viewModel: BillingGateViewModel = hiltViewModel(),
    loadingContent: @Composable () -> Unit = { BillingLoadingSplash() },
    content: @Composable (isPremium: Boolean) -> Unit,
) {
    val state by viewModel.entitlement.collectAsStateWithLifecycle()
    when (val s = state) {
        EntitlementState.Loading -> loadingContent()
        is EntitlementState.Ready -> content(s.isPremium)
        EntitlementState.Unavailable -> content(false)
    }
}

/**
 * Feature gate for premium-only surfaces (InsightsRoute, premium sleep
 * packs, AI companion entry point). Free tier keeps breathing, check-ins,
 * streaks, basic stats, and the widget. When locked, shows an upsell card
 * with a button to the paywall.
 */
@Composable
fun ProGate(
    onShowPaywall: () -> Unit,
    viewModel: BillingGateViewModel = hiltViewModel(),
    lockedTitle: String = stringResource(R.string.paywall_title),
    lockedBody: String = stringResource(R.string.paywall_subtitle),
    content: @Composable () -> Unit,
) {
    val state by viewModel.entitlement.collectAsStateWithLifecycle()
    val isPremium =
        when (val s = state) {
            is EntitlementState.Ready -> s.isPremium
            // While loading, hold the previous frame: never flash locked UI
            // at a paying user, and never flash paid UI at a free user.
            else -> null
        }
    when (isPremium) {
        true -> content()
        false ->
            ProLockedCard(
                title = lockedTitle,
                body = lockedBody,
                onShowPaywall = onShowPaywall,
            )
        null -> BillingLoadingSplash()
    }
}

@Composable
private fun BillingLoadingSplash() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ProLockedCard(
    title: String,
    body: String,
    onShowPaywall: () -> Unit,
) {
    val darkTheme = LocalAuroraDark.current
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Gradient medallion, same as EmptyState, so the lock reads
            // premium instead of a plain icon.
            Box(
                modifier =
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(AuroraBrushes.hero(darkTheme)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (darkTheme) Color.White else Color(0xFF002021),
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            AuroraButton(
                text = stringResource(R.string.paywall_title),
                onClick = onShowPaywall,
                gradient = true,
            )
        }
    }
}
